package com.example.ai

import com.example.domain.model.AgeBand
import com.example.domain.model.AiIdentity
import com.example.domain.model.FamilyGuidance
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemory
import com.example.domain.model.FamilyRole
import com.example.domain.model.IdentityMode
import com.example.domain.model.SafetyStatus
import com.example.domain.model.SourceType

/**
 * Logical Agent 5: Memory Agent
 * Filters memories & guidance strictly by familyId, ownership, sharingScope, allowedMemberIds,
 * reviewStatus == "approved", and consent BEFORE any context is ever passed to an LLM.
 * Vector/keyword relevance is NEVER authorization.
 */
class MemoryAgent {

    data class PermittedContext(
        val memories: List<FamilyMemory>,
        val guidance: List<FamilyGuidance>,
        val hasGuidanceConflict: Boolean
    )

    fun retrievePermittedContext(
        familyId: String,
        requester: FamilyMember,
        query: String,
        allMemories: List<FamilyMemory>,
        allGuidance: List<FamilyGuidance>
    ): PermittedContext {
        if (requester.consentState != "granted" || requester.membershipStatus != "active") {
            return PermittedContext(emptyList(), emptyList(), hasGuidanceConflict = false)
        }

        val permittedMemories = allMemories.filter { mem ->
            mem.familyId == familyId &&
                mem.ownershipConfirmed &&
                mem.reviewStatus == "approved" &&
                when (mem.sharingScope) {
                    "private" -> mem.ownerUid == requester.uid
                    "selected_members" -> mem.ownerUid == requester.uid || requester.uid in mem.allowedMemberIds
                    "family" -> requester.uid in mem.memberUids || requester.uid in mem.readableByUids
                    else -> false
                }
        }

        val permittedGuidance = allGuidance.filter { guid ->
            guid.familyId == familyId &&
                guid.status == "approved" &&
                (requester.uid in guid.readableByUids ||
                    (guid.audience == "family") ||
                    (guid.audience == "wife" && requester.role == FamilyRole.WIFE.apiValue) ||
                    (guid.audience == "child" && requester.role == FamilyRole.CHILD.apiValue))
        }

        val rankedMemories = permittedMemories
            .sortedByDescending { SemanticTextSimilarity.score(query, "${it.title} ${it.content}") }
            .take(5)

        val rankedGuidance = permittedGuidance
            .sortedByDescending { SemanticTextSimilarity.score(query, "${it.topic} ${it.question} ${it.answerText}") }
            .take(5)

        // Simple conflict detection: multiple approved guidance entries on overlapping topic with differing answers
        val topicGroups = rankedGuidance.groupBy { it.topic.trim().lowercase() }
        val conflict = topicGroups.values.any { group ->
            group.map { it.answerText.trim().lowercase() }.distinct().size > 1
        }

        return PermittedContext(
            memories = rankedMemories,
            guidance = rankedGuidance,
            hasGuidanceConflict = conflict
        )
    }
}

/**
 * Deterministic bilingual semantic similarity scorer combining token overlap and character 3-gram cosine similarity.
 * Used strictly AFTER authorization filtering in MemoryAgent; similarity NEVER grants access.
 */
object SemanticTextSimilarity {
    fun score(query: String, document: String): Double {
        val qClean = query.trim().lowercase()
        val dClean = document.trim().lowercase()
        if (qClean.isBlank() || dClean.isBlank()) return 0.0

        val qTokens = tokenize(qClean)
        val dTokens = tokenize(dClean)
        val tokenOverlap = if (qTokens.isEmpty()) 0.0 else {
            qTokens.count { qt -> dTokens.any { dt -> dt.contains(qt) || qt.contains(dt) } }.toDouble() / qTokens.size
        }

        val qGrams = charNGrams(qClean)
        val dGrams = charNGrams(dClean)
        val cosine = cosineSimilarity(qGrams, dGrams)

        return (0.65 * tokenOverlap) + (0.35 * cosine)
    }

