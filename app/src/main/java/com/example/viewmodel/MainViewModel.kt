package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.FirestoreRepository
import com.example.data.GeminiAiRepository
import com.example.data.SUPPORTED_VIDEO_MODEL
import com.example.data.VideoErrorCategory
import com.example.data.VideoGenerationException
import com.example.data.VideoGenerationMode
import com.example.data.VideoGenerationRepository
import com.example.model.AppConfig
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.Citation
import com.example.model.GeminiModelOption
import com.example.model.ImageCreationLog
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.VideoCreationLog
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "MainViewModel"

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val filePath: String, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

sealed interface VideoGenUiState {
    data object Idle : VideoGenUiState
    data class Generating(
        val stage: String,
        val interactionId: String? = null,
        val elapsedSeconds: Int = 0
    ) : VideoGenUiState
    data class Success(val videoLog: VideoCreationLog) : VideoGenUiState
    data class Error(val category: VideoErrorCategory, val message: String) : VideoGenUiState
}

data class ActiveChatUiState(
    val chatId: String? = null,
    val mode: AssistantMode = AssistantMode.CHAT,
    val modelOption: GeminiModelOption = GeminiModelOption.FLASH_3_8,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val streamingCitations: List<Citation> = emptyList(),
    val errorMessage: String? = null
)

data class VoiceChatUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val rmsDb: Float = 0f,
    val partialTranscript: String = "",
    val lastUserUtterance: String = "",
    val lastAiResponse: String = "",
    val errorMessage: String? = null,
    val speechAvailable: Boolean = true
)

