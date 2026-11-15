import type { AiIdentityRecord, FamilyMemberRecord, IdentityMode, OrchestratorTaskType } from "./models.ts";
import type { PermittedContext, RawModelResponse } from "./policyEngine.ts";

export type AiModelGenerator = (params: {
  taskType: OrchestratorTaskType;
  requestedMode: IdentityMode;
  prompt: string;
  requester: FamilyMemberRecord;
  digitalBabaIdentity: AiIdentityRecord | null;
  context: PermittedContext;
}) => Promise<RawModelResponse>;

export function buildGroundedGeminiGenerator(apiKey = process.env.GEMINI_API_KEY): AiModelGenerator {
  return async ({ taskType, requestedMode, prompt, requester, digitalBabaIdentity, context }) => {
    if (apiKey && apiKey.trim().length > 0 && apiKey !== "MY_GEMINI_API_KEY") {
      try {
        const systemInstruction = buildSystemPrompt(taskType, requestedMode, requester, digitalBabaIdentity, context);
        const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`;
        const res = await fetch(url, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            systemInstruction: { parts: [{ text: systemInstruction }] },
            contents: [{ role: "user", parts: [{ text: prompt }] }],
            generationConfig: {
              temperature: 0.4,
              responseMimeType: "application/json",
            },
          }),
        });
        if (res.ok) {
          const json = (await res.json()) as {
            candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }>;
          };
          const rawText = json.candidates?.[0]?.content?.parts?.[0]?.text;
          if (rawText) {
            const parsed = JSON.parse(rawText) as Partial<RawModelResponse>;
            return {
              responseText: typeof parsed.responseText === "string" ? parsed.responseText : rawText,
              sourceType: parsed.sourceType ?? (context.guidance.length > 0 ? "approved_guidance" : "general_ai"),
              sourceReferences: Array.isArray(parsed.sourceReferences) ? parsed.sourceReferences : [],
              requiresHumanInput: Boolean(parsed.requiresHumanInput),
              escalationType: parsed.escalationType ?? null,
              safetyStatus: parsed.safetyStatus ?? "approved",
            };
          }
        }
      } catch {
        // Fall through to deterministic grounded synthesis when upstream API is unavailable
      }
    }

    if (requestedMode === "DIGITAL_BABA") {
      if (context.guidance.length > 0) {
        const top = context.guidance[0];
        return {
          responseText: `Based on Father's approved guidance (${top.topic}): "${top.answerText}"`,
          sourceType: "approved_guidance",
          sourceReferences: [top.id],
          requiresHumanInput: false,
          escalationType: null,
          safetyStatus: "approved",
        };
      }
      if (context.memories.length > 0) {
        const topMem = context.memories[0];
        return {
          responseText: `Reflecting on approved family memory "${topMem.title}": ${topMem.content}`,
          sourceType: "memory",
          sourceReferences: [topMem.id],
          requiresHumanInput: false,
          escalationType: null,
          safetyStatus: "approved",
        };
      }
      return {
        responseText:
          "I don't know your father's approved view on this yet, and I will not guess. Please send a Live Guidance request so he can respond directly.",
        sourceType: "general_ai",
        sourceReferences: [],
        requiresHumanInput: true,
        escalationType: "live_guidance",
        safetyStatus: "approved",
      };
    }

    if (taskType === "KIDS_STORYLAND" && context.memories.length > 0) {
      const mem = context.memories[0];
      return {
        responseText: `Once upon a gentle evening, inspired by your approved family memory "${mem.title}", our heroes practiced kindness and curiosity together under the stars.`,
        sourceType: "memory",
        sourceReferences: [mem.id],
        requiresHumanInput: false,
        escalationType: null,
        safetyStatus: "approved",
      };
    }

    if (taskType === "KIDS_LEARNING") {
      return {
        responseText: `Great question for ${requester.ageBand}! Let's break "${prompt.slice(0, 80)}" into small steps together—first, what clue do you already notice? Check your school notebook as well.`,
        sourceType: "general_ai",
        sourceReferences: [],
        requiresHumanInput: false,
        escalationType: null,
        safetyStatus: "approved",
      };
    }

    return {
      responseText: "Here is a calm, practical next step: let's break today's family priorities into one small action and leave space to connect directly with each other.",
      sourceType: "general_ai",
      sourceReferences: [],
      requiresHumanInput: false,
      escalationType: null,
      safetyStatus: "approved",
    };
  };
}

function buildSystemPrompt(
  taskType: OrchestratorTaskType,
  mode: IdentityMode,
  requester: FamilyMemberRecord,
  identity: AiIdentityRecord | null,
  context: PermittedContext
): string {
  const guidanceLines = context.guidance
    .map((g) => `- [${g.id}] ${g.topic}: ${g.answerText}`)
    .join("\n");
  const memoryLines = context.memories.map((m) => `- [${m.id}] ${m.title}: ${m.content}`).join("\n");
  return [
    `Mode: ${mode} | Task: ${taskType} | Requester role: ${requester.role} (${requester.ageBand})`,
    identity ? `Approved Identity Bio: ${identity.bioSummary} | Values: ${identity.values}` : "",
    "Return JSON: {responseText, sourceType, sourceReferences, requiresHumanInput, escalationType, safetyStatus}.",
    "Never invent Father guidance or cross family boundaries.",
    `Approved Guidance:\n${guidanceLines || "(none)"}`,
    `Approved Memories:\n${memoryLines || "(none)"}`,
  ]
    .filter(Boolean)
    .join("\n");
}