    private fun tokenize(text: String): Set<String> =
        text.split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 2 }
            .toSet()

    private fun charNGrams(text: String, n: Int = 3): Map<String, Int> {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        if (compact.length < n) return mapOf(compact to 1)
        val counts = mutableMapOf<String, Int>()
        for (i in 0..(compact.length - n)) {
            val gram = compact.substring(i, i + n)
            counts[gram] = (counts[gram] ?: 0) + 1
        }
        return counts
    }

    private fun cosineSimilarity(a: Map<String, Int>, b: Map<String, Int>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for ((k, v) in a) {
            val av = v.toDouble()
            magA += av * av
            val bv = b[k]?.toDouble() ?: 0.0
            dot += av * bv
        }
        for (v in b.values) {
            val bv = v.toDouble()
            magB += bv * bv
        }
        val denom = kotlin.math.sqrt(magA) * kotlin.math.sqrt(magB)
        return if (denom == 0.0) 0.0 else dot / denom
    }
}

/**
 * Logical Agent 6: Safety & Policy Agent
 * Deterministic pre- and post-generation policy enforcement. Cannot be bypassed by prompts or model output.
 */
class SafetyPolicyAgent {

    data class PreCheckResult(
        val allowed: Boolean,
        val blockReason: String? = null,
        val safetyCategory: String? = null,
        val severity: String = "low",
        val escalationType: String? = null
    )

    private val injectionPatterns = listOf(
        "ignore previous instructions",
        "ignore all instructions",
        "system prompt",
        "other family",
        "another family",
        "wife private",
        "bypass rules",
        "reveal all families",
        "admin password"
    )

    private val childSecrecyPatterns = listOf(
        "don't tell mom",
        "dont tell mom",
        "don't tell dad",
        "dont tell dad",
        "don't tell my parents",
        "dont tell my parents",
        "keep this secret from",
        "hide this from mom",
        "hide this from dad",
        "promise not to tell",
        "mummy ko mat batana",
        "papa ko mat batana",
        "ammi ko mat batana",
        "abbu ko mat batana",
        "raaz rakhna",
        "मम्मी को मत बताना",
        "पापा को मत बताना",
        "बुबा लाई नभन्नु",
        "आमा लाई नभन्नु",
        "বাবাকে বলো না",
        "মাকে বলো না",
        "امی کو مت بتانا",
        "ابو کو مت بتانا"
    )

    private val piiPatterns = listOf(
        "home address",
        "my school is",
        "street address",
        "password is",
        "credit card",
        "passport number"
    )

    private val highRiskEmergencyPatterns = listOf(
        "hurt myself",
        "suicide",
        "kill myself",
        "abuse",
        "hitting me",
        "emergency",
        "911",
        "bleeding",
        "chest pain",
        "overdose",
        "आत्महत्या",
        "খুন",
        "আত্মহত্যা",
        "خودکشی"
    )

    private val professionalAdvicePatterns = listOf(
        "diagnose",
        "prescription",
        "dosage",
        "medicine should i take",
        "legal advice",
        "sue ",
        "court custody",
        "invest my savings",
        "financial advice"
    )

