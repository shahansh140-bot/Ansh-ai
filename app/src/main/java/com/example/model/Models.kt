package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AccountStatus {
    ACTIVE, DISABLED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

enum class UserRole {
    USER, SUPER_ADMIN;

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

enum class ThemeMode(val label: String) {
    ELECTRIC_DARK("Electric Obsidian"),
    OLED_BLACK("OLED Pitch Black"),
    MIDNIGHT_INDIGO("Midnight Indigo");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: ELECTRIC_DARK
    }
}

enum class AssistantMode(val label: String) {
    CHAT("AI Chat"),
    SEARCH("Web Search"),
    MATH("Math Solver"),
    CODE("Code Studio"),
    VOICE("Voice Chat");

    companion object {
        fun fromRaw(raw: String?): AssistantMode =
            entries.firstOrNull { it.name == raw } ?: CHAT
    }
}

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val subtitle: String,
    val requiresBilling: Boolean
) {
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash", "Default fast & intelligent", false),
    FLASH_LITE_3_5("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra-fast low cost", false),
    ARGON_4("gemini-4-argon", "Gemini 4 Argon", "Deep STEM & code (Blaze)", true)
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "ANSH User",
    val email: String = "",
    val photoUrl: String? = null,
    val status: String = AccountStatus.ACTIVE.name,
    val role: String = UserRole.USER.name,
    val dailyRequestCount: Long = 0L,
    val totalRequests: Long = 0L,
    val lastRequestDate: String = "",
    val preferredLanguage: String = "English",
    val themeMode: String = ThemeMode.ELECTRIC_DARK.name,
    val voiceSpeechRate: Double = 1.0,
    val voiceAutoSpeak: Boolean = true,
    val autoSaveHistory: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun accountStatusEnum(): AccountStatus = AccountStatus.fromRaw(status)
    fun roleEnum(): UserRole = UserRole.fromRaw(role)
    fun themeModeEnum(): ThemeMode = ThemeMode.fromRaw(themeMode)
}

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Chat",
    val mode: String = AssistantMode.CHAT.name,
    val modelName: String = GeminiModelOption.FLASH_3_8.modelId,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun modeEnum(): AssistantMode = AssistantMode.fromRaw(mode)
}

data class Citation(
    val title: String,
    val url: String
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val text: String = "",
    val hasImage: Boolean = false,
    val citationTitles: List<String> = emptyList(),
    val citationUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun citations(): List<Citation> = citationUrls.mapIndexed { idx, url ->
        Citation(
            title = citationTitles.getOrNull(idx)?.ifBlank { url } ?: url,
            url = url
        )
    }
}

@Keep
@IgnoreExtraProperties
data class ImageCreationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val aspectRatio: String = "1:1",
    val modelName: String = "gemini-nano-banana-2.1",
    val localFilePath: String = "",
    val status: String = "SUCCESS",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VideoCreationLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val prompt: String = "",
    val mode: String = "TEXT_TO_VIDEO",
    val style: String = "Cinematic",
    val aspectRatio: String = "16:9",
    val duration: String = "10s",
    val modelName: String = "gemini-omni-1.2-flash",
    val interactionId: String = "",
    val localFilePath: String = "",
    val status: String = "SUCCESS",
    val errorMessage: String = "",
    val actualDurationMs: Long = 0L,
    val parentInteractionId: String = "",
    val segmentCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "global",
    val dailyRequestLimit: Long = 50L,
    val chatEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
    val imageGenEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val videoGenEnabled: Boolean = false,
    val videoBackendUrl: String = "",
    val maintenanceMessage: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SystemErrorLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val feature: String = "",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiRequestMetric(
    @DocumentId val id: String = "",
    val userId: String = "",
    val feature: String = "CHAT",
    val modelName: String = GeminiModelOption.FLASH_3_8.modelId,
    val success: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
