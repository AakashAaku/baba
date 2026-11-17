package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.SecurityTokenHelper
import com.example.core.handleFirestoreError
import com.example.domain.model.AgeBand
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyRole
import com.example.domain.model.FamilyWorkspace
import com.example.domain.model.Invitation
import com.example.domain.model.UserAccount
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

class FamilyRepository(
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
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeUserAccount(uid: String): Flow<UserAccount?> {
        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun ensureUserAccount(displayName: String, locale: String = "en"): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val payload = mapOf(
                "uid" to uid,
                "displayName" to displayName.ifBlank { "Family Member" }.take(80),
                "locale" to locale,
                "activeFamilyId" to "",
                "quietHoursEnabled" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updateUserPreferences(
        displayName: String? = null,
        locale: String? = null,
        activeFamilyId: String? = null,
        quietHoursEnabled: Boolean? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.let { updates["displayName"] = it.take(80) }
        locale?.let { updates["locale"] = it.take(20) }
        activeFamilyId?.let { updates["activeFamilyId"] = it.take(128) }
        quietHoursEnabled?.let { updates["quietHoursEnabled"] = it }
        docRef.update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    fun observeFamiliesForUser(): Flow<List<FamilyWorkspace>> = flow {
        val uid = requireUserId()
        val query = db.collection("families").whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(FamilyWorkspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "families")
                    }
                    throw error
                }
        )
    }

    fun observeFamilyMembers(familyId: String): Flow<List<FamilyMember>> = flow {
        val uid = requireUserId()
        val path = "families/$familyId/members"
        emitAll(
            db.collection("families").document(familyId).collection("members")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(FamilyMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createFamilyWorkspace(
        familyName: String,
        creatorDisplayName: String,
        creatorRole: FamilyRole
    ): Result<String> = runCatching {
        require(creatorRole == FamilyRole.FATHER || creatorRole == FamilyRole.WIFE) {
            "Only an adult (Father or Wife) can create a family workspace."
        }
        val uid = requireUserId()
        val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val familyRef = db.collection("families").document(familyId)
        val memberRef = familyRef.collection("members").document(uid)
        val userRef = db.collection("users").document(uid)

        val familyPayload = mapOf(
            "name" to familyName.trim().take(80),
            "createdBy" to uid,
            "status" to "active",
            "planId" to "family_plus",
            "memberUids" to listOf(uid),
            "adultUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val memberPayload = mapOf(
            "familyId" to familyId,
            "uid" to uid,
            "displayName" to creatorDisplayName.ifBlank { creatorRole.label }.take(80),
            "role" to creatorRole.apiValue,
            "ageBand" to AgeBand.ADULT.apiValue,
            "guardianUid" to "",
            "consentState" to "granted",
            "membershipStatus" to "active",
            "memberUids" to listOf(uid),
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(familyRef, familyPayload)
        batch.set(memberRef, memberPayload)
        batch.commit().await()

        // Update user's activeFamilyId after workspace creation
        val userSnap = userRef.get().await()
        if (userSnap.exists()) {
            userRef.update(
                mapOf(
                    "activeFamilyId" to familyId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            userRef.set(
                mapOf(
                    "uid" to uid,
                    "displayName" to creatorDisplayName.ifBlank { creatorRole.label }.take(80),
                    "locale" to "en",
                    "activeFamilyId" to familyId,
                    "quietHoursEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        familyId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families")
    }

    suspend fun addManagedChildProfile(
        familyId: String,
        childName: String,
        ageBand: AgeBand,
        memberUids: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(ageBand != AgeBand.ADULT) { "Child profile requires a minor age band." }
        val childId = "child_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val memberRef = db.collection("families").document(familyId).collection("members").document(childId)
        val payload = mapOf(
            "familyId" to familyId,
            "uid" to childId,
            "displayName" to childName.trim().take(80),
            "role" to FamilyRole.CHILD.apiValue,
            "ageBand" to ageBand.apiValue,
            "guardianUid" to uid,
            "consentState" to "granted",
            "membershipStatus" to "active",
            "memberUids" to memberUids.distinct().take(20),
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        memberRef.set(payload).await()
        childId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "families/$familyId/members")
    }

    fun observeInvitationsForUser(familyId: String?): Flow<List<Invitation>> = flow {
        val uid = requireUserId()
        val email = auth.currentUser?.email?.lowercase().orEmpty()
        val createdFlow = db.collection("invitations")
            .whereEqualTo("createdBy", uid)
            .snapshots()
            .map { it.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

        if (email.isNotBlank() && auth.currentUser?.isEmailVerified == true) {
            val receivedFlow = db.collection("invitations")
                .whereEqualTo("invitedEmail", email)
                .snapshots()
                .map { it.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            emitAll(
                combine(createdFlow, receivedFlow) { created, received ->
                    (created + received)
                        .distinctBy { it.id }
                        .filter { familyId == null || it.familyId == familyId || it.invitedEmail == email }
                        .sortedByDescending { it.expiresAtMillis }
                }.catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "invitations")
                    }
                    throw error
                }
            )
        } else {
            emitAll(
                createdFlow.map { list ->
                    list.filter { familyId == null || it.familyId == familyId }
                }.catch { error ->
                    val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "invitations")
                    }
                    throw error
                }
            )
        }
    }

    suspend fun createInvitation(
        familyId: String,
        familyName: String,
        invitedEmail: String,
        role: FamilyRole,
        ageBand: AgeBand,
        ttlHours: Long = 72L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val inviteId = "inv_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val rawSecretToken = UUID.randomUUID().toString()
        val tokenHash = SecurityTokenHelper.sha256Hex(rawSecretToken)
        val expiresAtMillis = System.currentTimeMillis() + ttlHours * 3_600_000L
        val payload = mapOf(
            "familyId" to familyId,
            "familyName" to familyName.take(80),
            "invitedEmail" to invitedEmail.trim().lowercase().take(160),
            "role" to role.apiValue,
            "ageBand" to ageBand.apiValue,
            "createdBy" to uid,
            "tokenHash" to tokenHash,
            "status" to "pending",
            "expiresAtMillis" to expiresAtMillis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("invitations").document(inviteId).set(payload).await()
        inviteId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "invitations")
    }

    suspend fun revokeInvitation(inviteId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("invitations").document(inviteId).update(
            mapOf(
                "status" to "revoked",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "invitations/$inviteId")
    }

    suspend fun acceptInvitation(invitation: Invitation, displayName: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(invitation.status == "pending") { "Invitation is no longer active." }
        require(invitation.expiresAtMillis > System.currentTimeMillis()) { "Invitation has expired." }

        val familyRef = db.collection("families").document(invitation.familyId)
        val inviteRef = db.collection("invitations").document(invitation.id)
        val memberRef = familyRef.collection("members").document(uid)
        val familySnap = familyRef.get().await()
        val existingMembers = (familySnap.get("memberUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val existingAdults = (familySnap.get("adultUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val updatedMembers = (existingMembers + uid).distinct().take(20)
        val updatedAdults = if (invitation.role == FamilyRole.CHILD.apiValue) {
            existingAdults
        } else {
            (existingAdults + uid).distinct().take(6)
        }

        val batch = db.batch()
        batch.update(
            inviteRef,
            mapOf(
                "status" to "accepted",
                "acceptedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            familyRef,
            mapOf(
                "memberUids" to updatedMembers,
                "adultUids" to updatedAdults,
                "lastAcceptedInviteId" to invitation.id,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            memberRef,
            mapOf(
                "familyId" to invitation.familyId,
                "uid" to uid,
                "displayName" to displayName.ifBlank { "Family Member" }.take(80),
                "role" to invitation.role,
                "ageBand" to invitation.ageBand,
                "guardianUid" to if (invitation.role == FamilyRole.CHILD.apiValue) invitation.createdBy else "",
                "consentState" to "granted",
                "membershipStatus" to "active",
                "memberUids" to updatedMembers,
                "joinedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        updateUserPreferences(activeFamilyId = invitation.familyId)
        invitation.familyId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "invitations/${invitation.id}")
    }

    suspend fun updateFamilyPlan(familyId: String, planId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId).update(
            mapOf(
                "planId" to planId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId")
    }

    suspend fun updateFamilyMemberControl(
        familyId: String,
        memberUid: String,
        consentState: String,
        membershipStatus: String
    ): Result<Unit> = runCatching {
        requireUserId()
        db.collection("families").document(familyId)
            .collection("members").document(memberUid)
            .update(
                mapOf(
                    "consentState" to consentState,
                    "membershipStatus" to membershipStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "families/$familyId/members/$memberUid")
    }
}
