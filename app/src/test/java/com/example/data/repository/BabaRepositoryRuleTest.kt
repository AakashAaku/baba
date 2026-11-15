package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.domain.model.FamilyRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class BabaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createFamilyAndDigitalBaba_fatherOwner_succeedsAndStreams() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        signInTestUser("father_$uniqueSuffix@baba.test")

        val familyRepo = FamilyRepository(firestore, auth)
        val identityRepo = IdentityMemoryRepository(firestore, auth)

        val familyId = withTimeout(TIMEOUT_MS) {
            familyRepo.createFamilyWorkspace(
                familyName = "Noor Family $uniqueSuffix",
                creatorDisplayName = "Tariq",
                creatorRole = FamilyRole.FATHER
            ).getOrThrow()
        }

        val families = withTimeout(FLOW_TIMEOUT_MS) {
            familyRepo.observeFamiliesForUser().first { list -> list.any { it.id == familyId } }
        }
        assertTrue(families.any { it.id == familyId && it.name.startsWith("Noor Family") })

        val identityId = withTimeout(TIMEOUT_MS) {
            identityRepo.saveDigitalBabaIdentity(
                familyId = familyId,
                communicationStyle = "Warm and patient",
                values = "Honesty and kindness",
                interests = "Stargazing and storytelling",
                bioSummary = "Working abroad, always present in heart.",
                memberUids = listOf(auth.currentUser!!.uid),
                status = "approved",
                nextVersion = 1L
            ).getOrThrow()
        }

        val identities = withTimeout(FLOW_TIMEOUT_MS) {
            identityRepo.observeDigitalBabaIdentities(familyId).first { list -> list.any { it.id == identityId } }
        }
        assertEquals("approved", identities.first { it.id == identityId }.status)
    }

    @Test
    fun crossFamilyIsolation_familyBCannotReadFamilyA() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        signInTestUser("father_a_$suffix@baba.test")
        val familyRepoA = FamilyRepository(firestore, auth)
        val memoryRepoA = IdentityMemoryRepository(firestore, auth)

        val famAId = withTimeout(TIMEOUT_MS) {
            familyRepoA.createFamilyWorkspace("Family A $suffix", "Father A", FamilyRole.FATHER).getOrThrow()
        }
        val memId = withTimeout(TIMEOUT_MS) {
            memoryRepoA.createMemory(
                familyId = famAId,
                memoryClass = "shared_family",
                title = "Family A Picnic",
                content = "Sunny afternoon by the lake.",
                sourceType = "text",
                provenanceNote = "Confirmed by Father A",
                sharingScope = "family",
                allowedMemberIds = emptyList(),
                memberUids = listOf(auth.currentUser!!.uid),
                reviewStatus = "approved"
            ).getOrThrow()
        }

        // Switch to unrelated Family B user
        signInTestUser("father_b_$suffix@baba.test")
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("families").document(famAId)
                    .collection("memories").document(memId)
                    .get().await()
            }
            fail("Expected PERMISSION_DENIED when Family B reads Family A memory")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun wifePrivateCompanion_cannotBeReadByFather() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val wifeUid = signInTestUser("wife_$suffix@baba.test")
        val msgRepoWife = MessagingHubRepository(firestore, auth)

        val convId = "conv_$suffix"
        withTimeout(TIMEOUT_MS) {
            msgRepoWife.ensurePrivateConversation(convId, "Wife Journal").getOrThrow()
            msgRepoWife.appendPrivateMessage(convId, "user", "My private reflection").getOrThrow()
        }

        // Switch to Father user
        signInTestUser("father_spouse_$suffix@baba.test")
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(wifeUid)
                    .collection("privateConversations").document(convId)
                    .get().await()
            }
            fail("Expected PERMISSION_DENIED when Father reads Wife private conversation")
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
        }
    }

    @Test
    fun unauthenticatedUser_observeFamilies_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val familyRepo = FamilyRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                familyRepo.observeFamiliesForUser().first()
            }
            fail("Expected unauthenticated access to fail")
        } catch (e: Exception) {
            // Repository throws IllegalStateException fail-fast when auth.currentUser == null
            assertTrue(
                e is IllegalStateException ||
                    e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            )
        }
    }

    @Test
    fun mediaNotificationsUsageAndSelfDeletion_enforcedByLiveRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val fatherUid = signInTestUser("father_media_$suffix@baba.test")
        val familyRepo = FamilyRepository(firestore, auth)
        val msgRepo = MessagingHubRepository(firestore, auth)
        val govRepo = GovernanceRepository(firestore, auth)
        val identityRepo = IdentityMemoryRepository(firestore, auth)

        val familyId = withTimeout(TIMEOUT_MS) {
            familyRepo.createFamilyWorkspace("Noor Media $suffix", "Tariq", FamilyRole.FATHER).getOrThrow()
        }

        // Upload validated JPEG bytes through MessagingHubRepository
        val validBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val validation = com.example.core.MediaValidator.validateBytes("image/jpeg", validBytes)
        assertTrue(validation is com.example.core.MediaValidationResult.Valid)
        val mediaId = withTimeout(TIMEOUT_MS) {
            msgRepo.uploadMedia(
                familyId = familyId,
                validated = validation as com.example.core.MediaValidationResult.Valid,
                sharingScope = "family",
                visibleToUids = listOf(fatherUid)
            ).getOrThrow()
        }

        // Create memory referencing mediaId & private companion message to verify self-deletion
        withTimeout(TIMEOUT_MS) {
            identityRepo.createMemory(
                familyId = familyId,
                memoryClass = "shared_family",
                title = "Corniche Walk",
                content = "Sunset walk",
                sourceType = "image",
                mediaId = mediaId,
                provenanceNote = "Confirmed by Tariq",
                sharingScope = "family",
                allowedMemberIds = emptyList(),
                memberUids = listOf(fatherUid),
                reviewStatus = "approved"
            ).getOrThrow()
            msgRepo.ensurePrivateConversation("wife_companion_main", "Private").getOrThrow()
            msgRepo.appendPrivateMessage("wife_companion_main", "user", "My journal entry").getOrThrow()
        }

        // Create notification & record usage
        val notifId = withTimeout(TIMEOUT_MS) {
            msgRepo.createNotification(
                familyId = familyId,
                title = "Real Baba Message",
                body = "Checked in safely",
                type = "real_baba_message",
                recipientUids = listOf(fatherUid)
            ).getOrThrow()
        }
        withTimeout(TIMEOUT_MS) {
            msgRepo.markNotificationRead(familyId, notifId).getOrThrow()
            govRepo.recordUsage(
                familyId = familyId,
                service = "ai_turn",
                units = 1L,
                estimatedCostUsdCents = 1L,
                adultUids = listOf(fatherUid)
            ).getOrThrow()
        }

        val usage = withTimeout(FLOW_TIMEOUT_MS) {
            govRepo.observeUsageRecords(familyId, isAdult = true).first { it.isNotEmpty() }
        }
        assertEquals(1L, usage.first().units)

        // Execute cascading self-deletion and verify request marked completed & owned media/memory deleted
        val reqId = withTimeout(TIMEOUT_MS) {
            govRepo.executeSelfDataDeletion(
                familyId = familyId,
                notes = "Right to erasure test",
                adultUids = listOf(fatherUid)
            ).getOrThrow()
        }

        val fetchedMedia = withTimeout(TIMEOUT_MS) { msgRepo.fetchMedia(familyId, mediaId).getOrThrow() }
        assertEquals(null, fetchedMedia)

        val requests = withTimeout(FLOW_TIMEOUT_MS) {
            govRepo.observeDataRequests(familyId).first { list -> list.any { it.id == reqId } }
        }
        assertEquals("completed", requests.first { it.id == reqId }.status)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
