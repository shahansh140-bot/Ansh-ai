package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.AccountStatus
import com.example.model.AiRequestMetric
import com.example.model.AppConfig
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.GeminiModelOption
import com.example.model.ImageCreationLog
import com.example.model.SystemErrorLog
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.example.model.UserRole
import com.example.model.VideoCreationLog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FirestoreRepo"
private const val OWNER_ADMIN_EMAIL = "shahansh140@gmail.com"

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun todayDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun ensureUserProfileAndAdminBootstrap(user: FirebaseUser): Result<UserProfile> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val isVerifiedOwner =
                user.email.equals(OWNER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified

            if (isVerifiedOwner) {
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = adminRef.get().await()
                if (!adminSnap.exists()) {
                    adminRef.set(
                        mapOf(
                            "uid" to uid,
                            "email" to OWNER_ADMIN_EMAIL,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                // Seed default global app_config if absent
                val configRef = db.collection("app_config").document("global")
                if (!configRef.get().await().exists()) {
                    configRef.set(
                        mapOf(
                            "dailyRequestLimit" to 50L,
                            "chatEnabled" to true,
                            "voiceEnabled" to true,
                            "imageGenEnabled" to true,
                            "webSearchEnabled" to true,
                            "videoGenEnabled" to false,
                            "videoBackendUrl" to "",
                            "maintenanceMessage" to "",
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            val userRef = db.collection("users").document(uid)
            val snapshot = userRef.get().await()
            if (!snapshot.exists()) {
                val role = if (isVerifiedOwner) UserRole.SUPER_ADMIN.name else UserRole.USER.name
                val name = (user.displayName?.takeIf { it.isNotBlank() } ?: "ANSH User").take(100)
                val email = (user.email ?: "").take(120)
                val photoUrl = user.photoUrl?.toString()?.take(500)
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", name)
                    put("email", email)
                    if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl)
                    put("status", AccountStatus.ACTIVE.name)
                    put("role", role)
                    put("dailyRequestCount", 0L)
                    put("totalRequests", 0L)
                    put("lastRequestDate", todayDateKey())
                    put("preferredLanguage", "English")
                    put("themeMode", ThemeMode.ELECTRIC_DARK.name)
                    put("voiceSpeechRate", 1.0)
                    put("voiceAutoSpeak", true)
                    put("autoSaveHistory", true)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(payload).await()
                val created = userRef.get().await()
                    .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(userId = uid, displayName = name, email = email, role = role)
                Result.success(created)
            } else {
                val currentRole = snapshot.getString("role")
                val needsPromotion = isVerifiedOwner && currentRole != UserRole.SUPER_ADMIN.name
                val needsDemotion = !isVerifiedOwner && currentRole == UserRole.SUPER_ADMIN.name
                if (needsPromotion) {
                    userRef.update(
                        mapOf(
                            "role" to UserRole.SUPER_ADMIN.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else if (needsDemotion) {
                    userRef.update(
                        mapOf(
                            "role" to UserRole.USER.name,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                val refreshed = if (needsPromotion || needsDemotion) userRef.get().await() else snapshot
                val existing = refreshed.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid)
                Result.success(existing)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun verifyAndBootstrapAdminAccess(user: FirebaseUser): Result<Boolean> {
        return try {
            val isVerifiedOwner =
                user.email.equals(OWNER_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
            val profileResult = ensureUserProfileAndAdminBootstrap(user)
            if (profileResult.isFailure) {
                return Result.failure(profileResult.exceptionOrNull() ?: IllegalStateException("Profile check failed"))
            }
            val adminDoc = db.collection("admins").document(user.uid).get().await()
            val profile = profileResult.getOrNull()
            val isAdmin = isVerifiedOwner && (adminDoc.exists() || profile?.roleEnum() == UserRole.SUPER_ADMIN)
            Result.success(isAdmin)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserPreferences(
        userId: String,
        displayName: String? = null,
        preferredLanguage: String? = null,
        themeMode: ThemeMode? = null,
        voiceSpeechRate: Double? = null,
        voiceAutoSpeak: Boolean? = null,
        autoSaveHistory: Boolean? = null
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Refusing cross-user preference write." }
            val updates = buildMap<String, Any> {
                displayName?.trim()?.take(100)?.takeIf { it.isNotEmpty() }?.let { put("displayName", it) }
                preferredLanguage?.trim()?.take(30)?.takeIf { it.isNotEmpty() }?.let { put("preferredLanguage", it) }
                themeMode?.let { put("themeMode", it.name) }
                voiceSpeechRate?.coerceIn(0.5, 2.0)?.let { put("voiceSpeechRate", it) }
                voiceAutoSpeak?.let { put("voiceAutoSpeak", it) }
                autoSaveHistory?.let { put("autoSaveHistory", it) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordAiUsageAndIncrementQuota(
        userId: String,
        currentProfile: UserProfile,
        feature: String,
        modelName: String,
        success: Boolean
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val today = todayDateKey()
            val newDailyCount = if (currentProfile.lastRequestDate == today) {
                currentProfile.dailyRequestCount + 1L
            } else {
                1L
            }
            val newTotal = currentProfile.totalRequests + 1L
            db.collection("users").document(uid).update(
                mapOf(
                    "dailyRequestCount" to newDailyCount,
                    "totalRequests" to newTotal,
                    "lastRequestDate" to today,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val metricRef = db.collection("telemetry_events").document()
            metricRef.set(
                mapOf(
                    "userId" to uid,
                    "feature" to feature.take(20),
                    "modelName" to modelName.take(60),
                    "success" to success,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun logSystemError(userId: String, feature: String, message: String) {
        try {
            val uid = auth.currentUser?.uid ?: return
            if (uid != userId) return
            db.collection("system_errors").document().set(
                mapOf(
                    "userId" to uid,
                    "feature" to feature.take(60).ifBlank { "APP" },
                    "message" to message.take(1000).ifBlank { "Unknown error" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log system error to Firestore: ${e.message}")
        }
    }

    fun observeAppConfig(): Flow<AppConfig> = flow {
        val path = "app_config/global"
        emitAll(
            db.collection("app_config").document("global")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(AppConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: AppConfig()
                    } else {
                        AppConfig()
                    }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Using default AppConfig due to observe error: ${error.message}")
                    emit(AppConfig())
                }
        )
    }

    // Chat Threads & Messages (Private per user)
    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        val path = "users/$userId/chats"
        emitAll(
            db.collection("users").document(userId).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChatMessages(userId: String, chatId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChatThread(
        userId: String,
        title: String,
        mode: AssistantMode,
        modelName: String
    ): Result<String> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val docRef = db.collection("users").document(uid).collection("chats").document()
            val cleanTitle = title.trim().ifBlank { "New Chat" }.take(120)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "mode" to mode.name,
                    "modelName" to modelName.take(60),
                    "lastMessagePreview" to "",
                    "messageCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun appendMessageToChat(
        userId: String,
        chatId: String,
        role: String,
        text: String,
        hasImage: Boolean,
        citationTitles: List<String> = emptyList(),
        citationUrls: List<String> = emptyList(),
        updateThreadTitleIfFirst: String? = null,
        currentMessageCount: Long = 0L
    ): Result<String> {
        val path = "users/$userId/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document()
            val boundedText = text.take(50000)
            val boundedTitles = citationTitles.take(15).map { it.take(200) }
            val boundedUrls = citationUrls.take(15).map { it.take(500) }

            msgRef.set(
                mapOf(
                    "userId" to uid,
                    "chatId" to chatId,
                    "role" to role,
                    "text" to boundedText,
                    "hasImage" to hasImage,
                    "citationTitles" to boundedTitles,
                    "citationUrls" to boundedUrls,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val chatUpdates = buildMap<String, Any> {
                updateThreadTitleIfFirst?.trim()?.take(120)?.takeIf { it.isNotEmpty() }?.let {
                    put("title", it)
                }
                put("lastMessagePreview", boundedText.replace("\n", " ").take(300))
                put("messageCount", currentMessageCount + 1L)
                put("updatedAt", FieldValue.serverTimestamp())
            }
            chatRef.update(chatUpdates).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(userId: String, chatId: String, messageId: String): Result<Unit> {
        val path = "users/$userId/chats/$chatId/messages/$messageId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChatThread(userId: String, chatId: String): Result<Unit> {
        val path = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgs = chatRef.collection("messages").get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllChats(userId: String): Result<Unit> {
        val path = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val chats = db.collection("users").document(uid).collection("chats").get().await()
            for (chatDoc in chats.documents) {
                val msgs = chatDoc.reference.collection("messages").get().await()
                for (msgDoc in msgs.documents) {
                    msgDoc.reference.delete().await()
                }
                chatDoc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // Image Creation Logs
    fun observeImageLogs(userId: String): Flow<List<ImageCreationLog>> = flow {
        val path = "users/$userId/imageLogs"
        emitAll(
            db.collection("users").document(userId).collection("imageLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ImageCreationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveImageLog(
        userId: String,
        prompt: String,
        aspectRatio: String,
        modelName: String,
        localFilePath: String,
        status: String
    ): Result<Unit> {
        val path = "users/$userId/imageLogs"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid).collection("imageLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "prompt" to prompt.trim().take(1000).ifBlank { "Image prompt" },
                    "aspectRatio" to aspectRatio.take(20),
                    "modelName" to modelName.take(60),
                    "localFilePath" to localFilePath.take(500),
                    "status" to status,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // Video Creation Logs
    fun observeVideoLogs(userId: String): Flow<List<VideoCreationLog>> = flow {
        val path = "users/$userId/videoLogs"
        emitAll(
            db.collection("users").document(userId).collection("videoLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VideoCreationLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveVideoLog(
        userId: String,
        prompt: String,
        mode: String,
        style: String,
        aspectRatio: String,
        duration: String,
        modelName: String,
        interactionId: String,
        localFilePath: String,
        status: String,
        errorMessage: String = "",
        actualDurationMs: Long = 0L,
        parentInteractionId: String = "",
        segmentCount: Long = 1L
    ): Result<String> {
        val path = "users/$userId/videoLogs"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeMode = when (mode) {
                "IMAGE_TO_VIDEO", "EXTEND" -> mode
                else -> "TEXT_TO_VIDEO"
            }
            val docRef = db.collection("users").document(uid).collection("videoLogs").document()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "prompt" to prompt.trim().take(1000).ifBlank { "Video prompt" },
                    "mode" to safeMode,
                    "style" to style.trim().take(40).ifBlank { "Cinematic" },
                    "aspectRatio" to aspectRatio.take(20).ifBlank { "16:9" },
                    "duration" to duration.take(20).ifBlank { "10s" },
                    "modelName" to modelName.take(60).ifBlank { "gemini-omni-1.2-flash" },
                    "interactionId" to interactionId.take(200),
                    "localFilePath" to localFilePath.take(500),
                    "status" to if (status == "ERROR") "ERROR" else "SUCCESS",
                    "errorMessage" to errorMessage.take(1000),
                    "actualDurationMs" to actualDurationMs.coerceIn(0L, 600_000L),
                    "parentInteractionId" to parentInteractionId.take(200),
                    "segmentCount" to segmentCount.coerceIn(1L, 20L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideoLog(userId: String, logId: String): Result<Unit> {
        val path = "users/$userId/videoLogs/$logId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid)
                .collection("videoLogs").document(logId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // Super Admin Operations (protected server-side by firestore.rules)
    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeTelemetryForAdmin(): Flow<List<AiRequestMetric>> = flow {
        val path = "telemetry_events"
        emitAll(
            db.collection("telemetry_events")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AiRequestMetric::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeSystemErrorsForAdmin(): Flow<List<SystemErrorLog>> = flow {
        val path = "system_errors"
        emitAll(
            db.collection("system_errors")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SystemErrorLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun adminSetUserStatus(targetUid: String, status: AccountStatus): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            requireUserId()
            db.collection("users").document(targetUid).update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateGlobalConfig(config: AppConfig): Result<Unit> {
        val path = "app_config/global"
        return try {
            val uid = requireUserId()
            val ref = db.collection("app_config").document("global")
            val payload = mapOf(
                "dailyRequestLimit" to config.dailyRequestLimit.coerceIn(1L, 10000L),
                "chatEnabled" to config.chatEnabled,
                "voiceEnabled" to config.voiceEnabled,
                "imageGenEnabled" to config.imageGenEnabled,
                "webSearchEnabled" to config.webSearchEnabled,
                "videoGenEnabled" to config.videoGenEnabled,
                "videoBackendUrl" to config.videoBackendUrl.trim().take(500),
                "maintenanceMessage" to config.maintenanceMessage.take(300),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ref.get().await().exists()) {
                ref.update(payload).await()
            } else {
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminDeleteSystemError(errorId: String): Result<Unit> {
        val path = "system_errors/$errorId"
        return try {
            requireUserId()
            db.collection("system_errors").document(errorId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