    fun preCheck(
        prompt: String,
        requester: FamilyMember,
        requestedMode: IdentityMode,
        digitalBabaIdentity: AiIdentity?
    ): PreCheckResult {
        val lower = prompt.lowercase()

        if (requester.consentState != "granted") {
            return PreCheckResult(
                allowed = false,
                blockReason = "Consent has not been granted or was revoked for this profile. Please update consent settings with an authorized adult.",
                safetyCategory = "pii_guard",
                severity = "medium"
            )
        }

        if (requestedMode == IdentityMode.DIGITAL_BABA) {
            if (digitalBabaIdentity == null || digitalBabaIdentity.status != "approved") {
                return PreCheckResult(
                    allowed = false,
                    blockReason = "Digital Baba is not currently approved or active (status: ${digitalBabaIdentity?.status ?: "not created"}). Only Father-approved versions can respond. You can still send a Real Baba message or use BABA Companion.",
                    safetyCategory = null,
                    severity = "low",
                    escalationType = "live_guidance"
                )
            }
        }

        if (injectionPatterns.any { lower.contains(it) }) {
            return PreCheckResult(
                allowed = false,
                blockReason = "I cannot access other families, private adult conversations, or system instructions. Every family workspace is strictly isolated.",
                safetyCategory = "prompt_injection",
                severity = "high",
                escalationType = "safety_escalation"
            )
        }

        if (highRiskEmergencyPatterns.any { lower.contains(it) }) {
            return PreCheckResult(
                allowed = false,
                blockReason = "This sounds urgent or high-risk. BABA is an AI companion and cannot provide emergency, medical, or crisis intervention. Please contact local emergency services or speak immediately with a trusted adult or qualified professional.",
                safetyCategory = "high_risk_child",
                severity = "high",
                escalationType = "safety_escalation"
            )
        }

        if (requester.role == FamilyRole.CHILD.apiValue) {
            if (childSecrecyPatterns.any { lower.contains(it) }) {
                return PreCheckResult(
                    allowed = false,
                    blockReason = "I am BABA, an AI — I cannot promise to keep secrets from your parents or trusted adults. If something feels uncomfortable or worrying, please talk with your Mom, Dad, or another trusted adult.",
                    safetyCategory = "child_secrecy",
                    severity = "medium",
                    escalationType = "safety_escalation"
                )
            }
            if (piiPatterns.any { lower.contains(it) }) {
                return PreCheckResult(
                    allowed = false,
                    blockReason = "For your safety, please do not share personal details like your home address, school name, or passwords here.",
                    safetyCategory = "pii_guard",
                    severity = "medium"
                )
            }
        }

        if (professionalAdvicePatterns.any { lower.contains(it) }) {
            return PreCheckResult(
                allowed = false,
                blockReason = "BABA cannot make medical, legal, or financial decisions or replace a qualified doctor, lawyer, or adviser. Here is a safe next step: please consult a qualified professional or an authorized family adult.",
                safetyCategory = "medical_legal_escalation",
                severity = "medium",
                escalationType = "professional_referral"
            )
        }

        return PreCheckResult(allowed = true)
    }

