package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.R
import com.example.model.AppConfig
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "VideoGenRepo"

const val SUPPORTED_VIDEO_MODEL = "gemini-omni-1.2-flash"

enum class VideoGenerationMode(val wireValue: String, val label: String) {
    TEXT_TO_VIDEO("TEXT_TO_VIDEO", "Text to Video"),
    IMAGE_TO_VIDEO("IMAGE_TO_VIDEO", "Image to Video"),
    EXTEND("EXTEND", "Extend Video")
}

enum class VideoErrorCategory(val title: String) {
    MISSING_API_CONFIG("API & Backend Configuration Required"),
    DISABLED_BY_ADMIN("Disabled by Super Admin"),
    SERVICE_UNAVAILABLE("Video Service Unavailable"),
    QUOTA_EXCEEDED("Quota / Rate Limit Exceeded"),
    BILLING_REQUIRED("Billing Required"),
    AUTH_REQUIRED("Authentication Required"),
    VALIDATION_ERROR("Invalid Request"),
    GENERATION_FAILED("Generation Failed")
}

class VideoGenerationException(
    val category: VideoErrorCategory,
    override val message: String,
    val httpCode: Int? = null
) : Exception(message)

data class VideoProgressUpdate(
    val stage: String,
    val interactionId: String? = null,
    val elapsedSeconds: Int = 0
)

data class VideoGenerationResult(
    val interactionId: String,
    val parentInteractionId: String = "",
    val modelName: String,
    val localFilePath: String,
    val requestedDurationSeconds: Int,
    val actualDurationMs: Long,
    val segmentCount: Int = 1,
    val wasMuxedLocally: Boolean = false
)

fun interface VideoAuthTokenProvider {
    suspend fun fetchTokens(): Pair<String, String?>
}

object FirebaseVideoAuthTokenProvider : VideoAuthTokenProvider {
    override suspend fun fetchTokens(): Pair<String, String?> {
        val user = Firebase.auth.currentUser ?: throw VideoGenerationException(
            category = VideoErrorCategory.AUTH_REQUIRED,
            message = "Sign in with Google before generating videos."
        )
        val idToken = try {
            user.getIdToken(false).await()?.token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw VideoGenerationException(
                category = VideoErrorCategory.AUTH_REQUIRED,
                message = e.localizedMessage ?: "Failed to obtain Firebase Auth token."
            )
        } ?: throw VideoGenerationException(
            category = VideoErrorCategory.AUTH_REQUIRED,
            message = "Missing Firebase Auth ID token."
        )

        val appCheckToken = try {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "App Check token unavailable before video proxy call: ${e.message}")
            null
        }
        return idToken to appCheckToken
    }
}

class VideoGenerationRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val tokenProvider: VideoAuthTokenProvider = FirebaseVideoAuthTokenProvider,
    private val durationInspector: VideoDurationInspector = AndroidVideoDurationInspector(),
    private val mp4Combiner: Mp4Combiner = AndroidMp4Combiner,
    private val pollIntervalMs: Long = 4_000L
) {
    fun resolveBackendUrl(appConfig: AppConfig, context: Context): String {
        val remote = appConfig.videoBackendUrl.trim()
        if (remote.isNotBlank()) return remote.trimEnd('/')
        val bundled = runCatching { context.getString(R.string.video_backend_url).trim() }
            .getOrDefault("")
        return bundled.trimEnd('/')
    }

    suspend fun generateVideo(
        backendUrl: String,
        prompt: String,
        mode: VideoGenerationMode,
        style: String,
        aspectRatio: String,
        duration: String,
        sourceBitmap: Bitmap?,
        filesDir: File,
        onProgress: (VideoProgressUpdate) -> Unit
    ): VideoGenerationResult {
        val cleanUrl = validateBackendUrl(backendUrl)
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Please enter a video description before generating."
            )
        }
        if (mode == VideoGenerationMode.IMAGE_TO_VIDEO && sourceBitmap == null) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Please upload a source image for Image-to-Video generation."
            )
        }

        val durationSeconds = VideoDurationSpec.parseSecondsOrNull(duration)
        if (durationSeconds == null || !VideoDurationSpec.isGenerateDurationSupported(durationSeconds)) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Duration '$duration' is unsupported by $SUPPORTED_VIDEO_MODEL. Supported native durations are 3–15 seconds (1s and 2s are not supported)."
            )
        }

        val (idToken, appCheckToken) = tokenProvider.fetchTokens()
        onProgress(VideoProgressUpdate(stage = "Submitting ${durationSeconds}s video job to $SUPPORTED_VIDEO_MODEL..."))

        val encodedImageBase64 = if (mode == VideoGenerationMode.IMAGE_TO_VIDEO && sourceBitmap != null) {
            withContext(Dispatchers.IO) {
                encodeScaledJpegBase64(sourceBitmap)
            }
        } else {
            null
        }

        val payloadJson = JSONObject().apply {
            put("prompt", cleanPrompt)
            put("mode", mode.wireValue)
            put("style", style)
            put("aspectRatio", aspectRatio)
            put("duration", "${durationSeconds}s")
            if (encodedImageBase64 != null) {
                put("imageBase64", encodedImageBase64)
                put("imageMimeType", "image/jpeg")
            }
        }.toString()

        val startJson = withContext(Dispatchers.IO) {
            executePostJson(
                url = "$cleanUrl/generate",
                jsonBody = payloadJson,
                idToken = idToken,
                appCheckToken = appCheckToken
            )
        }

        val interactionId = startJson.optString("interactionId").trim()
        if (interactionId.isBlank()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.GENERATION_FAILED,
                message = "Video backend did not return a valid operation/interaction ID."
            )
        }

        pollUntilDownloadReady(
            cleanUrl = cleanUrl,
            interactionId = interactionId,
            initialJson = startJson,
            idToken = idToken,
            appCheckToken = appCheckToken,
            label = "Rendering ${durationSeconds}s MP4 with $SUPPORTED_VIDEO_MODEL",
            onProgress = onProgress
        )

        val outputFile = withContext(Dispatchers.IO) {
            val outDir = File(filesDir, "generated_videos").apply { mkdirs() }
            val target = File(outDir, "ansh_vid_${System.currentTimeMillis()}.mp4")
            downloadMp4ToFile(
                url = "$cleanUrl/download?id=$interactionId",
                idToken = idToken,
                appCheckToken = appCheckToken,
                targetFile = target
            )
            target
        }

        val actualDurationMs = withContext(Dispatchers.IO) {
            durationInspector.measureDurationMs(outputFile)
        }
        if (actualDurationMs <= 0L) {
            withContext(Dispatchers.IO) { runCatching { outputFile.delete() } }
            throw VideoGenerationException(
                category = VideoErrorCategory.GENERATION_FAILED,
                message = "Downloaded MP4 container failed duration verification (0 ms). No playable video was produced."
            )
        }

        return VideoGenerationResult(
            interactionId = interactionId,
            parentInteractionId = "",
            modelName = SUPPORTED_VIDEO_MODEL,
            localFilePath = outputFile.absolutePath,
            requestedDurationSeconds = durationSeconds,
            actualDurationMs = actualDurationMs,
            segmentCount = 1,
            wasMuxedLocally = false
        )
    }

    suspend fun extendVideo(
        backendUrl: String,
        parentInteractionId: String,
        parentFilePath: String,
        parentActualDurationMs: Long,
        parentSegmentCount: Int,
        continuationPrompt: String,
        style: String,
        aspectRatio: String,
        extensionDuration: String,
        filesDir: File,
        onProgress: (VideoProgressUpdate) -> Unit
    ): VideoGenerationResult {
        val cleanUrl = validateBackendUrl(backendUrl)
        val cleanParentId = parentInteractionId.trim()
        if (cleanParentId.isBlank()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "This video cannot be extended because it has no interaction ID."
            )
        }
        val cleanPrompt = continuationPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Describe what should happen next to extend the video."
            )
        }
        val extSeconds = VideoDurationSpec.parseSecondsOrNull(extensionDuration)
        if (extSeconds == null || !VideoDurationSpec.isExtendDurationSupported(extSeconds)) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Extension duration '$extensionDuration' is unsupported by $SUPPORTED_VIDEO_MODEL. Choose 3–10 seconds per extension step."
            )
        }

        val parentFile = File(parentFilePath)
        val measuredParentMs = if (parentActualDurationMs > 0L) {
            parentActualDurationMs
        } else {
            withContext(Dispatchers.IO) { durationInspector.measureDurationMs(parentFile) }
        }
        val currentTotalSec = ((measuredParentMs + 500L) / 1000L).toInt()
        if (currentTotalSec + extSeconds > VideoDurationSpec.MAX_CUMULATIVE_SECONDS) {
            throw VideoGenerationException(
                category = VideoErrorCategory.VALIDATION_ERROR,
                message = "Extending by ${extSeconds}s exceeds the ${VideoDurationSpec.MAX_CUMULATIVE_SECONDS}s maximum total clip length (current clip: ${currentTotalSec}s)."
            )
        }

        val (idToken, appCheckToken) = tokenProvider.fetchTokens()
        onProgress(VideoProgressUpdate(stage = "Submitting +${extSeconds}s video extension (preserving scene & style)..."))

        val payloadJson = JSONObject().apply {
            put("previousInteractionId", cleanParentId)
            put("prompt", cleanPrompt)
            put("style", style)
            put("aspectRatio", aspectRatio)
            put("duration", "${extSeconds}s")
            put("currentDurationSeconds", currentTotalSec)
        }.toString()

        val startJson = withContext(Dispatchers.IO) {
            executePostJson(
                url = "$cleanUrl/extend",
                jsonBody = payloadJson,
                idToken = idToken,
                appCheckToken = appCheckToken
            )
        }

        val newInteractionId = startJson.optString("interactionId").trim()
        if (newInteractionId.isBlank()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.GENERATION_FAILED,
                message = "Video backend did not return a valid interaction ID for video extension."
            )
        }

        pollUntilDownloadReady(
            cleanUrl = cleanUrl,
            interactionId = newInteractionId,
            initialJson = startJson,
            idToken = idToken,
            appCheckToken = appCheckToken,
            label = "Extending clip by +${extSeconds}s",
            onProgress = onProgress
        )

        val outDir = File(filesDir, "generated_videos").apply { mkdirs() }
        val downloadedExtensionFile = File(outDir, "ansh_ext_raw_${System.currentTimeMillis()}.mp4")
        withContext(Dispatchers.IO) {
            downloadMp4ToFile(
                url = "$cleanUrl/download?id=$newInteractionId",
                idToken = idToken,
                appCheckToken = appCheckToken,
                targetFile = downloadedExtensionFile
            )
        }

        val rawSegmentDurationMs = withContext(Dispatchers.IO) {
            durationInspector.measureDurationMs(downloadedExtensionFile)
        }
        if (rawSegmentDurationMs <= 0L) {
            withContext(Dispatchers.IO) { runCatching { downloadedExtensionFile.delete() } }
            throw VideoGenerationException(
                category = VideoErrorCategory.GENERATION_FAILED,
                message = "Extended MP4 failed duration verification (0 ms)."
            )
        }

        // If the backend returned only the new extension segment (duration close to extSeconds instead
        // of parent + extSeconds) and the parent MP4 exists, losslessly mux them into one MP4.
        val combinedThresholdMs = measuredParentMs + (extSeconds * 500L)
        var finalFile = downloadedExtensionFile
        var finalDurationMs = rawSegmentDurationMs
        var wasMuxedLocally = false

        if (measuredParentMs > 0L &&
            parentFile.exists() &&
            parentFile.length() > 0L &&
            rawSegmentDurationMs < combinedThresholdMs
        ) {
            onProgress(
                VideoProgressUpdate(
                    stage = "Combining original and +${extSeconds}s segments into one MP4...",
                    interactionId = newInteractionId
                )
            )
            val mergedTarget = File(outDir, "ansh_vid_merged_${System.currentTimeMillis()}.mp4")
            val combinedOk = withContext(Dispatchers.IO) {
                mp4Combiner.combine(parentFile, downloadedExtensionFile, mergedTarget)
            }
            if (combinedOk) {
                val measuredMergedMs = withContext(Dispatchers.IO) {
                    durationInspector.measureDurationMs(mergedTarget)
                }
                if (measuredMergedMs > measuredParentMs) {
                    withContext(Dispatchers.IO) { runCatching { downloadedExtensionFile.delete() } }
                    finalFile = mergedTarget
                    finalDurationMs = measuredMergedMs
                    wasMuxedLocally = true
                } else {
                    withContext(Dispatchers.IO) { runCatching { mergedTarget.delete() } }
                }
            }
        }

        return VideoGenerationResult(
            interactionId = newInteractionId,
            parentInteractionId = cleanParentId,
            modelName = SUPPORTED_VIDEO_MODEL,
            localFilePath = finalFile.absolutePath,
            requestedDurationSeconds = currentTotalSec + extSeconds,
            actualDurationMs = finalDurationMs,
            segmentCount = parentSegmentCount + 1,
            wasMuxedLocally = wasMuxedLocally
        )
    }

    private fun validateBackendUrl(backendUrl: String): String {
        val cleanUrl = backendUrl.trim().trimEnd('/')
        if (cleanUrl.isBlank()) {
            throw VideoGenerationException(
                category = VideoErrorCategory.MISSING_API_CONFIG,
                message = "No server-side video backend URL is configured. Deploy the Firebase Cloud Functions video proxy with GEMINI_API_KEY stored in Cloud Secret Manager, then paste its HTTPS URL in Super Admin Console -> Config."
            )
        }
        val isLocalDev = cleanUrl.startsWith("http://10.0.2.2") || cleanUrl.startsWith("http://127.0.0.1")
        if (!cleanUrl.startsWith("https://") && !isLocalDev) {
            throw VideoGenerationException(
                category = VideoErrorCategory.MISSING_API_CONFIG,
                message = "Video backend URL must use HTTPS (e.g. https://us-central1-<project>.cloudfunctions.net/anshVideoApi)."
            )
        }
        return cleanUrl
    }

    private suspend fun pollUntilDownloadReady(
        cleanUrl: String,
        interactionId: String,
        initialJson: JSONObject,
        idToken: String,
        appCheckToken: String?,
        label: String,
        onProgress: (VideoProgressUpdate) -> Unit
    ) {
        var status = initialJson.optString("status", "in_progress")
        var downloadReady = initialJson.optBoolean("downloadReady", false)
        val startedMs = System.currentTimeMillis()

        withTimeout(240_000L) {
            while (!downloadReady) {
                if (status == "failed" || status == "cancelled" || status == "incomplete") {
                    val errMsg = initialJson.optString("errorMessage")
                        .ifBlank { "Video generation ended with status: $status." }
                    throw VideoGenerationException(VideoErrorCategory.GENERATION_FAILED, errMsg)
                }

                val elapsed = ((System.currentTimeMillis() - startedMs) / 1000L).toInt()
                onProgress(
                    VideoProgressUpdate(
                        stage = "$label (${elapsed}s)...",
                        interactionId = interactionId,
                        elapsedSeconds = elapsed
                    )
                )

                delay(pollIntervalMs)

                val pollJson = withContext(Dispatchers.IO) {
                    executeGetJson(
                        url = "$cleanUrl/status?id=$interactionId",
                        idToken = idToken,
                        appCheckToken = appCheckToken
                    )
                }
                status = pollJson.optString("status", "in_progress")
                downloadReady = pollJson.optBoolean("downloadReady", false)

                if (status == "failed" || status == "cancelled" || status == "incomplete") {
                    val reason = pollJson.optString("errorMessage")
                        .ifBlank { "Video generation ended with status: $status." }
                    throw VideoGenerationException(VideoErrorCategory.GENERATION_FAILED, reason)
                }
            }
        }

        val elapsedAtReady = ((System.currentTimeMillis() - startedMs) / 1000L).toInt()
        onProgress(
            VideoProgressUpdate(
                stage = "Downloading and verifying MP4 stream...",
                interactionId = interactionId,
                elapsedSeconds = elapsedAtReady
            )
        )
    }

    private fun executePostJson(
        url: String,
        jsonBody: String,
        idToken: String,
        appCheckToken: String?
    ): JSONObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Authorization", "Bearer $idToken")
            .header("Accept", "application/json")
        if (!appCheckToken.isNullOrBlank()) {
            requestBuilder.header("X-Firebase-AppCheck", appCheckToken)
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, body)
                }
                return parseJsonOrThrow(body)
            }
        } catch (e: VideoGenerationException) {
            throw e
        } catch (e: IOException) {
            throw VideoGenerationException(
                category = VideoErrorCategory.SERVICE_UNAVAILABLE,
                message = "Unable to reach the video generation backend: ${e.localizedMessage ?: "Network error"}."
            )
        }
    }

    private fun executeGetJson(
        url: String,
        idToken: String,
        appCheckToken: String?
    ): JSONObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $idToken")
            .header("Accept", "application/json")
        if (!appCheckToken.isNullOrBlank()) {
            requestBuilder.header("X-Firebase-AppCheck", appCheckToken)
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, body)
                }
                return parseJsonOrThrow(body)
            }
        } catch (e: VideoGenerationException) {
            throw e
        } catch (e: IOException) {
            throw VideoGenerationException(
                category = VideoErrorCategory.SERVICE_UNAVAILABLE,
                message = "Lost connection while checking video status: ${e.localizedMessage ?: "Network error"}."
            )
        }
    }

    private fun downloadMp4ToFile(
        url: String,
        idToken: String,
        appCheckToken: String?,
        targetFile: File
    ) {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $idToken")
        if (!appCheckToken.isNullOrBlank()) {
            requestBuilder.header("X-Firebase-AppCheck", appCheckToken)
        }

        try {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw mapHttpError(response.code, errBody)
                }
                val contentType = response.header("Content-Type").orEmpty()
                val body = response.body ?: throw VideoGenerationException(
                    VideoErrorCategory.GENERATION_FAILED,
                    "Empty response body while downloading MP4."
                )
                if (contentType.contains("application/json", ignoreCase = true)) {
                    throw mapHttpError(response.code, body.string())
                }
                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    targetFile.delete()
                    throw VideoGenerationException(
                        VideoErrorCategory.GENERATION_FAILED,
                        "Downloaded MP4 file was empty."
                    )
                }
            }
        } catch (e: VideoGenerationException) {
            targetFile.delete()
            throw e
        } catch (e: IOException) {
            targetFile.delete()
            throw VideoGenerationException(
                VideoErrorCategory.SERVICE_UNAVAILABLE,
                "Failed to download completed MP4 video: ${e.localizedMessage ?: "Network error"}."
            )
        }
    }

    private fun parseJsonOrThrow(raw: String): JSONObject {
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw VideoGenerationException(
                VideoErrorCategory.SERVICE_UNAVAILABLE,
                "Unexpected non-JSON response from video backend."
            )
        }
    }

    private fun mapHttpError(httpCode: Int, rawBody: String): VideoGenerationException {
        var serverCode = ""
        var serverMsg = ""
        try {
            val root = JSONObject(rawBody)
            val errObj = root.optJSONObject("error")
            serverCode = errObj?.optString("code").orEmpty()
            serverMsg = errObj?.optString("message").orEmpty()
        } catch (_: Exception) {
            // Fall back to raw text inspection
        }

        val detail = serverMsg.ifBlank { rawBody.take(300).ifBlank { "HTTP $httpCode" } }
        val lower = detail.lowercase()

        val category = when {
            serverCode == "MISSING_API_CONFIG" -> VideoErrorCategory.MISSING_API_CONFIG
            serverCode == "DISABLED_BY_ADMIN" -> VideoErrorCategory.DISABLED_BY_ADMIN
            serverCode == "BILLING_REQUIRED" || httpCode == 402 ||
                lower.contains("billing") || lower.contains("blaze") || lower.contains("paid") ->
                VideoErrorCategory.BILLING_REQUIRED
            serverCode == "QUOTA_EXCEEDED" || httpCode == 429 ||
                lower.contains("quota") || lower.contains("rate") ->
                VideoErrorCategory.QUOTA_EXCEEDED
            serverCode == "AUTH_REQUIRED" || serverCode == "APP_CHECK_FAILED" || httpCode == 401 ->
                VideoErrorCategory.AUTH_REQUIRED
            serverCode == "VALIDATION_ERROR" || httpCode == 400 ->
                VideoErrorCategory.VALIDATION_ERROR
            serverCode == "SERVICE_UNAVAILABLE" || httpCode == 404 || httpCode == 503 || httpCode == 502 ->
                VideoErrorCategory.SERVICE_UNAVAILABLE
            httpCode == 403 -> VideoErrorCategory.BILLING_REQUIRED
            else -> VideoErrorCategory.GENERATION_FAILED
        }
        return VideoGenerationException(category = category, message = detail, httpCode = httpCode)
    }

    private fun encodeScaledJpegBase64(bitmap: Bitmap, maxDim: Int = 1024): String {
        val width = bitmap.width
        val height = bitmap.height
        val scaled = if (width <= maxDim && height <= maxDim) {
            bitmap
        } else {
            val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
            Bitmap.createScaledBitmap(
                bitmap,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true
            )
        }
        return ByteArrayOutputStream().use { baos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }
    }
}
