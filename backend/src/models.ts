export type IdentityMode = "DIGITAL_BABA" | "BABA_COMPANION" | "REAL_BABA";
export type SourceType = "approved_guidance" | "memory" | "general_ai" | "human_message";
export type SafetyStatus = "approved" | "blocked" | "needs_review";
export type FamilyRole = "father" | "wife" | "child";
export type PlanId = "free" | "family_starter" | "family_plus" | "family_premium";
export type OrchestratorTaskType =
  | "DIGITAL_BABA_CHAT"
  | "COMPANION_CHAT"
  | "KIDS_LEARNING"
  | "KIDS_STORYLAND";

export interface PlanQuotaSpec {
  planId: PlanId;
  displayName: string;
  monthlyAiUnitsLimit: number;
  digitalBabaEnabled: boolean;
  voiceMessagesEnabled: boolean;
}

export const PLAN_QUOTAS: Record<PlanId, PlanQuotaSpec> = {
  free: {
    planId: "free",
    displayName: "Free",
    monthlyAiUnitsLimit: 30,
    digitalBabaEnabled: false,
    voiceMessagesEnabled: false,
  },
  family_starter: {
    planId: "family_starter",
    displayName: "Family Starter",
    monthlyAiUnitsLimit: 150,
    digitalBabaEnabled: true,
    voiceMessagesEnabled: false,
  },
  family_plus: {
    planId: "family_plus",
    displayName: "Family Plus",
    monthlyAiUnitsLimit: 500,
    digitalBabaEnabled: true,
    voiceMessagesEnabled: true,
  },
  family_premium: {
    planId: "family_premium",
    displayName: "Family Premium",
    monthlyAiUnitsLimit: 2000,
    digitalBabaEnabled: true,
    voiceMessagesEnabled: true,
  },
};

export const IDENTITY_DISCLOSURES: Record<IdentityMode, string> = {
  DIGITAL_BABA: "AI version of your father, based on information he approved.",
  BABA_COMPANION: "I am BABA, an AI companion.",
  REAL_BABA: "Verified human-authored message.",
};

export interface AuthUser {
  uid: string;
  email: string;
  emailVerified: boolean;
  name: string;
}

export interface FamilyRecord {
  id: string;
  name: string;
  createdBy: string;
  status: "active" | "suspended";
  planId: PlanId;
  memberUids: string[];
  adultUids: string[];
  createdAtIso: string;
  updatedAtIso: string;
}

export interface FamilyMemberRecord {
  familyId: string;
  uid: string;
  displayName: string;
  role: FamilyRole;
  ageBand: "adult" | "6-8" | "9-12" | "13-15" | "16-17";
  consentState: "granted" | "pending" | "revoked";
  membershipStatus: "active" | "suspended";
}

export interface InvitationRecord {
  id: string;
  familyId: string;
  familyName: string;
  invitedEmail: string;
  role: FamilyRole;
  ageBand: "adult" | "6-8" | "9-12" | "13-15" | "16-17";
  createdBy: string;
  tokenHash: string;
  status: "pending" | "accepted" | "revoked";
  expiresAtMillis: number;
  acceptedByUid?: string;
}

export interface AiIdentityRecord {
  id: string;
  familyId: string;
  ownerUid: string;
  status: "draft" | "approved" | "suspended";
  activeVersion: number;
  communicationStyle: string;
  values: string;
  interests: string;
  bioSummary: string;
}

export interface MemoryRecord {
  id: string;
  familyId: string;
  ownerUid: string;
  title: string;
  content: string;
  sharingScope: "private" | "selected_members" | "family";
  allowedMemberIds: string[];
  readableByUids: string[];
  reviewStatus: "draft" | "pending_review" | "approved" | "revoked";
  ownershipConfirmed: boolean;
}

export interface GuidanceRecord {
  id: string;
  familyId: string;
  topic: string;
  question: string;
  answerText: string;
  audience: "family" | "wife" | "child";
  readableByUids: string[];
  status: "requested" | "draft" | "approved" | "superseded";
}

export interface PrivateMessageRecord {
  id: string;
  ownerUid: string;
  text: string;
  createdAtIso: string;
}

export interface UsageRecord {
  id: string;
  familyId: string;
  actorUid: string;
  service: "ai_turn" | "voice_note" | "media_upload" | "data_export";
  units: number;
  estimatedCostUsdCents: number;
  periodKey: string;
  createdAtIso: string;
}

export interface SafetyEventRecord {
  id: string;
  familyId: string;
  triggeredByUid: string;
  category: string;
  severity: "low" | "medium" | "high";
  summaryMetadata: string;
  createdAtIso: string;
}