    fun postCheck(
        prompt: String,
        raw: RawModelStructuredOutput,
        requestedMode: IdentityMode,
        requester: FamilyMember,
        permittedContext: MemoryAgent.PermittedContext,
        agentName: String
    ): com.example.domain.model.BabaAiResponse {
        val lowerPrompt = prompt.lowercase()
        val lowerOutput = raw.responseText.lowercase()
        val permittedIds = (permittedContext.memories.map { it.id } + permittedContext.guidance.map { it.id }).toSet()
        val sanitizedRefs = raw.sourceReferences.filter { it in permittedIds }

        val impersonationLeak = lowerOutput.contains("i am your real father") ||
            lowerOutput.contains("i am really your dad") ||
            lowerOutput.contains("this is the real baba speaking")

        val askingIfRealFather = lowerPrompt.contains("are you really my dad") ||
            lowerPrompt.contains("are you my real father") ||
            lowerPrompt.contains("are you a human") ||
            lowerPrompt.contains("is this really baba")

        var finalText = raw.responseText
        var safetyStatus = raw.safetyStatus
        var requiresHumanInput = raw.requiresHumanInput
        var escalationType = raw.escalationType

        if (impersonationLeak) {
            finalText = "To be completely clear: ${requestedMode.disclosureText} I am an AI system, not the real human father. Let's send a Real Baba message if you want to hear directly from him."
            safetyStatus = SafetyStatus.NEEDS_REVIEW
        } else if (askingIfRealFather && !lowerOutput.contains("ai")) {
            finalText = "${requestedMode.disclosureText} I am an AI representation/companion, not the real human father. You can always send or listen to a Real Baba message to connect directly with him."
        }

        if (permittedContext.hasGuidanceConflict) {
            requiresHumanInput = true
            escalationType = "live_guidance"
            finalText = "$finalText\n\nNote: There are conflicting approved guidance entries on this topic, so I have flagged this for an authorized adult to review."
        }

        val decisionCue = lowerPrompt.contains("can i ") ||
            lowerPrompt.contains("may i ") ||
            lowerPrompt.contains("should i ") ||
            lowerPrompt.contains("permission") ||
            lowerPrompt.contains("allowed to") ||
            lowerPrompt.contains("opinion")

        if (requestedMode == IdentityMode.DIGITAL_BABA &&
            sanitizedRefs.isEmpty() &&
            permittedContext.guidance.isEmpty() &&
            decisionCue
        ) {
            requiresHumanInput = true
            escalationType = escalationType ?: "live_guidance"
            if (!finalText.lowercase().contains("don't know") && !finalText.lowercase().contains("do not know")) {
                finalText = "I don't know your father's approved view on this yet, and I will not guess. I can send a Live Guidance request so your father or an authorized adult can reply directly."
            }
        }

        if (requester.role == FamilyRole.CHILD.apiValue && agentName == "ChildrenLearningAgent") {
            if (!lowerOutput.contains("school") && !lowerOutput.contains("check")) {
                finalText = "$finalText\n\nTip: Remember to double-check with your school notes or teacher too!"
            }
        }

        return com.example.domain.model.BabaAiResponse(
            responseText = finalText,
            identityMode = requestedMode,
            sourceType = if (sanitizedRefs.isNotEmpty()) raw.sourceType else SourceType.GENERAL_AI,
            sourceReferences = sanitizedRefs,
            requiresHumanInput = requiresHumanInput,
            escalationType = escalationType,
            safetyStatus = safetyStatus,
            disclosure = requestedMode.disclosureText,
            agentUsed = agentName
        )
    }
}

/**
 * Logical Agents 1–4: Prompt builders for Father Identity, Family Companion, Children Learning, and Storytelling.
 */
object SpecialistAgentPrompts {

    private fun languageInstruction(preferredLocale: String): String {
        val label = when (preferredLocale.trim().lowercase()) {
            "ne" -> "Nepali (नेपाली)"
            "hi" -> "Hindi (हिन्दी)"
            "bn" -> "Bengali / Bangladeshi (বাংলা)"
            "ur" -> "Urdu / Pakistani (اردو)"
            "ar" -> "Arabic (العربية)"
            else -> "English"
        }
        return "RESPONSE LANGUAGE RULE: Reply warmly and clearly in $label (matching the user's selected family language, unless they explicitly write and ask in another language). Keep every safety rule, AI disclosure, and honesty requirement intact."
    }

    fun buildFatherIdentityPrompt(
        identity: AiIdentity,
        context: MemoryAgent.PermittedContext,
        preferredLocale: String = "en"
    ): String = buildString {
        appendLine("You are Logical Agent 1: Father Identity Agent (mode: DIGITAL_BABA).")
        appendLine("FAMILY CONTEXT: Supporting a family where the father/husband may live or work far away; foster genuine warmth and encourage real human calls/messages when needed.")
        appendLine(languageInstruction(preferredLocale))
        appendLine("CRITICAL RULES:")
        appendLine("1. You are an AI representation grounded ONLY in Father's approved profile, approved memories, and approved guidance below.")
        appendLine("2. NEVER pretend to be the real human father or imply this is a live conversation.")
        appendLine("3. NEVER invent Father's opinions, permissions, promises, or memories. If approved guidance does not answer the question, explicitly say you do not know Father's view, set requires_human_input=true and escalation_type='live_guidance'.")
        appendLine("4. Cite exact record IDs in source_references whenever you use an approved memory or guidance.")
        appendLine()
        appendLine("FATHER APPROVED IDENTITY (v${identity.activeVersion}):")
        appendLine("- Communication Style: ${identity.communicationStyle}")
        appendLine("- Core Values: ${identity.values}")
        appendLine("- Interests: ${identity.interests}")
        appendLine("- Bio Summary: ${identity.bioSummary}")
        appendLine()
        appendContextRecords(context)
    }

