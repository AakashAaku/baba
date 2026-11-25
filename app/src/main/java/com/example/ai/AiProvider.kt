package com.example.ai

import com.example.domain.model.IdentityMode
import com.example.domain.model.SafetyStatus
import com.example.domain.model.SourceType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class RawModelStructuredOutput(
    val responseText: String,
    val identityMode: IdentityMode,
    val sourceType: SourceType,
    val sourceReferences: List<String>,
    val requiresHumanInput: Boolean,
    val escalationType: String?,
    val safetyStatus: SafetyStatus
)

interface BabaAiProvider {
    suspend fun generateStructuredResponse(
        systemInstruction: String,
        userPrompt: String,
        requestedMode: IdentityMode
    ): RawModelStructuredOutput
}

class FirebaseBabaAiProvider : BabaAiProvider {

    private val responseSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "response_text" to Schema.string(
                    description = "Warm, clear, policy-compliant response text for the user"
                ),
                "identity_mode" to Schema.enumeration(
                    values = listOf("DIGITAL_BABA", "BABA_COMPANION", "REAL_BABA"),
                    description = "Must match the requested identity mode"
                ),
                "source_type" to Schema.enumeration(
                    values = listOf("approved_guidance", "memory", "general_ai", "human_message"),
                    description = "Primary source used to ground this answer"
                ),
                "source_references" to Schema.array(
                    items = Schema.string(),
                    description = "Exact IDs of approved memories or guidance records cited"
                ),
                "requires_human_input" to Schema.boolean(
                    description = "True if approved guidance is missing/conflicting or an adult should review"
                ),
                "escalation_type" to Schema.string(
                    description = "live_guidance, medical_legal_escalation, or none",
                    nullable = true
                ),
                "safety_status" to Schema.enumeration(
                    values = listOf("approved", "blocked", "needs_review"),
                    description = "Safety classification"
                )
            ),
            optionalProperties = listOf("escalation_type")
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@FirebaseBabaAiProvider.responseSchema
                temperature = 0.4f
            }
        )
    }

    override suspend fun generateStructuredResponse(
        systemInstruction: String,
        userPrompt: String,
        requestedMode: IdentityMode
    ): RawModelStructuredOutput {
        val combinedPrompt = buildString {
            appendLine("[SYSTEM POLICY & AGENT CONTEXT]")
            appendLine(systemInstruction)
            appendLine()
            appendLine("[USER REQUEST]")
            appendLine(userPrompt)
        }

        val response = try {
            withTimeout(60_000L) {
                model.generateContent(combinedPrompt)
            }
        } catch (e: CancellationException) {
            throw e
        }

        val jsonText = response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty response.")

        val obj = JSONObject(jsonText)
        val refsArray = obj.optJSONArray("source_references")
        val refs = if (refsArray != null) {
            (0 until refsArray.length()).mapNotNull { idx ->
                refsArray.optString(idx).takeIf { it.isNotBlank() }
            }
        } else {
            emptyList()
        }

        val rawEsc = obj.optString("escalation_type", "").takeIf {
            it.isNotBlank() && it != "null" && it != "none"
        }

        val parsedText = obj.optString("response_text")
            .ifBlank { obj.optString("text") }
            .ifBlank { jsonText }

        return RawModelStructuredOutput(
            responseText = parsedText,
            identityMode = IdentityMode.fromApi(obj.optString("identity_mode", requestedMode.apiValue)),
            sourceType = SourceType.fromApi(obj.optString("source_type", SourceType.GENERAL_AI.apiValue)),
            sourceReferences = refs,
            requiresHumanInput = obj.optBoolean("requires_human_input", false),
            escalationType = rawEsc,
            safetyStatus = SafetyStatus.fromApi(obj.optString("safety_status", SafetyStatus.APPROVED.apiValue))
        )
    }
}
