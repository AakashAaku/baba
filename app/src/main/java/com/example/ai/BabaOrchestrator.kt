package com.example.ai

import com.example.domain.model.AgeBand
import com.example.domain.model.AiIdentity
import com.example.domain.model.BabaAiResponse
import com.example.domain.model.FamilyGuidance
import com.example.domain.model.FamilyMember
import com.example.domain.model.FamilyMemory
import com.example.domain.model.IdentityMode
import com.example.domain.model.SafetyStatus
import com.example.domain.model.SourceType

enum class OrchestratorTaskType {
    DIGITAL_BABA_CHAT,
    COMPANION_CHAT,
    KIDS_LEARNING,
    KIDS_STORYLAND
}

data class OrchestrationResult(
    val response: BabaAiResponse,
    val safetyCategory: String? = null,
    val safetySeverity: String = "low",
    val safetyMetadata: String? = null
)

class BabaOrchestrator(
    private val aiProvider: BabaAiProvider,
    private val memoryAgent: MemoryAgent = MemoryAgent(),
    private val safetyPolicyAgent: SafetyPolicyAgent = SafetyPolicyAgent()
) {
    suspend fun handleRequest(
        familyId: String,
        requester: FamilyMember,
        taskType: OrchestratorTaskType,
        userPrompt: String,
        digitalBabaIdentity: AiIdentity?,
        allMemories: List<FamilyMemory>,
        allGuidance: List<FamilyGuidance>,
        preferredLocale: String = "en"
    ): OrchestrationResult {
        val requestedMode = when (taskType) {
            OrchestratorTaskType.DIGITAL_BABA_CHAT -> IdentityMode.DIGITAL_BABA
            OrchestratorTaskType.COMPANION_CHAT,
            OrchestratorTaskType.KIDS_LEARNING,
            OrchestratorTaskType.KIDS_STORYLAND -> IdentityMode.BABA_COMPANION
        }

        // Step 1-2: Safety & Policy pre-check (auth/consent/identity approval/injection/child-safety/high-risk)
        val preCheck = safetyPolicyAgent.preCheck(
            prompt = userPrompt,
            requester = requester,
            requestedMode = requestedMode,
            digitalBabaIdentity = digitalBabaIdentity
        )

        if (!preCheck.allowed) {
            val blockedStatus = if (preCheck.safetyCategory != null) {
                SafetyStatus.BLOCKED
            } else {
                SafetyStatus.NEEDS_REVIEW
            }
            return OrchestrationResult(
                response = BabaAiResponse(
                    responseText = preCheck.blockReason ?: "Request could not be completed safely.",
                    identityMode = requestedMode,
                    sourceType = SourceType.GENERAL_AI,
                    sourceReferences = emptyList(),
                    requiresHumanInput = preCheck.escalationType == "live_guidance",
                    escalationType = preCheck.escalationType,
                    safetyStatus = blockedStatus,
                    disclosure = requestedMode.disclosureText,
                    agentUsed = "SafetyPolicyAgent"
                ),
                safetyCategory = preCheck.safetyCategory,
                safetySeverity = preCheck.severity,
                safetyMetadata = preCheck.safetyCategory?.let {
                    "Pre-check policy trigger: category=$it, role=${requester.role}, ageBand=${requester.ageBand}"
                }
            )
        }

        // Step 3: Memory Agent retrieves ONLY permitted, approved records
        val permittedContext = memoryAgent.retrievePermittedContext(
            familyId = familyId,
            requester = requester,
            query = userPrompt,
            allMemories = allMemories,
            allGuidance = allGuidance
        )

        // Step 4: Select specialist agent
        val ageBand = AgeBand.fromApi(requester.ageBand)
        val (agentName, systemPrompt) = when (taskType) {
            OrchestratorTaskType.DIGITAL_BABA_CHAT -> {
                "FatherIdentityAgent" to SpecialistAgentPrompts.buildFatherIdentityPrompt(
                    identity = requireNotNull(digitalBabaIdentity),
                    context = permittedContext,
                    preferredLocale = preferredLocale
                )
            }
            OrchestratorTaskType.COMPANION_CHAT -> {
                "FamilyCompanionAgent" to SpecialistAgentPrompts.buildFamilyCompanionPrompt(
                    requesterRole = requester.role,
                    preferredLocale = preferredLocale
                )
            }
            OrchestratorTaskType.KIDS_LEARNING -> {
                "ChildrenLearningAgent" to SpecialistAgentPrompts.buildChildrenLearningPrompt(
                    ageBand = ageBand,
                    preferredLocale = preferredLocale
                )
            }
            OrchestratorTaskType.KIDS_STORYLAND -> {
                "StorytellingAgent" to SpecialistAgentPrompts.buildStorytellingPrompt(
                    ageBand = ageBand,
                    context = permittedContext,
                    preferredLocale = preferredLocale
                )
            }
        }

        // Step 5: Generate structured response via provider abstraction
        val raw = aiProvider.generateStructuredResponse(
            systemInstruction = systemPrompt,
            userPrompt = userPrompt,
            requestedMode = requestedMode
        )

        // Step 6: Deterministic Safety & Policy post-validation
        val validated = safetyPolicyAgent.postCheck(
            prompt = userPrompt,
            raw = raw,
            requestedMode = requestedMode,
            requester = requester,
            permittedContext = permittedContext,
            agentName = agentName
        )

        return OrchestrationResult(response = validated)
    }
}
