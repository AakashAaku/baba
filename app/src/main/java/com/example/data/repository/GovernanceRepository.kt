package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.model.AuditLog
import com.example.domain.model.ConsentRecord
import com.example.domain.model.DataRequest
import com.example.domain.model.SafetyEvent
import com.example.domain.model.UsageRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

class GovernanceRepository(
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
            ?: throw IllegalStateException("User must be signed in before accessing governance records.")

    fun observeConsents(familyId: String): Flow<List<ConsentRecord>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("consents")
        emitAll(
            col.whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ConsentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun recordConsent(
        familyId: String,
        subjectUid: String,
        purpose: String,
        status: String,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "con_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val visibleTo = (adultUids + subjectUid + uid).distinct().take(20)
        db.collection("families").document(familyId).collection("consents").document(id).set(
            mapOf(
                "familyId" to familyId,
                "subjectUid" to subjectUid,
                "guardianUid" to uid,
                "purpose" to purpose.trim().take(160),
                "status" to status,
                "version" to "v1.0",
                "visibleToUids" to visibleTo,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/consents")
    }

    suspend fun updateConsentStatus(familyId: String, consentId: String, status: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).collection("consents").document(consentId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/consents/$consentId")
    }

    fun observeSafetyEvents(familyId: String): Flow<List<SafetyEvent>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("safetyEvents")
        emitAll(
            col.whereArrayContains("adultUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SafetyEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun logSafetyEvent(
        familyId: String,
        category: String,
        severity: String,
        summaryMetadata: String,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "saf_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("families").document(familyId).collection("safetyEvents").document(id).set(
            mapOf(
                "familyId" to familyId,
                "triggeredByUid" to uid,
                "category" to category,
                "severity" to severity,
                "status" to "open",
                "summaryMetadata" to summaryMetadata.trim().take(500),
                "adultUids" to adultUids.distinct().take(6),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/safetyEvents")
    }

    suspend fun updateSafetyEventStatus(familyId: String, eventId: String, status: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).collection("safetyEvents").document(eventId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/safetyEvents/$eventId")
    }

    fun observeDataRequests(familyId: String): Flow<List<DataRequest>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("dataRequests")
        emitAll(
            col.whereArrayContains("visibleToUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(DataRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun submitDataRequest(
        familyId: String,
        requestType: String,
        notes: String,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "req_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val visibleTo = (adultUids + uid).distinct().take(20)
        db.collection("families").document(familyId).collection("dataRequests").document(id).set(
            mapOf(
                "familyId" to familyId,
                "requesterUid" to uid,
                "requestType" to requestType,
                "status" to "submitted",
                "notes" to notes.trim().take(500),
                "visibleToUids" to visibleTo,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/dataRequests")
    }

    fun observeAuditLogs(familyId: String): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("auditLogs")
        emitAll(
            col.whereArrayContains("adultUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun appendAuditLog(
        familyId: String,
        action: String,
        resourceType: String,
        resourceId: String,
        outcome: String,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "aud_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("families").document(familyId).collection("auditLogs").document(id).set(
            mapOf(
                "familyId" to familyId,
                "actorUid" to uid,
                "action" to action.take(80),
                "resourceType" to resourceType.take(80),
                "resourceId" to resourceId.take(128),
                "outcome" to outcome,
                "adultUids" to adultUids.distinct().take(6),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/auditLogs")
    }

    fun currentPeriodKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun observeUsageRecords(familyId: String, isAdult: Boolean): Flow<List<UsageRecord>> = flow {
        val uid = requireUserId()
        val col = db.collection("families").document(familyId).collection("usageRecords")
        val query = if (isAdult) {
            col.whereArrayContains("adultUids", uid)
        } else {
            col.whereEqualTo("actorUid", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(UsageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun recordUsage(
        familyId: String,
        service: String,
        units: Long,
        estimatedCostUsdCents: Long,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val id = "use_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        db.collection("families").document(familyId).collection("usageRecords").document(id).set(
            mapOf(
                "familyId" to familyId,
                "actorUid" to uid,
                "service" to service,
                "units" to units.coerceIn(1L, 1000L),
                "estimatedCostUsdCents" to estimatedCostUsdCents.coerceIn(0L, 100_000L),
                "periodKey" to currentPeriodKey(),
                "adultUids" to adultUids.distinct().take(6),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/usageRecords")
    }

    suspend fun executeDataExport(
        context: Context,
        familyId: String,
        notes: String,
        adultUids: List<String>
    ): Result<Pair<String, File>> = runCatching {
        val uid = requireUserId()
        val requestId = submitDataRequest(
            familyId = familyId,
            requestType = "export",
            notes = notes,
            adultUids = adultUids
        ).getOrThrow()

        val rootJson = JSONObject().apply {
            put("exportedAtIso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            put("requesterUid", uid)
            put("familyId", familyId)
            put("dataRequestId", requestId)
        }

        val userDoc = db.collection("users").document(uid).get().await()
        if (userDoc.exists()) {
            rootJson.put("userAccount", JSONObject(userDoc.data ?: emptyMap<String, Any>()))
        }

        val privateMsgs = db.collection("users").document(uid)
            .collection("privateConversations").document("wife_companion_main")
            .collection("messages").get().await()
        rootJson.put("privateCompanionMessages", JSONArray().apply {
            privateMsgs.documents.forEach { d -> put(JSONObject(d.data ?: emptyMap<String, Any>()).put("id", d.id)) }
        })

        val famRef = db.collection("families").document(familyId)
        val ownMemories = famRef.collection("memories").whereEqualTo("ownerUid", uid).get().await()
        val readableMemories = famRef.collection("memories")
            .whereArrayContains("readableByUids", uid)
            .whereEqualTo("reviewStatus", "approved").get().await()
        val mergedMemories = (ownMemories.documents + readableMemories.documents).distinctBy { it.id }
        rootJson.put("memories", JSONArray().apply {
            mergedMemories.forEach { d -> put(JSONObject(d.data ?: emptyMap<String, Any>()).put("id", d.id)) }
        })

        val guidance = famRef.collection("guidance").whereArrayContains("readableByUids", uid).get().await()
        rootJson.put("guidance", JSONArray().apply {
            guidance.documents.forEach { d -> put(JSONObject(d.data ?: emptyMap<String, Any>()).put("id", d.id)) }
        })

        val familyMessages = famRef.collection("messages").whereArrayContains("visibleToUids", uid).get().await()
        rootJson.put("familyMessages", JSONArray().apply {
            familyMessages.documents.forEach { d -> put(JSONObject(d.data ?: emptyMap<String, Any>()).put("id", d.id)) }
        })

        val consents = famRef.collection("consents").whereArrayContains("visibleToUids", uid).get().await()
        rootJson.put("consents", JSONArray().apply {
            consents.documents.forEach { d -> put(JSONObject(d.data ?: emptyMap<String, Any>()).put("id", d.id)) }
        })

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "baba_export_${System.currentTimeMillis()}.json")
        exportFile.writeText(rootJson.toString(2), Charsets.UTF_8)

        famRef.collection("dataRequests").document(requestId).update(
            mapOf(
                "status" to "completed",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        appendAuditLog(
            familyId = familyId,
            action = "DATA_EXPORT_COMPLETED",
            resourceType = "dataRequests",
            resourceId = requestId,
            outcome = "allowed",
            adultUids = adultUids
        )

        requestId to exportFile
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "families/$familyId/dataRequests")
    }

    suspend fun executeSelfDataDeletion(
        familyId: String,
        notes: String,
        adultUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val requestId = submitDataRequest(
            familyId = familyId,
            requestType = "delete",
            notes = notes,
            adultUids = adultUids
        ).getOrThrow()

        // 1. Delete requester's owner-only private companion messages & conversation doc
        val privConvRef = db.collection("users").document(uid)
            .collection("privateConversations").document("wife_companion_main")
        val privMsgs = privConvRef.collection("messages").get().await()
        privMsgs.documents.forEach { it.reference.delete().await() }
        if (privConvRef.get().await().exists()) {
            privConvRef.delete().await()
        }

        val famRef = db.collection("families").document(familyId)
        // 2. Delete owned memories & owned media
        val ownMemories = famRef.collection("memories").whereEqualTo("ownerUid", uid).get().await()
        ownMemories.documents.forEach { it.reference.delete().await() }

        val ownMedia = famRef.collection("media").whereEqualTo("ownerUid", uid).get().await()
        ownMedia.documents.forEach { it.reference.delete().await() }

        // 3. Revoke requester's granted consents
        val consents = famRef.collection("consents").whereArrayContains("visibleToUids", uid).get().await()
        consents.documents.filter {
            it.getString("subjectUid") == uid && it.getString("status") == "granted"
        }.forEach { doc ->
            doc.reference.update(
                mapOf(
                    "status" to "revoked",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // 4. Mark request completed & log audit
        famRef.collection("dataRequests").document(requestId).update(
            mapOf(
                "status" to "completed",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        appendAuditLog(
            familyId = familyId,
            action = "DATA_DELETE_COMPLETED",
            resourceType = "dataRequests",
            resourceId = requestId,
            outcome = "allowed",
            adultUids = adultUids
        )
        requestId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "families/$familyId")
    }
}
