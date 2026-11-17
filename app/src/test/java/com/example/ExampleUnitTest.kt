package com.example

import com.example.data.Mp4Combiner
import com.example.data.VideoDurationInspector
import com.example.data.VideoDurationSpec
import com.example.data.VideoErrorCategory
import com.example.data.VideoGenerationException
import com.example.data.VideoGenerationMode
import com.example.data.VideoGenerationRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleUnitTest {

    @Test
    fun `duration spec enables 3s-15s and marks 1s and 2s unsupported`() {
        assertEquals((1..15).toList(), VideoDurationSpec.ALL_UI_SECONDS)
        assertEquals(10, VideoDurationSpec.DEFAULT_GENERATE_SECONDS)
        assertFalse(VideoDurationSpec.isGenerateDurationSupported(1))
        assertFalse(VideoDurationSpec.isGenerateDurationSupported(2))
        for (sec in 3..15) {
            assertTrue("Expected ${sec}s supported", VideoDurationSpec.isGenerateDurationSupported(sec))
        }
        assertFalse(VideoDurationSpec.isGenerateDurationSupported(16))
    }

    @Test
    fun `repository rejects unsupported 1s and 2s durations without silent clamping`() = runBlocking {
        val tempDir = Files.createTempDirectory("vid_test_unsupported").toFile()
        try {
            var networkCalled = false
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    networkCalled = true
                    chain.proceed(chain.request())
                }
                .build()
            val repo = VideoGenerationRepository(
                httpClient = client,
                tokenProvider = { "id_tok" to null },
                durationInspector = VideoDurationInspector { 0L },
                pollIntervalMs = 1L
            )

            for (badDuration in listOf("1s", "2s", "0s", "16s")) {
                var caught: VideoGenerationException? = null
                try {
                    repo.generateVideo(
                        backendUrl = "https://example.cloudfunctions.net/anshVideoApi",
                        prompt = "Ocean waves at sunset",
                        mode = VideoGenerationMode.TEXT_TO_VIDEO,
                        style = "Cinematic",
                        aspectRatio = "16:9",
                        duration = badDuration,
                        sourceBitmap = null,
                        filesDir = tempDir,
                        onProgress = {}
                    )
                } catch (e: VideoGenerationException) {
                    caught = e
                }
                assertEquals(VideoErrorCategory.VALIDATION_ERROR, caught?.category)
                assertFalse("Should fail client-side before silent clamping", networkCalled)
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `repository sends exact requested durations 3s through 15s and verifies actual MP4 duration`() = runBlocking {
        val tempDir = Files.createTempDirectory("vid_test_all_durations").toFile()
        try {
            for (sec in 3..15) {
                var capturedDurationField = ""
                val interceptor = Interceptor { chain ->
                    val req = chain.request()
                    val url = req.url.toString()
                    when {
                        url.endsWith("/generate") -> {
                            val buf = Buffer()
                            req.body?.writeTo(buf)
                            val json = JSONObject(buf.readUtf8())
                            capturedDurationField = json.getString("duration")
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("""{"interactionId":"int_$sec","status":"in_progress","downloadReady":false}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                        url.contains("/status?id=int_$sec") -> {
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body("""{"interactionId":"int_$sec","status":"completed","downloadReady":true}""".toResponseBody("application/json".toMediaType()))
                                .build()
                        }
                        url.contains("/download?id=int_$sec") -> {
                            val bytes = "MP4_${sec}s_BYTES".toByteArray()
                            Response.Builder()
                                .request(req)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("Content-Type", "video/mp4")
                                .body(bytes.toResponseBody("video/mp4".toMediaType()))
                                .build()
                        }
                        else -> error("Unexpected URL: $url")
                    }
                }

                val expectedActualMs = sec * 1000L
                val repo = VideoGenerationRepository(
                    httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                    tokenProvider = { "id_tok" to "app_check_tok" },
                    durationInspector = VideoDurationInspector { file ->
                        if (file.exists() && file.readText() == "MP4_${sec}s_BYTES") expectedActualMs else 0L
                    },
                    pollIntervalMs = 1L
                )

                val result = repo.generateVideo(
                    backendUrl = "https://example.cloudfunctions.net/anshVideoApi",
                    prompt = "Futuristic skyline flyover",
                    mode = VideoGenerationMode.TEXT_TO_VIDEO,
                    style = "Cinematic",
                    aspectRatio = "16:9",
                    duration = "${sec}s",
                    sourceBitmap = null,
                    filesDir = tempDir,
                    onProgress = {}
                )

                assertEquals("${sec}s", capturedDurationField)
                assertEquals(sec, result.requestedDurationSeconds)
                assertEquals(expectedActualMs, result.actualDurationMs)
                assertTrue(File(result.localFilePath).exists())
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `extendVideo passes previousInteractionId, combines delta segment into unified MP4, and verifies final duration`() = runBlocking {
        val tempDir = Files.createTempDirectory("vid_test_extend").toFile()
        try {
            val parentFile = File(tempDir, "parent_10s.mp4").apply { writeText("PARENT_10S") }
            var capturedParentId = ""
            var capturedExtDuration = ""

            val interceptor = Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.endsWith("/extend") -> {
                        val buf = Buffer()
                        req.body?.writeTo(buf)
                        val json = JSONObject(buf.readUtf8())
                        capturedParentId = json.getString("previousInteractionId")
                        capturedExtDuration = json.getString("duration")
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body("""{"interactionId":"int_ext_2","parentInteractionId":"int_parent_1","status":"completed","downloadReady":true}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    url.contains("/download?id=int_ext_2") -> {
                        Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "video/mp4")
                            .body("DELTA_5S".toByteArray().toResponseBody("video/mp4".toMediaType()))
                            .build()
                    }
                    else -> error("Unexpected URL: $url")
                }
            }

            val repo = VideoGenerationRepository(
                httpClient = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                tokenProvider = { "id_tok" to null },
                durationInspector = VideoDurationInspector { file ->
                    when (file.readText()) {
                        "PARENT_10S" -> 10_000L
                        "DELTA_5S" -> 5_000L
                        "MERGED_15S" -> 15_000L
                        else -> 0L
                    }
                },
                mp4Combiner = Mp4Combiner { first, second, output ->
                    assertEquals("PARENT_10S", first.readText())
                    assertEquals("DELTA_5S", second.readText())
                    output.writeText("MERGED_15S")
                    true
                },
                pollIntervalMs = 1L
            )

            val result = repo.extendVideo(
                backendUrl = "https://example.cloudfunctions.net/anshVideoApi",
                parentInteractionId = "int_parent_1",
                parentFilePath = parentFile.absolutePath,
                parentActualDurationMs = 10_000L,
                parentSegmentCount = 1,
                continuationPrompt = "Continue soaring above electric clouds",
                style = "Cinematic",
                aspectRatio = "16:9",
                extensionDuration = "5s",
                filesDir = tempDir,
                onProgress = {}
            )

            assertEquals("int_parent_1", capturedParentId)
            assertEquals("5s", capturedExtDuration)
            assertTrue(result.wasMuxedLocally)
            assertEquals(15_000L, result.actualDurationMs)
            assertEquals(2, result.segmentCount)
            assertEquals("MERGED_15S", File(result.localFilePath).readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
