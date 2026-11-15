import crypto from "node:crypto";
import {
  IDENTITY_DISCLOSURES,
  type AiIdentityRecord,
  type FamilyMemberRecord,
  type GuidanceRecord,
  type IdentityMode,
  type MemoryRecord,
  type OrchestratorTaskType,
  type SafetyStatus,
  type SourceType,
} from "./models.ts";

export const MAX_MEDIA_BYTES = 420_000;
const ALLOWED_MIME_TYPES = new Set([
  "audio/mp4",
  "audio/m4a",
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export function sha256Hex(raw: string): string {
  return crypto.createHash("sha256").update(raw, "utf8").digest("hex");
}

export function verifyHashConstantTime(rawToken: string, expectedHashHex: string): boolean {
  if (!rawToken || !expectedHashHex) return false;
  const actualHex = sha256Hex(rawToken);
  const a = Buffer.from(actualHex, "utf8");
  const b = Buffer.from(expectedHashHex.toLowerCase(), "utf8");
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export interface MediaValidationResult {
  valid: boolean;
  mimeType: string;
  byteSize: number;
  reason?: string;
}

export function validateMediaPayload(mimeType: string, payloadBase64: string): MediaValidationResult {
  const normalized = mimeType.toLowerCase().trim();
  if (!ALLOWED_MIME_TYPES.has(normalized)) {
    return {
      valid: false,
      mimeType: normalized,
      byteSize: 0,
      reason: `Unsupported file type: ${mimeType}. Allowed: audio/mp4, image/jpeg, png, webp.`,
    };
  }
  if (!payloadBase64 || typeof payloadBase64 !== "string") {
    return { valid: false, mimeType: normalized, byteSize: 0, reason: "Media payload is empty." };
  }
  const bytes = Buffer.from(payloadBase64, "base64");
  if (bytes.length === 0) {
    return { valid: false, mimeType: normalized, byteSize: 0, reason: "Media file decoded to 0 bytes." };
  }
  if (bytes.length > MAX_MEDIA_BYTES) {
    return {
      valid: false,
      mimeType: normalized,
      byteSize: bytes.length,
      reason: `File exceeds safe limit (${Math.floor(bytes.length / 1024)} KB > ${Math.floor(MAX_MEDIA_BYTES / 1024)} KB).`,
    };
  }
  if (containsDisallowedExecutableSignature(bytes)) {
    return {
      valid: false,
      mimeType: normalized,
      byteSize: bytes.length,
      reason: "Security scan blocked executable or active script signature.",
    };
  }
  return { valid: true, mimeType: normalized, byteSize: bytes.length };
}

export function containsDisallowedExecutableSignature(bytes: Buffer): boolean {
  if (bytes.length >= 2 && bytes[0] === 0x4d && bytes[1] === 0x5a) return true; // MZ
  if (bytes.length >= 4 && bytes[0] === 0x7f && bytes[1] === 0x45 && bytes[2] === 0x4c && bytes[3] === 0x46) {
    return true; // ELF
  }
  if (bytes.length >= 4 && bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 0x03 && bytes[3] === 0x04) {
    return true; // PK\x03\x04
  }
  const headText = bytes.subarray(0, 256).toString("utf8").toLowerCase();
  return headText.includes("<script") || headText.includes("<svg") || headText.startsWith("#!");
}

export function semanticScore(query: string, document: string): number {
  const qClean = query.trim().toLowerCase();
  const dClean = document.trim().toLowerCase();
  if (!qClean || !dClean) return 0;

  const tokenize = (t: string): string[] =>
    t
      .split(/[^\p{L}\p{Nd}]+/u)
      .filter((w) => w.length >= 2);

  const qTokens = Array.from(new Set(tokenize(qClean)));
  const dTokens = Array.from(new Set(tokenize(dClean)));
  const tokenOverlap =
    qTokens.length === 0
      ? 0
      : qTokens.filter((qt) => dTokens.some((dt) => dt.includes(qt) || qt.includes(dt))).length / qTokens.length;

  const charNGrams = (t: string, n = 3): Map<string, number> => {
    const compact = t.replace(/\s+/g, " ").trim();
    const map = new Map<string, number>();
    if (compact.length < n) {
      map.set(compact, 1);
      return map;
    }
    for (let i = 0; i <= compact.length - n; i += 1) {
      const gram = compact.slice(i, i + n);
      map.set(gram, (map.get(gram) ?? 0) + 1);
    }
    return map;
  };

  const qGrams = charNGrams(qClean);
  const dGrams = charNGrams(dClean);
  let dot = 0;
  let magA = 0;
  let magB = 0;
  for (const [k, av] of qGrams.entries()) {
    magA += av * av;
    dot += av * (dGrams.get(k) ?? 0);
  }
  for (const bv of dGrams.values()) {
    magB += bv * bv;
  }
  const denom = Math.sqrt(magA) * Math.sqrt(magB);
  const cosine = denom === 0 ? 0 : dot / denom;
  return 0.65 * tokenOverlap + 0.35 * cosine;
}

export interface PermittedContext {
  memories: MemoryRecord[];
  guidance: GuidanceRecord[];
  hasGuidanceConflict: boolean;
}

export function retrievePermittedContext(
  familyId: string,
  requester: FamilyMemberRecord,
  query: string,
  allMemories: MemoryRecord[],
  allGuidance: GuidanceRecord[]
): PermittedContext {
  if (requester.consentState !== "granted" || requester.membershipStatus !== "active") {
    return { memories: [], guidance: [], hasGuidanceConflict: false };
  }

  const permittedMemories = allMemories.filter((mem) => {
    if (mem.familyId !== familyId || !mem.ownershipConfirmed || mem.reviewStatus !== "approved") {
      return false;
    }
    if (mem.sharingScope === "private") return mem.ownerUid === requester.uid;
    if (mem.sharingScope === "selected_members") {
      return mem.ownerUid === requester.uid || mem.allowedMemberIds.includes(requester.uid);
    }
    if (mem.sharingScope === "family") {
      return mem.readableByUids.includes(requester.uid);
    }
    return false;
  });

  const permittedGuidance = allGuidance.filter((g) => {
    if (g.familyId !== familyId || g.status !== "approved") return false;
    return (
      g.readableByUids.includes(requester.uid) ||
      g.audience === "family" ||
      (g.audience === "wife" && requester.role === "wife") ||
      (g.audience === "child" && requester.role === "child")
    );
  });

  const rankedMemories = [...permittedMemories]
    .sort((a, b) => semanticScore(query, `${b.title} ${b.content}`) - semanticScore(query, `${a.title} ${a.content}`))
    .slice(0, 5);

  const rankedGuidance = [...permittedGuidance]
    .sort(
      (a, b) =>
        semanticScore(query, `${b.topic} ${b.question} ${b.answerText}`) -
        semanticScore(query, `${a.topic} ${a.question} ${a.answerText}`)
    )
    .slice(0, 5);

  const byTopic = new Map<string, Set<string>>();
  for (const g of rankedGuidance) {
    const key = g.topic.trim().toLowerCase();
    const set = byTopic.get(key) ?? new Set<string>();
    set.add(g.answerText.trim().toLowerCase());
    byTopic.set(key, set);
  }
  const hasGuidanceConflict = Array.from(byTopic.values()).some((set) => set.size > 1);

  return { memories: rankedMemories, guidance: rankedGuidance, hasGuidanceConflict };
}

export interface SafetyPreCheckResult {
  allowed: boolean;
  blockReason?: string;
  safetyCategory?: string;
  severity: "low" | "medium" | "high";
  escalationType?: string;
}

const INJECTION_PATTERNS = [
  "ignore previous instructions",
  "ignore all instructions",
  "system prompt",
  "other family",
  "another family",
  "wife private",
  "bypass rules",
  "reveal all families",
  "admin password",
];

const CHILD_SECRECY_PATTERNS = [
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
];

const PII_PATTERNS = [
  "home address",
  "my school is",
  "street address",
  "password is",
  "credit card",
  "passport number",
];

const HIGH_RISK_EMERGENCY_PATTERNS = [
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
];

const PROFESSIONAL_ADVICE_PATTERNS = [
  "diagnose",
  "prescription",
  "dosage",
  "medicine should i take",
  "legal advice",
  "sue ",
  "court custody",
  "invest my savings",
  "financial advice",
];

export function runSafetyPreCheck(
  prompt: string,
  requester: FamilyMemberRecord,
  requestedMode: IdentityMode,
  digitalBabaIdentity: AiIdentityRecord | null
): SafetyPreCheckResult {
  const lower = prompt.toLowerCase();

  if (requester.consentState !== "granted") {
    return {
      allowed: false,
      blockReason:
        "Consent has not been granted or was revoked for this profile. Please update consent settings with an authorized adult.",
      safetyCategory: "pii_guard",
      severity: "medium",
    };
  }

  if (requestedMode === "DIGITAL_BABA" && (!digitalBabaIdentity || digitalBabaIdentity.status !== "approved")) {
    return {
      allowed: false,
      blockReason: `Digital Baba is not currently approved or active (status: ${digitalBabaIdentity?.status ?? "not created"}). Only Father-approved versions can respond.`,
      severity: "low",
      escalationType: "live_guidance",
    };
  }

  if (INJECTION_PATTERNS.some((p) => lower.includes(p))) {
    return {
      allowed: false,
      blockReason:
        "I cannot access other families, private adult conversations, or system instructions. Every family workspace is strictly isolated.",
      safetyCategory: "prompt_injection",
      severity: "high",
      escalationType: "safety_escalation",
    };
  }

  if (HIGH_RISK_EMERGENCY_PATTERNS.some((p) => lower.includes(p))) {
    return {
      allowed: false,
      blockReason:
        "This sounds urgent or high-risk. BABA is an AI companion and cannot provide emergency, medical, or crisis intervention. Please contact local emergency services or a trusted adult.",
      safetyCategory: "high_risk_child",
      severity: "high",
      escalationType: "safety_escalation",
    };
  }

  if (requester.role === "child") {
    if (CHILD_SECRECY_PATTERNS.some((p) => lower.includes(p))) {
      return {
        allowed: false,
        blockReason:
          "I am BABA, an AI — I cannot promise to keep secrets from your parents or trusted adults. Please talk with your Mom, Dad, or another trusted adult.",
        safetyCategory: "child_secrecy",
        severity: "medium",
        escalationType: "safety_escalation",
      };
    }
    if (PII_PATTERNS.some((p) => lower.includes(p))) {
      return {
        allowed: false,
        blockReason: "For your safety, please do not share personal details like your home address, school name, or passwords here.",
        safetyCategory: "pii_guard",
        severity: "medium",
      };
    }
  }

  if (PROFESSIONAL_ADVICE_PATTERNS.some((p) => lower.includes(p))) {
    return {
      allowed: false,
      blockReason:
        "BABA cannot make medical, legal, or financial decisions or replace a qualified doctor, lawyer, or adviser. Please consult a qualified professional or an authorized family adult.",
      safetyCategory: "medical_legal_escalation",
      severity: "medium",
      escalationType: "professional_referral",
    };
  }

  return { allowed: true, severity: "low" };
}

export interface RawModelResponse {
  responseText: string;
  sourceType: SourceType;
  sourceReferences: string[];
  requiresHumanInput: boolean;
  escalationType: string | null;
  safetyStatus: SafetyStatus;
}

export function runSafetyPostCheck(params: {
  prompt: string;
  raw: RawModelResponse;
  requestedMode: IdentityMode;
  requester: FamilyMemberRecord;
  permittedContext: PermittedContext;
  taskType: OrchestratorTaskType;
}): {
  responseText: string;
  identityMode: IdentityMode;
  sourceType: SourceType;
  sourceReferences: string[];
  requiresHumanInput: boolean;
  escalationType: string | null;
  safetyStatus: SafetyStatus;
  disclosure: string;
  agentUsed: string;
} {
  const { prompt, raw, requestedMode, requester, permittedContext, taskType } = params;
  const lowerPrompt = prompt.toLowerCase();
  const lowerOutput = raw.responseText.toLowerCase();
  const permittedIds = new Set([
    ...permittedContext.memories.map((m) => m.id),
    ...permittedContext.guidance.map((g) => g.id),
  ]);
  const sanitizedRefs = raw.sourceReferences.filter((id) => permittedIds.has(id));
  const disclosure = IDENTITY_DISCLOSURES[requestedMode];

  let finalText = raw.responseText;
  let safetyStatus = raw.safetyStatus;
  let requiresHumanInput = raw.requiresHumanInput;
  let escalationType = raw.escalationType;

  const impersonationLeak =
    lowerOutput.includes("i am your real father") ||
    lowerOutput.includes("i am really your dad") ||
    lowerOutput.includes("this is the real baba speaking");

  const askingIfRealFather =
    lowerPrompt.includes("are you really my dad") ||
    lowerPrompt.includes("are you my real father") ||
    lowerPrompt.includes("are you a human") ||
    lowerPrompt.includes("is this really baba");

  if (impersonationLeak) {
    finalText = `To be completely clear: ${disclosure} I am an AI system, not the real human father. Send a Real Baba message to hear directly from him.`;
    safetyStatus = "needs_review";
  } else if (askingIfRealFather && !lowerOutput.includes("ai")) {
    finalText = `${disclosure} I am an AI representation/companion, not the real human father.`;
  }

  if (permittedContext.hasGuidanceConflict) {
    requiresHumanInput = true;
    escalationType = "live_guidance";
    finalText = `${finalText}\n\nNote: There are conflicting approved guidance entries on this topic, so this has been flagged for an authorized adult to review.`;
  }

  const decisionCue =
    lowerPrompt.includes("can i ") ||
    lowerPrompt.includes("may i ") ||
    lowerPrompt.includes("should i ") ||
    lowerPrompt.includes("permission") ||
    lowerPrompt.includes("allowed to") ||
    lowerPrompt.includes("opinion");

  if (
    requestedMode === "DIGITAL_BABA" &&
    sanitizedRefs.length === 0 &&
    permittedContext.guidance.length === 0 &&
    decisionCue
  ) {
    requiresHumanInput = true;
    escalationType = escalationType ?? "live_guidance";
    if (!finalText.toLowerCase().includes("don't know") && !finalText.toLowerCase().includes("do not know")) {
      finalText =
        "I don't know your father's approved view on this yet, and I will not guess. Please send a Live Guidance request so your father or an authorized adult can reply.";
    }
  }

  const agentUsed =
    taskType === "DIGITAL_BABA_CHAT"
      ? "FatherIdentityAgent"
      : taskType === "KIDS_LEARNING"
        ? "ChildrenLearningAgent"
        : taskType === "KIDS_STORYLAND"
          ? "StorytellingAgent"
          : "FamilyCompanionAgent";

  if (requester.role === "child" && agentUsed === "ChildrenLearningAgent") {
    if (!lowerOutput.includes("school") && !lowerOutput.includes("check")) {
      finalText = `${finalText}\n\nTip: Remember to double-check with your school notes or teacher too!`;
    }
  }

  return {
    responseText: finalText,
    identityMode: requestedMode,
    sourceType: sanitizedRefs.length > 0 ? raw.sourceType : "general_ai",
    sourceReferences: sanitizedRefs,
    requiresHumanInput,
    escalationType,
    safetyStatus,
    disclosure,
    agentUsed,
  };
}
