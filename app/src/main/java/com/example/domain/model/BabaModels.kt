package com.example.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class IdentityMode(val apiValue: String, val label: String, val disclosureText: String) {
    DIGITAL_BABA(
        apiValue = "DIGITAL_BABA",
        label = "Digital Baba (AI)",
        disclosureText = "AI version of your father, based on information he approved."
    ),
    BABA_COMPANION(
        apiValue = "BABA_COMPANION",
        label = "BABA Companion (AI)",
        disclosureText = "I am BABA, an AI companion."
    ),
    REAL_BABA(
        apiValue = "REAL_BABA",
        label = "Real Baba (Human)",
        disclosureText = "Verified human-authored message."
    );

    companion object {
        fun fromApi(value: String): IdentityMode =
            entries.firstOrNull { it.apiValue == value } ?: BABA_COMPANION
    }
}

enum class SourceType(val apiValue: String, val label: String) {
    APPROVED_GUIDANCE("approved_guidance", "Approved Guidance"),
    MEMORY("memory", "Approved Family Memory"),
    GENERAL_AI("general_ai", "General AI Support"),
    HUMAN_MESSAGE("human_message", "Human-Authored");

    companion object {
        fun fromApi(value: String): SourceType =
            entries.firstOrNull { it.apiValue == value } ?: GENERAL_AI
    }
}

enum class SafetyStatus(val apiValue: String) {
    APPROVED("approved"),
    BLOCKED("blocked"),
    NEEDS_REVIEW("needs_review");

    companion object {
        fun fromApi(value: String): SafetyStatus =
            entries.firstOrNull { it.apiValue == value } ?: NEEDS_REVIEW
    }
}

enum class FamilyRole(val apiValue: String, val label: String) {
    FATHER("father", "Father"),
    WIFE("wife", "Wife"),
    CHILD("child", "Child");

    companion object {
        fun fromApi(value: String): FamilyRole =
            entries.firstOrNull { it.apiValue == value } ?: CHILD
    }
}

enum class AgeBand(val apiValue: String, val label: String) {
    ADULT("adult", "Adult"),
    AGE_6_8("6-8", "Ages 6–8"),
    AGE_9_12("9-12", "Ages 9–12"),
    AGE_13_15("13-15", "Ages 13–15"),
    AGE_16_17("16-17", "Ages 16–17");

    companion object {
        fun fromApi(value: String): AgeBand =
            entries.firstOrNull { it.apiValue == value } ?: ADULT
    }
}

enum class AppSurface(val routeKey: String, val title: String) {
    FATHER("father", "Father"),
    WIFE("wife", "Wife"),
    KIDS("kids", "Kids"),
    FAMILY_HUB("family_hub", "Family Hub"),
    GOVERNANCE("governance", "Safety & Admin")
}

