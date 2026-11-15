package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.MediaValidationResult
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.AiTurn
import com.example.domain.model.BabaAiResponse
import com.example.domain.model.FamilyEvent
import com.example.domain.model.FamilyMedia
import com.example.domain.model.FamilyMessage
import com.example.domain.model.FamilyNotification
import com.example.domain.model.IdentityMode
import com.example.domain.model.PrivateConversation
import com.example.domain.model.PrivateMessage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessagingHubRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing messaging.")

    fun observeFamilyMessages(familyId: String): Flow<List<FamilyMessage>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("messages")
        emitAll(
            col.whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FamilyMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendGenuineFamilyMessage(
        familyId: String,
        senderName: String,
        senderRole: String,
        recipientScope: String,
        visibleToUids: List<String>,
        messageKind: String,
        textContent: String,
        audioDurationSec: Long = 0L,
        localAudioPath: String = "",
        mediaId: String = "",
        memberUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val payload = mutableMapOf<String, Any>(
            "familyId" to familyId,
            "senderUid" to uid,
            "senderName" to senderName.ifBlank { "Family Member" }.take(80),
            "senderRole" to senderRole,
            "recipientScope" to recipientScope,
            "visibleToUids" to (visibleToUids + uid).distinct().take(20),
            "messageKind" to messageKind,
            "textContent" to textContent.trim().take(3000),
            "audioDurationSec" to audioDurationSec.coerceIn(0L, 600L),
            "isHumanAuthored" to true,
            "memberUids" to memberUids.distinct().take(20),
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (localAudioPath.isNotBlank()) {
            payload["localAudioPath"] = localAudioPath.take(300)
        }
        if (mediaId.isNotBlank()) {
            payload["mediaId"] = mediaId.take(128)
        }
        db.collection("families").document(familyId).collection("messages").document(msgId).set(payload).await()
        msgId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/messages")
    }

    fun observeFamilyEvents(familyId: String): Flow<List<FamilyEvent>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("events")
        emitAll(
            col.whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FamilyEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createFamilyEvent(
        familyId: String,
        title: String,
        description: String,
        eventDateLabel: String,
        category: String,
        childVisible: Boolean,
        memberUids: List<String>,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val eventId = "evt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val visibleTo = if (childVisible) memberUids else adultUids
        val payload = mapOf(
            "familyId" to familyId,
            "createdBy" to uid,
            "title" to title.trim().take(120),
            "description" to description.trim().take(1000),
            "eventDateLabel" to eventDateLabel.trim().take(80),
            "category" to category,
            "childVisible" to childVisible,
            "visibleToUids" to (visibleTo + uid).distinct().take(20),
            "memberUids" to memberUids.distinct().take(20),
            "adultUids" to adultUids.distinct().take(6),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("families").document(familyId).collection("events").document(eventId).set(payload).await()
        eventId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/events")
    }

    // --- Wife / Adult Owner-Only Private Companion Conversations ---
    fun observePrivateConversations(): Flow<List<PrivateConversation>> = flow {
        val uid = requireUserId()
        val col = db.collection("users").document(uid).collection("privateConversations")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(PrivateConversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateMessages(conversationId: String): Flow<List<PrivateMessage>> = flow {
        val uid = requireUserId()
        val col = db.collection("users").document(uid)
            .collection("privateConversations").document(conversationId)
            .collection("messages")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(PrivateMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensurePrivateConversation(conversationId: String = "wife_companion_main", title: String = "Private BABA Companion"): Result<String> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("privateConversations").document(conversationId)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "ownerUid" to uid,
                    "title" to title.take(120),
                    "agentMode" to IdentityMode.BABA_COMPANION.apiValue,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        conversationId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/privateConversations/$conversationId")
    }

    suspend fun appendPrivateMessage(
        conversationId: String,
        senderType: String,
        text: String,
        safetyStatus: String = "approved"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgId = "pmsg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val msgRef = db.collection("users").document(uid)
            .collection("privateConversations").document(conversationId)
            .collection("messages").document(msgId)
        msgRef.set(
            mapOf(
                "ownerUid" to uid,
                "senderType" to senderType,
                "text" to text.take(6000),
                "identityMode" to IdentityMode.BABA_COMPANION.apiValue,
                "disclosure" to IdentityMode.BABA_COMPANION.disclosureText,
                "safetyStatus" to safetyStatus,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        msgId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/privateConversations/$conversationId/messages")
    }

    // --- Family AI Sessions (Digital Baba / Storyland / Learning Academy) ---
    fun observeAiSessionTurns(familyId: String, sessionId: String): Flow<List<AiTurn>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId)
            .collection("aiSessions").document(sessionId)
            .collection("turns")
        emitAll(
            col.whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AiTurn::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureAiSession(
        familyId: String,
        sessionId: String,
        surface: String,
        agentMode: IdentityMode,
        guardianOversight: Boolean,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val visibleTo = if (guardianOversight) (adultUids + uid).distinct().take(20) else listOf(uid)
        val ref = db.collection("families").document(familyId).collection("aiSessions").document(sessionId)
        if (!ref.get().await().exists()) {
            ref.set(
                mapOf(
                    "familyId" to familyId,
                    "participantUid" to uid,
                    "surface" to surface,
                    "agentMode" to agentMode.apiValue,
                    "guardianOversight" to guardianOversight,
                    "visibleToUids" to visibleTo,
                    "adultUids" to adultUids.distinct().take(6),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        sessionId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "families/$familyId/aiSessions/$sessionId")
    }

    suspend fun appendAiTurn(
        familyId: String,
        sessionId: String,
        senderType: String,
        text: String,
        aiResponse: BabaAiResponse? = null,
        guardianOversight: Boolean,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val turnId = "turn_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val visibleTo = if (guardianOversight) (adultUids + uid).distinct().take(20) else listOf(uid)
        val payload = mutableMapOf<String, Any>(
            "familyId" to familyId,
            "participantUid" to uid,
            "senderType" to senderType,
            "text" to text.take(6000),
            "identityMode" to (aiResponse?.identityMode?.apiValue ?: IdentityMode.DIGITAL_BABA.apiValue),
            "sourceType" to (aiResponse?.sourceType?.apiValue ?: "human_message"),
            "sourceReferences" to (aiResponse?.sourceReferences?.take(20) ?: emptyList<String>()),
            "requiresHumanInput" to (aiResponse?.requiresHumanInput ?: false),
            "safetyStatus" to (aiResponse?.safetyStatus?.apiValue ?: "approved"),
            "disclosure" to (aiResponse?.disclosure ?: "Family member message"),
            "visibleToUids" to visibleTo,
            "createdAt" to FieldValue.serverTimestamp()
        )
        aiResponse?.escalationType?.let { payload["escalationType"] = it.take(80) }
        db.collection("families").document(familyId)
            .collection("aiSessions").document(sessionId)
            .collection("turns").document(turnId)
            .set(payload).await()
        turnId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/aiSessions/$sessionId/turns")
    }

    // --- Validated Cross-Device Media ---
    suspend fun uploadMedia(
        familyId: String,
        validated: MediaValidationResult.Valid,
        sharingScope: String,
        visibleToUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val mediaId = "med_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val payload = mapOf(
            "familyId" to familyId,
            "ownerUid" to uid,
            "mimeType" to validated.mimeType,
            "byteSize" to validated.byteSize,
            "scanStatus" to validated.scanStatus,
            "sharingScope" to sharingScope,
            "visibleToUids" to (visibleToUids + uid).distinct().take(20),
            "payloadBase64" to validated.payloadBase64,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("families").document(familyId).collection("media").document(mediaId).set(payload).await()
        mediaId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/media")
    }

    suspend fun fetchMedia(familyId: String, mediaId: String): Result<FamilyMedia?> = runCatching {
        requireUserId()
        val snap = db.collection("families").document(familyId).collection("media").document(mediaId).get().await()
        if (snap.exists()) {
            snap.toObject(FamilyMedia::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else null
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "families/$familyId/media/$mediaId")
    }

    // --- Family Notification Inbox ---
    fun observeNotifications(familyId: String): Flow<List<FamilyNotification>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("notifications")
        emitAll(
            col.whereArrayContains("recipientUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FamilyNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createNotification(
        familyId: String,
        title: String,
        body: String,
        type: String,
        recipientUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val notifId = "not_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val recipients = recipientUids.ifEmpty { listOf(uid) }.distinct().take(20)
        db.collection("families").document(familyId).collection("notifications").document(notifId).set(
            mapOf(
                "familyId" to familyId,
                "title" to title.trim().take(120),
                "body" to body.trim().take(500),
                "type" to type,
                "recipientUids" to recipients,
                "readByUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        notifId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/notifications")
    }

    suspend fun markNotificationRead(familyId: String, notificationId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("families").document(familyId).collection("notifications").document(notificationId)
            .update("readByUids", FieldValue.arrayUnion(uid)).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/notifications/$notificationId")
    }
}