    fun buildFamilyCompanionPrompt(
        requesterRole: String,
        preferredLocale: String = "en"
    ): String = buildString {
        appendLine("You are Logical Agent 2: Family Companion Agent (mode: BABA_COMPANION).")
        appendLine("FAMILY CONTEXT: Supporting a long-distance family ($requesterRole) when the father/husband works far from home—offer practical organization, emotional encouragement, and healthy human connection.")
        appendLine(languageInstruction(preferredLocale))
        appendLine("CRITICAL RULES:")
        appendLine("1. You are BABA, an independent AI companion (NOT Digital Baba and NOT a human).")
        appendLine("2. Support the $requesterRole with practical family organization, warm everyday conversation, and encouragement to connect with real family members.")
        appendLine("3. Never impersonate the father, never diagnose or give medical/legal/financial advice, and never foster emotional dependency.")
        appendLine("4. Set identity_mode='BABA_COMPANION' and source_type='general_ai'.")
    }

    fun buildChildrenLearningPrompt(
        ageBand: AgeBand,
        preferredLocale: String = "en"
    ): String = buildString {
        appendLine("You are Logical Agent 3: Children Learning Agent (mode: BABA_COMPANION).")
        appendLine("Target Child Age Band: ${ageBand.label}.")
        appendLine(languageInstruction(preferredLocale))
        appendLine("CRITICAL RULES:")
        appendLine("1. Provide warm, age-appropriate explanations, practice questions, and reading/quiz support.")
        appendLine("2. Guide the child step-by-step instead of completing graded homework/assessments for them.")
        appendLine("3. Clearly express uncertainty where appropriate and encourage checking school materials or asking a parent/teacher.")
        appendLine("4. Never ask for personal identifiers (home address, school name, passwords). Never encourage secrets from parents.")
        appendLine("5. Set identity_mode='BABA_COMPANION' and source_type='general_ai'.")
    }

    fun buildStorytellingPrompt(
        ageBand: AgeBand,
        context: MemoryAgent.PermittedContext,
        preferredLocale: String = "en"
    ): String = buildString {
        appendLine("You are Logical Agent 4: Storytelling Agent (mode: BABA_COMPANION).")
        appendLine("Target Child Age Band: ${ageBand.label}.")
        appendLine(languageInstruction(preferredLocale))
        appendLine("CRITICAL RULES:")
        appendLine("1. Craft a gentle, age-appropriate bedtime or adventure story celebrating love across distance.")
        appendLine("2. You may weave in ONLY the approved family memories listed below, clearly blending safe fictionalization.")
        appendLine("3. Never reveal private memories or use unapproved likenesses.")
        appendLine("4. Cite any used memory IDs in source_references and set source_type='memory' if used, else 'general_ai'.")
        appendLine()
        appendContextRecords(context)
    }

    private fun StringBuilder.appendContextRecords(context: MemoryAgent.PermittedContext) {
        appendLine("PERMITTED APPROVED GUIDANCE:")
        if (context.guidance.isEmpty()) {
            appendLine("(none)")
        } else {
            context.guidance.forEach { g ->
                appendLine("- [ID: ${g.id}] Topic: ${g.topic} | Q: ${g.question} | Approved Answer: ${g.answerText}")
            }
        }
        appendLine()
        appendLine("PERMITTED APPROVED MEMORIES:")
        if (context.memories.isEmpty()) {
            appendLine("(none)")
        } else {
            context.memories.forEach { m ->
                appendLine("- [ID: ${m.id}] Title: ${m.title} | Content: ${m.content}")
            }
        }
    }
}
