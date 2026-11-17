package com.example.core

import com.example.ai.MemoryAgent
import com.example.ai.SemanticTextSimilarity
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaAndSecurityHelperTest {

    @Test
    fun `sha256Hex produces deterministic 64-char hex digest`() {
        val digestA = SecurityTokenHelper.sha256Hex("invite-token-123")
        val digestB = SecurityTokenHelper.sha256Hex("invite-token-123")
        val digestOther = SecurityTokenHelper.sha256Hex("invite-token-999")
        assertEquals(64, digestA.length)
        assertEquals(digestA, digestB)
        assertNotEquals(digestA, digestOther)
    }

    @Test
    fun `MediaValidator rejects executable scripts oversized payloads and unsupported MIME types`() {
        val exeBytes = "MZ\u0090\u0000pe-binary".toByteArray(Charsets.UTF_8)
        assertTrue(MediaValidator.validateBytes("image/jpeg", exeBytes) is MediaValidationResult.Invalid)

        val svgScript = "<svg onload='alert(1)'><script>evil()</script></svg>".toByteArray(Charsets.UTF_8)
        assertTrue(MediaValidator.validateBytes("image/png", svgScript) is MediaValidationResult.Invalid)

        val elfBytes = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 0x02)
        assertTrue(MediaValidator.validateBytes("audio/mp4", elfBytes) is MediaValidationResult.Invalid)

        val oversized = ByteArray(MediaValidator.MAX_MEDIA_BYTES + 10) { 0x01 }
        assertTrue(MediaValidator.validateBytes("image/jpeg", oversized) is MediaValidationResult.Invalid)

        val badMime = MediaValidator.validateBytes("application/pdf", byteArrayOf(1, 2, 3))
        assertTrue(badMime is MediaValidationResult.Invalid)

        val validJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val result = MediaValidator.validateBytes("image/jpeg", validJpeg)
        assertTrue(result is MediaValidationResult.Valid)
    }

    @Test
    fun `SemanticTextSimilarity ranks relevant memories higher while MemoryAgent still excludes unauthorized docs`() {
        val highScore = SemanticTextSimilarity.score(
            query = "astronomy constellations roof",
            document = "Evening Stargazing: We counted constellations on the roof together."
        )
        val lowScore = SemanticTextSimilarity.score(
            query = "astronomy constellations roof",
            document = "Grocery shopping list for onions and olive oil."
        )
        assertTrue("Expected relevant memory to outscore unrelated memory ($highScore vs $lowScore)", highScore > lowScore)

        // Arabic similarity check
        val arabicScore = SemanticTextSimilarity.score(
            query = "قصص النجوم والقمر",
            document = "حكاية مسائية عن النجوم والقمر مع العائلة"
        )
        assertTrue("Expected positive Arabic semantic similarity score, got $arabicScore", arabicScore > 0.2)

        // Ensure unauthorized memory with 100% query match is STILL excluded before ranking
        val child = FamilyMember(
            uid = "child_1",
            familyId = "fam_1",
            role = "child",
            consentState = "granted",
            membershipStatus = "active",
            memberUids = listOf("father_1", "child_1")
        )
        val secretMatch = FamilyMemory(
            id = "mem_secret_exact",
            familyId = "fam_1",
            ownerUid = "father_1",
            title = "astronomy constellations roof",
            content = "astronomy constellations roof PRIVATE_SECRET",
            sharingScope = "private",
            readableByUids = listOf("father_1"),
            reviewStatus = "approved",
            ownershipConfirmed = true,
            memberUids = listOf("father_1", "child_1")
        )
        val permitted = MemoryAgent().retrievePermittedContext(
            familyId = "fam_1",
            requester = child,
            query = "astronomy constellations roof",
            allMemories = listOf(secretMatch),
            allGuidance = emptyList()
        )
        assertFalse(permitted.memories.any { it.id == "mem_secret_exact" })
        assertTrue(permitted.memories.isEmpty())
    }

    @Test
    fun `AppLanguage and LocalePreferenceStore support all six family languages and timezones`() {
        assertEquals(AppLanguage.EN, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en-US"))
        assertEquals(AppLanguage.NE, AppLanguage.fromCode("ne-NP"))
        assertEquals(AppLanguage.HI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.BN, AppLanguage.fromCode("bn-BD"))
        assertEquals(AppLanguage.UR, AppLanguage.fromCode("ur-PK"))
        assertEquals(AppLanguage.AR, AppLanguage.fromCode("ar"))
        assertTrue(AppLanguage.UR.isRtl)
        assertTrue(AppLanguage.AR.isRtl)
        assertFalse(AppLanguage.NE.isRtl)
        assertFalse(AppLanguage.BN.isRtl)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        LocalePreferenceStore.saveLocale(context, "ne", markPreLoginPending = true)
        assertEquals("ne", LocalePreferenceStore.getSavedLocale(context))
        assertTrue(LocalePreferenceStore.hasPendingPreLoginSelection(context))
        LocalePreferenceStore.clearPendingPreLoginSelection(context)
        assertFalse(LocalePreferenceStore.hasPendingPreLoginSelection(context))

        LocalePreferenceStore.saveWorkTimezone(context, "Asia/Riyadh")
        LocalePreferenceStore.saveHomeTimezone(context, "Asia/Kathmandu")
        assertEquals("Asia/Riyadh", LocalePreferenceStore.getWorkTimezone(context))
        assertEquals("Asia/Kathmandu", LocalePreferenceStore.getHomeTimezone(context, "Asia/Dhaka"))

        val formatted = FamilyTimeZoneHelper.formatLocalTime("Asia/Kathmandu", "en", 1_735_689_600_000L)
        assertTrue(formatted.isNotBlank())
        assertEquals("Nepal (Kathmandu)", FamilyTimeZoneHelper.zoneDisplayName("Asia/Kathmandu"))
    }
}