data class BabaAiResponse(
    val responseText: String,
    val identityMode: IdentityMode,
    val sourceType: SourceType,
    val sourceReferences: List<String>,
    val requiresHumanInput: Boolean,
    val escalationType: String?,
    val safetyStatus: SafetyStatus,
    val disclosure: String,
    val agentUsed: String
)

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val locale: String = "en",
    val activeFamilyId: String = "",
    val quietHoursEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrivateConversation(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val title: String = "",
    val agentMode: String = "BABA_COMPANION",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PrivateMessage(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val senderType: String = "user",
    val text: String = "",
    val identityMode: String = "BABA_COMPANION",
    val disclosure: String = IdentityMode.BABA_COMPANION.disclosureText,
    val safetyStatus: String = "approved",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyWorkspace(
    @DocumentId val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val status: String = "active",
    val planId: String = "free",
    val memberUids: List<String> = emptyList(),
    val adultUids: List<String> = emptyList(),
    val lastAcceptedInviteId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMember(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val uid: String = "",
    val displayName: String = "",
    val role: String = "father",
    val ageBand: String = "adult",
    val guardianUid: String = "",
    val consentState: String = "granted",
    val membershipStatus: String = "active",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Invitation(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val invitedEmail: String = "",
    val role: String = "wife",
    val ageBand: String = "adult",
    val createdBy: String = "",
    val tokenHash: String = "",
    val status: String = "pending",
    val expiresAtMillis: Long = 0L,
    val acceptedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiIdentity(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val ownerUid: String = "",
    val type: String = "DIGITAL_BABA",
    val activeVersion: Long = 1L,
    val status: String = "draft",
    val communicationStyle: String = "",
    val values: String = "",
    val interests: String = "",
    val bioSummary: String = "",
    val voiceCloningEnabled: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMemory(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val ownerUid: String = "",
    val memoryClass: String = "shared_family",
    val title: String = "",
    val content: String = "",
    val sourceType: String = "text",
    val mediaId: String = "",
    val provenanceNote: String = "",
    val sharingScope: String = "family",
    val allowedMemberIds: List<String> = emptyList(),
    val readableByUids: List<String> = emptyList(),
    val reviewStatus: String = "draft",
    val ownershipConfirmed: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyGuidance(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val authorUid: String = "",
    val askedByUid: String = "",
    val topic: String = "",
    val question: String = "",
    val answerText: String = "",
    val audience: String = "family",
    val readableByUids: List<String> = emptyList(),
    val status: String = "requested",
    val version: Long = 1L,
    val reviewAfterLabel: String = "90 days",
    val memberUids: List<String> = emptyList(),
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMessage(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "father",
    val recipientScope: String = "family",
    val visibleToUids: List<String> = emptyList(),
    val messageKind: String = "text",
    val textContent: String = "",
    val audioDurationSec: Long = 0L,
    val localAudioPath: String = "",
    val mediaId: String = "",
    val isHumanAuthored: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyEvent(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val createdBy: String = "",
    val title: String = "",
    val description: String = "",
    val eventDateLabel: String = "",
    val category: String = "milestone",
    val childVisible: Boolean = true,
    val visibleToUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiSession(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val participantUid: String = "",
    val surface: String = "digital_baba",
    val agentMode: String = "DIGITAL_BABA",
    val guardianOversight: Boolean = false,
    val visibleToUids: List<String> = emptyList(),
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiTurn(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val participantUid: String = "",
    val senderType: String = "user",
    val text: String = "",
    val identityMode: String = "DIGITAL_BABA",
    val sourceType: String = "general_ai",
    val sourceReferences: List<String> = emptyList(),
    val requiresHumanInput: Boolean = false,
    val escalationType: String? = null,
    val safetyStatus: String = "approved",
    val disclosure: String = IdentityMode.DIGITAL_BABA.disclosureText,
    val visibleToUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConsentRecord(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val subjectUid: String = "",
    val guardianUid: String = "",
    val purpose: String = "",
    val status: String = "granted",
    val version: String = "v1.0",
    val visibleToUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SafetyEvent(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val triggeredByUid: String = "",
    val category: String = "high_risk_child",
    val severity: String = "medium",
    val status: String = "open",
    val summaryMetadata: String = "",
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DataRequest(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val requesterUid: String = "",
    val requestType: String = "export",
    val status: String = "submitted",
    val notes: String = "",
    val visibleToUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val actorUid: String = "",
    val action: String = "",
    val resourceType: String = "",
    val resourceId: String = "",
    val outcome: String = "allowed",
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyMedia(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val ownerUid: String = "",
    val mimeType: String = "audio/mp4",
    val byteSize: Long = 0L,
    val scanStatus: String = "validated",
    val sharingScope: String = "family",
    val visibleToUids: List<String> = emptyList(),
    val payloadBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FamilyNotification(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "real_baba_message",
    val recipientUids: List<String> = emptyList(),
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsageRecord(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val actorUid: String = "",
    val service: String = "ai_turn",
    val units: Long = 1L,
    val estimatedCostUsdCents: Long = 1L,
    val periodKey: String = "",
    val adultUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class PlanQuotaTier(
    val planId: String,
    val displayName: String,
    val monthlyPriceLabel: String,
    val monthlyAiUnitsLimit: Long,
    val digitalBabaEnabled: Boolean,
    val voiceMessagesEnabled: Boolean
) {
    FREE("free", "Free", "$0 / mo", 30L, false, false),
    FAMILY_STARTER("family_starter", "Family Starter", "$4.99 / mo", 150L, true, false),
    FAMILY_PLUS("family_plus", "Family Plus", "$9.99 / mo", 500L, true, true),
    FAMILY_PREMIUM("family_premium", "Family Premium", "$19.99 / mo", 2000L, true, true);

    companion object {
        fun fromPlanId(planId: String): PlanQuotaTier =
            entries.firstOrNull { it.planId == planId } ?: FAMILY_PLUS
    }
}