class MainViewModel(
    private val firestoreRepo: FirestoreRepository,
    private val geminiRepo: GeminiAiRepository,
    private val currentUserId: String,
    private val filesDir: File,
    private val videoRepo: VideoGenerationRepository = VideoGenerationRepository()
) : ViewModel() {

    val userProfile: StateFlow<UiState<UserProfile?>> = firestoreRepo.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appConfig: StateFlow<AppConfig> = firestoreRepo.observeAppConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppConfig())

    val chatsState: StateFlow<UiState<List<ChatThread>>> = firestoreRepo.observeChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to observe chats"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val imageLogsState: StateFlow<UiState<List<ImageCreationLog>>> = firestoreRepo.observeImageLogs(currentUserId)
        .map<List<ImageCreationLog>, UiState<List<ImageCreationLog>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load image history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val videoLogsState: StateFlow<UiState<List<VideoCreationLog>>> = firestoreRepo.observeVideoLogs(currentUserId)
        .map<List<VideoCreationLog>, UiState<List<VideoCreationLog>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load video history"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _activeChatState = MutableStateFlow(ActiveChatUiState())
    val activeChatState: StateFlow<ActiveChatUiState> = _activeChatState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<ChatMessage>> = _activeChatState
        .map { it.chatId }
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                firestoreRepo.observeChatMessages(currentUserId, chatId)
                    .catch { error ->
                        val fsErr = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsErr == null && error is CancellationException) throw error
                        Log.w(TAG, "Message stream error: ${error.message}")
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _videoGenState = MutableStateFlow<VideoGenUiState>(VideoGenUiState.Idle)
    val videoGenState: StateFlow<VideoGenUiState> = _videoGenState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceChatUiState())
    val voiceState: StateFlow<VoiceChatUiState> = _voiceState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var activeVideoJob: Job? = null

    init {
        viewModelScope.launch {
            val firebaseUser = Firebase.auth.currentUser
            if (firebaseUser != null && firebaseUser.uid == currentUserId) {
                firestoreRepo.ensureUserProfileAndAdminBootstrap(firebaseUser)
            }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun startNewChat(mode: AssistantMode = AssistantMode.CHAT) {
        activeGenerationJob?.cancel()
        _activeChatState.value = ActiveChatUiState(
            chatId = null,
            mode = mode,
            modelOption = _activeChatState.value.modelOption
        )
    }

    fun openExistingChat(thread: ChatThread) {
        activeGenerationJob?.cancel()
        val modelOption = GeminiModelOption.entries.firstOrNull { it.modelId == thread.modelName }
            ?: GeminiModelOption.FLASH_3_8
        _activeChatState.value = ActiveChatUiState(
            chatId = thread.id,
            mode = thread.modeEnum(),
            modelOption = modelOption
        )
    }

    fun setAssistantMode(mode: AssistantMode) {
        _activeChatState.value = _activeChatState.value.copy(mode = mode, errorMessage = null)
    }

    fun setModelOption(option: GeminiModelOption) {
        _activeChatState.value = _activeChatState.value.copy(modelOption = option, errorMessage = null)
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        val partial = _activeChatState.value.streamingText
        val chatId = _activeChatState.value.chatId
        val citations = _activeChatState.value.streamingCitations
        _activeChatState.value = _activeChatState.value.copy(
            isGenerating = false,
            streamingText = "",
            streamingCitations = emptyList()
        )
        if (partial.isNotBlank() && !chatId.isNullOrBlank()) {
            viewModelScope.launch {
                val count = activeMessages.value.size.toLong()
                firestoreRepo.appendMessageToChat(
                    userId = currentUserId,
                    chatId = chatId,
                    role = "model",
                    text = "$partial\n\n_(Generation stopped)_",
                    hasImage = false,
                    citationTitles = citations.map { it.title },
                    citationUrls = citations.map { it.url },
                    currentMessageCount = count
                )
            }
        }
    }

    private fun checkFeatureAndQuota(feature: String): String? {
        val cfg = appConfig.value
        if (cfg.maintenanceMessage.isNotBlank()) {
            return "Maintenance Notice: ${cfg.maintenanceMessage}"
        }
        when (feature) {
            "CHAT", "MATH", "CODE" -> if (!cfg.chatEnabled) return "AI Chat is currently disabled by Super Admin."
            "SEARCH" -> if (!cfg.webSearchEnabled) return "Internet Search is currently disabled by Super Admin."
            "VOICE" -> if (!cfg.voiceEnabled) return "Voice Chat is currently disabled by Super Admin."
            "IMAGE" -> if (!cfg.imageGenEnabled) return "Image Generation is currently disabled by Super Admin."
            "VIDEO" -> if (!cfg.videoGenEnabled) return "Video Generation is currently disabled by Super Admin."
        }
        val profile = (userProfile.value as? UiState.Success)?.data
        if (profile != null) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val usedToday = if (profile.lastRequestDate == today) profile.dailyRequestCount else 0L
            if (usedToday >= cfg.dailyRequestLimit) {
                return "Daily AI request limit (${cfg.dailyRequestLimit}) reached. Resets tomorrow or via Admin settings."
            }
        }
        return null
    }

    fun sendChatMessage(rawPrompt: String, attachedBitmap: Bitmap? = null) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() && attachedBitmap == null) return
        val effectivePrompt = if (prompt.isEmpty()) "Analyze this image in detail." else prompt

        val state = _activeChatState.value
        val featureTag = state.mode.name
        val blockReason = checkFeatureAndQuota(featureTag)
        if (blockReason != null) {
            _activeChatState.value = state.copy(errorMessage = blockReason)
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _activeChatState.value = _activeChatState.value.copy(
                isGenerating = true,
                streamingText = "",
                streamingCitations = emptyList(),
                errorMessage = null
            )

            val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
            val saveToCloud = profile.autoSaveHistory
            var chatId = _activeChatState.value.chatId
            val currentHistory = activeMessages.value

            try {
                if (saveToCloud && chatId.isNullOrBlank()) {
                    val created = firestoreRepo.createChatThread(
                        userId = currentUserId,
                        title = effectivePrompt.take(60),
                        mode = state.mode,
                        modelName = state.modelOption.modelId
                    )
                    chatId = created.getOrThrow()
                    _activeChatState.value = _activeChatState.value.copy(chatId = chatId)
                }

                if (saveToCloud && !chatId.isNullOrBlank()) {
                    firestoreRepo.appendMessageToChat(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "user",
                        text = effectivePrompt,
                        hasImage = attachedBitmap != null,
                        updateThreadTitleIfFirst = if (currentHistory.isEmpty()) effectivePrompt.take(60) else null,
                        currentMessageCount = currentHistory.size.toLong()
                    )
                }

                var finalReply = ""
                var finalCitations = emptyList<Citation>()

                geminiRepo.streamChatMessage(
                    mode = state.mode,
                    modelOption = state.modelOption,
                    history = currentHistory,
                    userMessage = effectivePrompt,
                    attachedBitmap = attachedBitmap,
                    preferredLanguage = profile.preferredLanguage
                ).collect { chunk ->
                    finalReply = chunk.accumulatedText
                    finalCitations = chunk.citations
                    _activeChatState.value = _activeChatState.value.copy(
                        streamingText = finalReply,
                        streamingCitations = finalCitations
                    )
                }

                if (finalReply.isBlank()) {
                    throw IllegalStateException("Model returned an empty response.")
                }

                if (saveToCloud && !chatId.isNullOrBlank()) {
                    firestoreRepo.appendMessageToChat(
                        userId = currentUserId,
                        chatId = chatId,
                        role = "model",
                        text = finalReply,
                        hasImage = false,
                        citationTitles = finalCitations.map { it.title },
                        citationUrls = finalCitations.map { it.url },
                        currentMessageCount = (currentHistory.size + 1).toLong()
                    )
                }

                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = featureTag,
                    modelName = state.modelOption.modelId,
                    success = true
                )

                _activeChatState.value = _activeChatState.value.copy(
                    isGenerating = false,
                    streamingText = "",
                    streamingCitations = emptyList()
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                val msg = "Request timed out after 60 seconds. Please try again."
                firestoreRepo.logSystemError(currentUserId, featureTag, msg)
                _activeChatState.value = _activeChatState.value.copy(
                    isGenerating = false,
                    errorMessage = msg
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                val friendly = formatAiException(e, state.mode)
                firestoreRepo.logSystemError(currentUserId, featureTag, friendly)
                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = featureTag,
                    modelName = state.modelOption.modelId,
                    success = false
                )
                _activeChatState.value = _activeChatState.value.copy(
                    isGenerating = false,
                    errorMessage = friendly
                )
            }
        }
    }

    fun regenerateLastResponse() {
        val msgs = activeMessages.value
        val lastUserMsg = msgs.lastOrNull { it.role == "user" } ?: return
        val lastMsg = msgs.lastOrNull()
        val chatId = _activeChatState.value.chatId
        viewModelScope.launch {
            if (lastMsg != null && lastMsg.role == "model" && !chatId.isNullOrBlank()) {
                firestoreRepo.deleteMessage(currentUserId, chatId, lastMsg.id)
            }
            sendChatMessage(lastUserMsg.text)
        }
    }

    fun clearCurrentChat() {
        val chatId = _activeChatState.value.chatId
        activeGenerationJob?.cancel()
        if (!chatId.isNullOrBlank()) {
            viewModelScope.launch {
                firestoreRepo.deleteChatThread(currentUserId, chatId)
                _activeChatState.value = ActiveChatUiState(mode = _activeChatState.value.mode)
                _statusBanner.value = "Conversation cleared."
            }
        } else {
            _activeChatState.value = ActiveChatUiState(mode = _activeChatState.value.mode)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteChatThread(currentUserId, chatId)
            if (_activeChatState.value.chatId == chatId) {
                _activeChatState.value = ActiveChatUiState()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            val res = firestoreRepo.clearAllChats(currentUserId)
            if (res.isSuccess) {
                _activeChatState.value = ActiveChatUiState()
                _statusBanner.value = "All chat history cleared."
            } else {
                _statusBanner.value = res.exceptionOrNull()?.message ?: "Failed to clear history."
            }
        }
    }

    // Image Studio
    fun generateOrEditImage(prompt: String, aspectRatio: String, referenceBitmap: Bitmap? = null) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Please describe the image you want to create.")
            return
        }
        val blockReason = checkFeatureAndQuota("IMAGE")
        if (blockReason != null) {
            _imageGenState.value = ImageGenUiState.Error(blockReason)
            return
        }

        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
            try {
                val bitmap = geminiRepo.generateImage(
                    prompt = cleanPrompt,
                    aspectRatioLabel = aspectRatio,
                    referenceBitmap = referenceBitmap
                )
                val file = withContext(Dispatchers.IO) {
                    val outDir = File(filesDir, "generated_images").apply { mkdirs() }
                    val target = File(outDir, "ansh_${System.currentTimeMillis()}.png")
                    FileOutputStream(target).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    target
                }

                firestoreRepo.saveImageLog(
                    userId = currentUserId,
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio,
                    modelName = "gemini-nano-banana-2.1",
                    localFilePath = file.absolutePath,
                    status = "SUCCESS"
                )
                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "IMAGE",
                    modelName = "gemini-nano-banana-2.1",
                    success = true
                )
                _imageGenState.value = ImageGenUiState.Success(bitmap, file.absolutePath, cleanPrompt)
            } catch (e: TimeoutCancellationException) {
                val msg = "Image generation timed out after 60 seconds."
                firestoreRepo.logSystemError(currentUserId, "IMAGE", msg)
                _imageGenState.value = ImageGenUiState.Error(msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                val msg = formatImageException(e)
                firestoreRepo.logSystemError(currentUserId, "IMAGE", msg)
                firestoreRepo.saveImageLog(
                    userId = currentUserId,
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio,
                    modelName = "gemini-nano-banana-2.1",
                    localFilePath = "",
                    status = "ERROR"
                )
                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "IMAGE",
                    modelName = "gemini-nano-banana-2.1",
                    success = false
                )
                _imageGenState.value = ImageGenUiState.Error(msg)
            }
        }
    }

    // Video Studio (Gemini Omni 1.2 Flash via secure server-side backend proxy)
    fun generateVideo(
        backendUrl: String,
        prompt: String,
        mode: VideoGenerationMode,
        style: String,
        aspectRatio: String,
        duration: String,
        sourceBitmap: Bitmap?
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.VALIDATION_ERROR,
                "Please describe the video you want to generate."
            )
            return
        }
        if (mode == VideoGenerationMode.IMAGE_TO_VIDEO && sourceBitmap == null) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.VALIDATION_ERROR,
                "Please upload a source image for Image-to-Video animation."
            )
            return
        }

        val cfg = appConfig.value
        if (cfg.maintenanceMessage.isNotBlank()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.SERVICE_UNAVAILABLE,
                "Maintenance Notice: ${cfg.maintenanceMessage}"
            )
            return
        }
        if (!cfg.videoGenEnabled) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.DISABLED_BY_ADMIN,
                "Video generation is disabled in Global Config. Ask the Super Admin to enable it and configure the video backend proxy URL."
            )
            return
        }

        val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val usedToday = if (profile.lastRequestDate == today) profile.dailyRequestCount else 0L
        if (usedToday >= cfg.dailyRequestLimit) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.QUOTA_EXCEEDED,
                "Daily AI request limit (${cfg.dailyRequestLimit}) reached. Resets tomorrow or via Admin settings."
            )
            return
        }

        if (backendUrl.isBlank()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.MISSING_API_CONFIG,
                "Missing server-side video backend URL. Deploy the Cloud Function proxy with GEMINI_API_KEY stored in Cloud Secret Manager, then configure its HTTPS URL in Super Admin Console -> Config."
            )
            return
        }

        activeVideoJob?.cancel()
        activeVideoJob = viewModelScope.launch {
            _videoGenState.value = VideoGenUiState.Generating(stage = "Connecting to secure video backend...")
            try {
                val result = videoRepo.generateVideo(
                    backendUrl = backendUrl,
                    prompt = cleanPrompt,
                    mode = mode,
                    style = style,
                    aspectRatio = aspectRatio,
                    duration = duration,
                    sourceBitmap = sourceBitmap,
                    filesDir = filesDir,
                    onProgress = { progress ->
                        _videoGenState.value = VideoGenUiState.Generating(
                            stage = progress.stage,
                            interactionId = progress.interactionId,
                            elapsedSeconds = progress.elapsedSeconds
                        )
                    }
                )

                val measuredLabel = com.example.data.VideoDurationSpec.formatMeasuredSeconds(
                    result.actualDurationMs,
                    fallbackLabel = duration
                )

                val savedId = firestoreRepo.saveVideoLog(
                    userId = currentUserId,
                    prompt = cleanPrompt,
                    mode = mode.wireValue,
                    style = style,
                    aspectRatio = aspectRatio,
                    duration = measuredLabel,
                    modelName = result.modelName,
                    interactionId = result.interactionId,
                    localFilePath = result.localFilePath,
                    status = "SUCCESS",
                    actualDurationMs = result.actualDurationMs,
                    parentInteractionId = "",
                    segmentCount = result.segmentCount.toLong()
                ).getOrDefault("")

                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "VIDEO",
                    modelName = result.modelName,
                    success = true
                )

                val log = VideoCreationLog(
                    id = savedId,
                    userId = currentUserId,
                    prompt = cleanPrompt,
                    mode = mode.wireValue,
                    style = style,
                    aspectRatio = aspectRatio,
                    duration = measuredLabel,
                    modelName = result.modelName,
                    interactionId = result.interactionId,
                    localFilePath = result.localFilePath,
                    status = "SUCCESS",
                    actualDurationMs = result.actualDurationMs,
                    parentInteractionId = "",
                    segmentCount = result.segmentCount.toLong()
                )
                _videoGenState.value = VideoGenUiState.Success(log)
            } catch (e: TimeoutCancellationException) {
                val msg = "Video generation timed out after 4 minutes while waiting for cloud rendering."
                Log.e(TAG, msg, e)
                firestoreRepo.logSystemError(currentUserId, "VIDEO", msg)
                firestoreRepo.saveVideoLog(
                    userId = currentUserId,
                    prompt = cleanPrompt,
                    mode = mode.wireValue,
                    style = style,
                    aspectRatio = aspectRatio,
                    duration = duration,
                    modelName = SUPPORTED_VIDEO_MODEL,
                    interactionId = "",
                    localFilePath = "",
                    status = "ERROR",
                    errorMessage = msg
                )
                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "VIDEO",
                    modelName = SUPPORTED_VIDEO_MODEL,
                    success = false
                )
                _videoGenState.value = VideoGenUiState.Error(VideoErrorCategory.SERVICE_UNAVAILABLE, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VideoGenerationException) {
                Log.e(TAG, "Video generation failed (${e.category}): ${e.message}", e)
                firestoreRepo.logSystemError(currentUserId, "VIDEO", "${e.category.name}: ${e.message}")
                if (e.category != VideoErrorCategory.VALIDATION_ERROR &&
                    e.category != VideoErrorCategory.MISSING_API_CONFIG &&
                    e.category != VideoErrorCategory.DISABLED_BY_ADMIN
                ) {
                    firestoreRepo.saveVideoLog(
                        userId = currentUserId,
                        prompt = cleanPrompt,
                        mode = mode.wireValue,
                        style = style,
                        aspectRatio = aspectRatio,
                        duration = duration,
                        modelName = SUPPORTED_VIDEO_MODEL,
                        interactionId = "",
                        localFilePath = "",
                        status = "ERROR",
                        errorMessage = e.message
                    )
                    firestoreRepo.recordAiUsageAndIncrementQuota(
                        userId = currentUserId,
                        currentProfile = profile,
                        feature = "VIDEO",
                        modelName = SUPPORTED_VIDEO_MODEL,
                        success = false
                    )
                }
                _videoGenState.value = VideoGenUiState.Error(e.category, e.message)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unexpected error during video generation."
                Log.e(TAG, msg, e)
                firestoreRepo.logSystemError(currentUserId, "VIDEO", msg)
                _videoGenState.value = VideoGenUiState.Error(VideoErrorCategory.GENERATION_FAILED, msg)
            }
        }
    }

    fun extendVideo(
        backendUrl: String,
        sourceLog: VideoCreationLog,
        continuationPrompt: String,
        extensionDuration: String
    ) {
        val cleanPrompt = continuationPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.VALIDATION_ERROR,
                "Please describe how the video should continue."
            )
            return
        }

        val cfg = appConfig.value
        if (cfg.maintenanceMessage.isNotBlank()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.SERVICE_UNAVAILABLE,
                "Maintenance Notice: ${cfg.maintenanceMessage}"
            )
            return
        }
        if (!cfg.videoGenEnabled) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.DISABLED_BY_ADMIN,
                "Video generation is disabled in Global Config."
            )
            return
        }

        val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val usedToday = if (profile.lastRequestDate == today) profile.dailyRequestCount else 0L
        if (usedToday >= cfg.dailyRequestLimit) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.QUOTA_EXCEEDED,
                "Daily AI request limit (${cfg.dailyRequestLimit}) reached."
            )
            return
        }

        if (backendUrl.isBlank()) {
            _videoGenState.value = VideoGenUiState.Error(
                VideoErrorCategory.MISSING_API_CONFIG,
                "Missing server-side video backend URL."
            )
            return
        }

        activeVideoJob?.cancel()
        activeVideoJob = viewModelScope.launch {
            _videoGenState.value = VideoGenUiState.Generating(stage = "Submitting video extension to $SUPPORTED_VIDEO_MODEL...")
            try {
                val result = videoRepo.extendVideo(
                    backendUrl = backendUrl,
                    parentInteractionId = sourceLog.interactionId,
                    parentFilePath = sourceLog.localFilePath,
                    parentActualDurationMs = sourceLog.actualDurationMs,
                    parentSegmentCount = sourceLog.segmentCount.toInt().coerceAtLeast(1),
                    continuationPrompt = cleanPrompt,
                    style = sourceLog.style,
                    aspectRatio = sourceLog.aspectRatio,
                    extensionDuration = extensionDuration,
                    filesDir = filesDir,
                    onProgress = { progress ->
                        _videoGenState.value = VideoGenUiState.Generating(
                            stage = progress.stage,
                            interactionId = progress.interactionId,
                            elapsedSeconds = progress.elapsedSeconds
                        )
                    }
                )

                val measuredLabel = com.example.data.VideoDurationSpec.formatMeasuredSeconds(
                    result.actualDurationMs,
                    fallbackLabel = "${result.requestedDurationSeconds}s"
                )
                val combinedPrompt = "${sourceLog.prompt} -> $cleanPrompt".take(1000)

                val savedId = firestoreRepo.saveVideoLog(
                    userId = currentUserId,
                    prompt = combinedPrompt,
                    mode = VideoGenerationMode.EXTEND.wireValue,
                    style = sourceLog.style,
                    aspectRatio = sourceLog.aspectRatio,
                    duration = measuredLabel,
                    modelName = result.modelName,
                    interactionId = result.interactionId,
                    localFilePath = result.localFilePath,
                    status = "SUCCESS",
                    actualDurationMs = result.actualDurationMs,
                    parentInteractionId = result.parentInteractionId,
                    segmentCount = result.segmentCount.toLong()
                ).getOrDefault("")

                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "VIDEO",
                    modelName = result.modelName,
                    success = true
                )

                val extendedLog = VideoCreationLog(
                    id = savedId,
                    userId = currentUserId,
                    prompt = combinedPrompt,
                    mode = VideoGenerationMode.EXTEND.wireValue,
                    style = sourceLog.style,
                    aspectRatio = sourceLog.aspectRatio,
                    duration = measuredLabel,
                    modelName = result.modelName,
                    interactionId = result.interactionId,
                    localFilePath = result.localFilePath,
                    status = "SUCCESS",
                    actualDurationMs = result.actualDurationMs,
                    parentInteractionId = result.parentInteractionId,
                    segmentCount = result.segmentCount.toLong()
                )
                _videoGenState.value = VideoGenUiState.Success(extendedLog)
            } catch (e: TimeoutCancellationException) {
                val msg = "Video extension timed out after 4 minutes while waiting for cloud rendering."
                firestoreRepo.logSystemError(currentUserId, "VIDEO", msg)
                _videoGenState.value = VideoGenUiState.Error(VideoErrorCategory.SERVICE_UNAVAILABLE, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VideoGenerationException) {
                firestoreRepo.logSystemError(currentUserId, "VIDEO", "${e.category.name}: ${e.message}")
                _videoGenState.value = VideoGenUiState.Error(e.category, e.message)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unexpected error during video extension."
                firestoreRepo.logSystemError(currentUserId, "VIDEO", msg)
                _videoGenState.value = VideoGenUiState.Error(VideoErrorCategory.GENERATION_FAILED, msg)
            }
        }
    }

    fun cancelVideoGeneration() {
        activeVideoJob?.cancel()
        activeVideoJob = null
        _videoGenState.value = VideoGenUiState.Idle
    }

    fun resetVideoResult() {
        activeVideoJob?.cancel()
        activeVideoJob = null
        _videoGenState.value = VideoGenUiState.Idle
    }

    fun selectHistoryVideo(log: VideoCreationLog) {
        if (log.status == "SUCCESS" && log.localFilePath.isNotBlank()) {
            val file = File(log.localFilePath)
            if (file.exists() && file.length() > 0L) {
                _videoGenState.value = VideoGenUiState.Success(log)
                return
            }
        }
        val msg = log.errorMessage.ifBlank { "Video MP4 file is unavailable on this device." }
        _videoGenState.value = VideoGenUiState.Error(VideoErrorCategory.GENERATION_FAILED, msg)
    }

    fun deleteVideoLog(log: VideoCreationLog) {
        viewModelScope.launch {
            if (log.localFilePath.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    runCatching { File(log.localFilePath).delete() }
                }
            }
            if (log.id.isNotBlank()) {
                firestoreRepo.deleteVideoLog(currentUserId, log.id)
            }
            val current = _videoGenState.value
            if (current is VideoGenUiState.Success && current.videoLog.id == log.id && log.id.isNotBlank()) {
                _videoGenState.value = VideoGenUiState.Idle
            }
        }
    }

    // Voice Chat
    fun updateVoiceAvailability(available: Boolean) {
        _voiceState.value = _voiceState.value.copy(speechAvailable = available)
    }

    fun setVoiceListening(listening: Boolean, partial: String = "") {
        _voiceState.value = _voiceState.value.copy(
            isListening = listening,
            partialTranscript = partial,
            errorMessage = if (listening) null else _voiceState.value.errorMessage
        )
    }

    fun updateVoiceRms(rmsDb: Float) {
        _voiceState.value = _voiceState.value.copy(rmsDb = rmsDb)
    }

    fun setVoiceSpeaking(speaking: Boolean) {
        _voiceState.value = _voiceState.value.copy(isSpeaking = speaking)
    }

    fun toggleVoiceMute() {
        val nextMuted = !_voiceState.value.isMuted
        _voiceState.value = _voiceState.value.copy(
            isMuted = nextMuted,
            isSpeaking = if (nextMuted) false else _voiceState.value.isSpeaking
        )
    }

    fun setVoiceError(message: String?) {
        _voiceState.value = _voiceState.value.copy(
            isListening = false,
            isThinking = false,
            errorMessage = message
        )
    }

    fun submitVoiceQuery(spokenText: String, onSpeakReply: (String) -> Unit) {
        val query = spokenText.trim()
        if (query.isEmpty()) return

        val blockReason = checkFeatureAndQuota("VOICE")
        if (blockReason != null) {
            _voiceState.value = _voiceState.value.copy(errorMessage = blockReason)
            return
        }

        viewModelScope.launch {
            _voiceState.value = _voiceState.value.copy(
                isListening = false,
                isThinking = true,
                partialTranscript = "",
                lastUserUtterance = query,
                lastAiResponse = "",
                errorMessage = null
            )
            val profile = (userProfile.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
            try {
                var reply = ""
                geminiRepo.streamChatMessage(
                    mode = AssistantMode.VOICE,
                    modelOption = GeminiModelOption.FLASH_3_8,
                    history = emptyList(),
                    userMessage = query,
                    preferredLanguage = profile.preferredLanguage
                ).collect { chunk ->
                    reply = chunk.accumulatedText
                    _voiceState.value = _voiceState.value.copy(lastAiResponse = reply)
                }

                if (reply.isBlank()) {
                    throw IllegalStateException("Empty voice response received.")
                }

                firestoreRepo.recordAiUsageAndIncrementQuota(
                    userId = currentUserId,
                    currentProfile = profile,
                    feature = "VOICE",
                    modelName = GeminiModelOption.FLASH_3_8.modelId,
                    success = true
                )

                _voiceState.value = _voiceState.value.copy(
                    isThinking = false,
                    lastAiResponse = reply
                )
                if (!_voiceState.value.isMuted && profile.voiceAutoSpeak) {
                    onSpeakReply(reply)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice AI error", e)
                val msg = formatAiException(e, AssistantMode.VOICE)
                firestoreRepo.logSystemError(currentUserId, "VOICE", msg)
                _voiceState.value = _voiceState.value.copy(
                    isThinking = false,
                    errorMessage = msg
                )
            }
        }
    }

    // Preferences
    fun updatePreferences(
        displayName: String? = null,
        preferredLanguage: String? = null,
        themeMode: ThemeMode? = null,
        voiceSpeechRate: Double? = null,
        voiceAutoSpeak: Boolean? = null,
        autoSaveHistory: Boolean? = null
    ) {
        viewModelScope.launch {
            val res = firestoreRepo.updateUserPreferences(
                userId = currentUserId,
                displayName = displayName,
                preferredLanguage = preferredLanguage,
                themeMode = themeMode,
                voiceSpeechRate = voiceSpeechRate,
                voiceAutoSpeak = voiceAutoSpeak,
                autoSaveHistory = autoSaveHistory
            )
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.message ?: "Failed to update settings."
            }
        }
    }

    private fun formatAiException(e: Exception, mode: AssistantMode): String {
        val raw = e.localizedMessage ?: "AI request failed."
        return when {
            raw.contains("403") || raw.contains("App Check", ignoreCase = true) ->
                "App Check attestation failed (HTTP 403). Ensure Firebase AI Logic is provisioned and launch via standard emulator intent."
            raw.contains("billing", ignoreCase = true) || raw.contains("Blaze", ignoreCase = true) ->
                if (mode == AssistantMode.SEARCH) {
                    "Live Google Search grounding or selected model requires Firebase Blaze billing enabled on the project."
                } else {
                    "This model requires the Firebase Blaze plan. Switch to Gemini 3.8 Flash (free tier) or upgrade billing."
                }
            raw.contains("quota", ignoreCase = true) || raw.contains("429") ->
                "Rate limit or free-tier quota exceeded. Please wait a moment and try again."
            else -> raw
        }
    }

    private fun formatImageException(e: Exception): String {
        val raw = e.localizedMessage ?: "Image generation failed."
        return when {
            raw.contains("billing", ignoreCase = true) ||
                raw.contains("Blaze", ignoreCase = true) ||
                raw.contains("403") ||
                raw.contains("permission", ignoreCase = true) ->
                "Image generation (gemini-nano-banana-2.1) requires the Firebase Blaze pay-as-you-go plan or valid App Check attestation. Details: $raw"
            raw.contains("quota", ignoreCase = true) ->
                "Image generation quota exceeded. Please try again later."
            else -> raw
        }
    }

    companion object {
        fun provideFactory(
            firestoreRepo: FirestoreRepository,
            geminiRepo: GeminiAiRepository,
            currentUserId: String,
            filesDir: File
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(firestoreRepo, geminiRepo, currentUserId, filesDir)
            }
        }
    }
}
