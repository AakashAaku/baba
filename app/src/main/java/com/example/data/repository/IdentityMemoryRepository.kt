package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.AiIdentity
import com.example.domain.model.FamilyGuidance
import com.example.domain.model.FamilyMemory
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class IdentityMemoryRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing identity & memory.")

    fun observeDigitalBabaIdentities(familyId: String): Flow<List<AiIdentity>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("aiIdentities")
        val ownFlow = col.whereEqualTo("ownerUid", uid).snapshots()
            .map { it.toObjects(AiIdentity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val approvedFlow = col.whereArrayContains("memberUids", uid)
            .whereEqualTo("status", "approved")
            .snapshots()
            .map { it.toObjects(AiIdentity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(ownFlow, approvedFlow) { own, approved ->
                (own + approved).distinctBy { it.id }.sortedByDescending { it.activeVersion }
            }.catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                }
                throw error
            }
        )
    }

    suspend fun saveDigitalBabaIdentity(
        familyId: String,
        identityId: String? = null,
        communicationStyle: String,
        values: String,
        interests: String,
        bioSummary: String,
        memberUids: List<String>,
        status: String = "draft",
        nextVersion: Long = 1L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = identityId ?: "digital_baba_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val docRef = db.collection("families").document(familyId).collection("aiIdentities").document(id)
        if (identityId == null) {
            docRef.set(
                mapOf(
                    "familyId" to familyId,
                    "ownerUid" to uid,
                    "type" to "DIGITAL_BABA",
                    "activeVersion" to nextVersion,
                    "status" to status,
                    "communicationStyle" to communicationStyle.trim().take(500),
                    "values" to values.trim().take(800),
                    "interests" to interests.trim().take(600),
                    "bioSummary" to bioSummary.trim().take(1500),
                    "voiceCloningEnabled" to false,
                    "memberUids" to memberUids.distinct().take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "activeVersion" to nextVersion,
                    "status" to status,
                    "communicationStyle" to communicationStyle.trim().take(500),
                    "values" to values.trim().take(800),
                    "interests" to interests.trim().take(600),
                    "bioSummary" to bioSummary.trim().take(1500),
                    "memberUids" to memberUids.distinct().take(20),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "families/$familyId/aiIdentities")
    }

    suspend fun updateIdentityStatus(familyId: String, identityId: String, newStatus: String, newVersion: Long): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).collection("aiIdentities").document(identityId)
            .update(
                mapOf(
                    "status" to newStatus,
                    "activeVersion" to newVersion,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/aiIdentities/$identityId")
    }

    fun observePermittedMemories(familyId: String): Flow<List<FamilyMemory>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("memories")
        val ownFlow = col.whereEqualTo("ownerUid", uid).snapshots()
            .map { it.toObjects(FamilyMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        val sharedFlow = col.whereArrayContains("readableByUids", uid)
            .whereEqualTo("reviewStatus", "approved")
            .snapshots()
            .map { it.toObjects(FamilyMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(ownFlow, sharedFlow) { own, shared ->
                (own + shared).distinctBy { it.id }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }.catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                }
                throw error
            }
        )
    }

    suspend fun createMemory(
        familyId: String,
        memoryClass: String,
        title: String,
        content: String,
        sourceType: String,
        provenanceNote: String,
        sharingScope: String,
        allowedMemberIds: List<String>,
        memberUids: List<String>,
        reviewStatus: String = "approved",
        mediaId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val memoryId = "mem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val readableByUids = when (sharingScope) {
            "private" -> listOf(uid)
            "selected_members" -> (allowedMemberIds + uid).distinct().take(20)
            else -> (memberUids + uid).distinct().take(20)
        }
        val payload = mutableMapOf<String, Any>(
            "familyId" to familyId,
            "ownerUid" to uid,
            "memoryClass" to memoryClass,
            "title" to title.trim().take(120),
            "content" to content.trim().take(4000),
            "sourceType" to sourceType,
            "provenanceNote" to provenanceNote.trim().take(300),
            "sharingScope" to sharingScope,
            "allowedMemberIds" to allowedMemberIds.distinct().take(20),
            "readableByUids" to readableByUids,
            "reviewStatus" to reviewStatus,
            "ownershipConfirmed" to true,
            "memberUids" to memberUids.distinct().take(20),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (mediaId.isNotBlank()) {
            payload["mediaId"] = mediaId.take(128)
        }
        db.collection("families").document(familyId).collection("memories").document(memoryId).set(payload).await()
        memoryId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/memories")
    }

    suspend fun updateMemoryReviewStatus(
        familyId: String,
        memoryId: String,
        reviewStatus: String
    ): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).collection("memories").document(memoryId)
            .update(
                mapOf(
                    "reviewStatus" to reviewStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/memories/$memoryId")
    }

    suspend fun deleteMemory(familyId: String, memoryId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).collection("memories").document(memoryId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "families/$familyId/memories/$memoryId")
    }

    fun observeGuidance(familyId: String, isAdult: Boolean): Flow<List<FamilyGuidance>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("guidance")
        val memberFlow = col.whereArrayContains("readableByUids", uid).snapshots()
            .map { it.toObjects(FamilyGuidance::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        if (isAdult) {
            val adultFlow = col.whereArrayContains("adultUids", uid).snapshots()
                .map { it.toObjects(FamilyGuidance::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            emitAll(
                combine(memberFlow, adultFlow) { a, b ->
                    (a + b).distinctBy { it.id }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }.catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
            )
        } else {
            emitAll(
                memberFlow.map { list ->
                    list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                }.catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, col.path)
                    }
                    throw error
                }
            )
        }
    }

    suspend fun requestLiveGuidance(
        familyId: String,
        topic: String,
        question: String,
        memberUids: List<String>,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "guid_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val readableBy = (adultUids + uid).distinct().take(20)
        db.collection("families").document(familyId).collection("guidance").document(id).set(
            mapOf(
                "familyId" to familyId,
                "authorUid" to "",
                "askedByUid" to uid,
                "topic" to topic.trim().take(120),
                "question" to question.trim().take(1000),
                "answerText" to "",
                "audience" to "family",
                "readableByUids" to readableBy,
                "status" to "requested",
                "version" to 1L,
                "reviewAfterLabel" to "90 days",
                "memberUids" to memberUids.distinct().take(20),
                "adultUids" to adultUids.distinct().take(6),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/guidance")
    }

    suspend fun approveGuidance(
        familyId: String,
        guidanceId: String?,
        topic: String,
        question: String,
        answerText: String,
        audience: String,
        readableByUids: List<String>,
        memberUids: List<String>,
        adultUids: List<String>,
        nextVersion: Long = 1L,
        reviewAfterLabel: String = "90 days"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = guidanceId ?: "guid_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("families").document(familyId).collection("guidance").document(id)
        val finalReaders = (readableByUids + uid).distinct().take(20)
        if (guidanceId == null) {
            docRef.set(
                mapOf(
                    "familyId" to familyId,
                    "authorUid" to uid,
                    "askedByUid" to uid,
                    "topic" to topic.trim().take(120),
                    "question" to question.trim().take(1000),
                    "answerText" to answerText.trim().take(3000),
                    "audience" to audience,
                    "readableByUids" to finalReaders,
                    "status" to "approved",
                    "version" to nextVersion,
                    "reviewAfterLabel" to reviewAfterLabel.take(60),
                    "memberUids" to memberUids.distinct().take(20),
                    "adultUids" to adultUids.distinct().take(6),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "authorUid" to uid,
                    "answerText" to answerText.trim().take(3000),
                    "audience" to audience,
                    "readableByUids" to finalReaders,
                    "status" to "approved",
                    "version" to nextVersion,
                    "reviewAfterLabel" to reviewAfterLabel.take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "families/$familyId/guidance")
    }
}
