import crypto from "node:crypto";
import http, { type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { HttpError, verifyAuthHeader } from "./auth.ts";
import { buildGroundedGeminiGenerator, type AiModelGenerator } from "./geminiClient.ts";
import {
  IDENTITY_DISCLOSURES,
  PLAN_QUOTAS,
  type FamilyRole,
  type IdentityMode,
  type OrchestratorTaskType,
  type PlanId,
} from "./models.ts";
import {
  retrievePermittedContext,
  runSafetyPostCheck,
  runSafetyPreCheck,
  sha256Hex,
  validateMediaPayload,
  verifyHashConstantTime,
} from "./policyEngine.ts";
import { InMemoryBackendStore } from "./store.ts";

export interface BabaServerOptions {
  store?: InMemoryBackendStore;
  authSecret?: string;
  allowUnsignedJwtInDev?: boolean;
  aiGenerator?: AiModelGenerator;
}

const MAX_BODY_BYTES = 1_000_000;

export function currentPeriodKey(date = new Date()): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}

export function createBabaServer(options: BabaServerOptions = {}): { server: Server; store: InMemoryBackendStore } {
  const store = options.store ?? new InMemoryBackendStore();
  const authSecret = options.authSecret ?? process.env.BABA_AUTH_SECRET ?? "baba-dev-secret-change-in-prod";
  const allowUnsignedJwt = options.allowUnsignedJwtInDev ?? process.env.BABA_ALLOW_UNSIGNED_DEV_JWT === "true";
  const aiGenerator = options.aiGenerator ?? buildGroundedGeminiGenerator();

  const server = http.createServer(async (req, res) => {
    setSecurityHeaders(res);
    if (req.method === "OPTIONS") {
      res.writeHead(204);
      res.end();
      return;
    }

    try {
      const url = new URL(req.url || "/", "http://localhost");
      const path = url.pathname;

      if (req.method === "GET" && path === "/api/v1/health") {
        sendJson(res, 200, { status: "ok", service: "baba-backend", version: "1.0.0" });
        return;
      }

      const authUser = verifyAuthHeader(req.headers, authSecret, allowUnsignedJwt);

      if (req.method === "POST" && path === "/api/v1/families") {
        const body = await readJsonBody(req);
        const familyName = requireNonEmptyString(body.name, "name", 80);
        const role = body.role === "wife" ? "wife" : "father";
        const planId = isPlanId(body.planId) ? body.planId : "family_plus";
        const family = store.createFamily({
          ownerUid: authUser.uid,
          ownerName: requireStringOrDefault(body.displayName, authUser.name, 80),
          familyName,
          role,
          planId,
        });
        sendJson(res, 201, { family });
        return;
      }

      if (req.method === "POST" && path === "/api/v1/invitations") {
        const body = await readJsonBody(req);
        const familyId = requireNonEmptyString(body.familyId, "familyId", 128);
        const invitedEmail = requireNonEmptyString(body.invitedEmail, "invitedEmail", 160).toLowerCase();
        const role = parseRole(body.role);
        const family = store.getFamily(familyId);
        if (!family || !family.adultUids.includes(authUser.uid)) {
          throw new HttpError(403, "FORBIDDEN", "Only an authorized adult of this family can issue invitations.");
        }
        const rawToken = crypto.randomBytes(18).toString("base64url");
        const tokenHash = sha256Hex(rawToken);
        const inviteId = store.createId("inv");
        const invite = {
          id: inviteId,
          familyId,
          familyName: family.name,
          invitedEmail,
          role,
          ageBand: parseAgeBand(body.ageBand, role),
          createdBy: authUser.uid,
          tokenHash,
          status: "pending" as const,
          expiresAtMillis: Date.now() + 7 * 24 * 60 * 60 * 1000,
        };
        store.invitations.set(inviteId, invite);
        sendJson(res, 201, {
          invitationId: inviteId,
          familyId,
          invitedEmail,
          role,
          status: invite.status,
          expiresAtMillis: invite.expiresAtMillis,
          oneTimeInviteToken: rawToken,
        });
        return;
      }

      const acceptMatch = /^\/api\/v1\/invitations\/([a-zA-Z0-9_-]+)\/accept$/.exec(path);
      if (req.method === "POST" && acceptMatch) {
        const inviteId = acceptMatch[1];
        const body = await readJsonBody(req);
        const token = requireNonEmptyString(body.token, "token", 256);
        const invite = store.invitations.get(inviteId);
        if (!invite) {
          throw new HttpError(404, "NOT_FOUND", "Invitation not found.");
        }
        if (invite.status !== "pending") {
          throw new HttpError(409, "INVITE_NOT_PENDING", "Invitation is no longer active.");
        }
        if (invite.expiresAtMillis < Date.now()) {
          throw new HttpError(403, "INVITE_EXPIRED", "Invitation has expired.");
        }
        if (!authUser.emailVerified || authUser.email.toLowerCase() !== invite.invitedEmail.toLowerCase()) {
          throw new HttpError(403, "EMAIL_MISMATCH", "Verified email does not match the invited email.");
        }
        if (!verifyHashConstantTime(token, invite.tokenHash)) {
          throw new HttpError(403, "INVALID_INVITE_TOKEN", "Invitation token verification failed.");
        }
        const family = store.getFamily(invite.familyId);
        if (!family) {
          throw new HttpError(404, "FAMILY_NOT_FOUND", "Target family no longer exists.");
        }
        invite.status = "accepted";
        invite.acceptedByUid = authUser.uid;
        if (!family.memberUids.includes(authUser.uid)) {
          family.memberUids.push(authUser.uid);
        }
        if (invite.role !== "child" && !family.adultUids.includes(authUser.uid)) {
          family.adultUids.push(authUser.uid);
        }
        family.updatedAtIso = new Date().toISOString();
        const memberMap = store.members.get(family.id) ?? new Map();
        memberMap.set(authUser.uid, {
          familyId: family.id,
          uid: authUser.uid,
          displayName: requireStringOrDefault(body.displayName, authUser.name, 80),
          role: invite.role,
          ageBand: invite.ageBand,
          consentState: "granted",
          membershipStatus: "active",
        });
        store.members.set(family.id, memberMap);
        sendJson(res, 200, { status: "accepted", familyId: family.id, role: invite.role });
        return;
      }

      if (req.method === "POST" && path === "/api/v1/ai/respond") {
        const body = await readJsonBody(req);
        const familyId = requireNonEmptyString(body.familyId, "familyId", 128);
        const prompt = requireNonEmptyString(body.prompt, "prompt", 4000);
        const requestedMode: IdentityMode = body.requestedMode === "DIGITAL_BABA" ? "DIGITAL_BABA" : "BABA_COMPANION";
        const taskType = parseTaskType(body.taskType, requestedMode);

        const family = store.getFamily(familyId);
        const member = store.getMember(familyId, authUser.uid);
        if (!family || !member || !family.memberUids.includes(authUser.uid)) {
          throw new HttpError(403, "TENANT_ISOLATION_DENIED", "User is not an active member of this family workspace.");
        }
        if (family.status !== "active" || member.membershipStatus !== "active") {
          throw new HttpError(403, "ACCOUNT_SUSPENDED", "Family workspace or membership is suspended.");
        }

        const plan = PLAN_QUOTAS[family.planId];
        if (requestedMode === "DIGITAL_BABA" && !plan.digitalBabaEnabled) {
          throw new HttpError(403, "PLAN_UPGRADE_REQUIRED", `Plan ${plan.displayName} does not include Digital Baba mode.`);
        }
        const periodKey = currentPeriodKey();
        const usedUnits = store.getMonthlyUsageUnits(familyId, periodKey);
        if (usedUnits >= plan.monthlyAiUnitsLimit) {
          throw new HttpError(
            429,
            "QUOTA_EXCEEDED",
            `Monthly AI quota reached (${usedUnits}/${plan.monthlyAiUnitsLimit} units on ${plan.displayName}).`
          );
        }

        const identity = store.getApprovedIdentity(familyId);
        const preCheck = runSafetyPreCheck(prompt, member, requestedMode, identity);
        if (!preCheck.allowed) {
          if (preCheck.safetyCategory) {
            store.addSafetyEvent({
              id: store.createId("saf"),
              familyId,
              triggeredByUid: authUser.uid,
              category: preCheck.safetyCategory,
              severity: preCheck.severity,
              summaryMetadata: `Backend policy check (${preCheck.safetyCategory})`,
              createdAtIso: new Date().toISOString(),
            });
          }
          sendJson(res, 200, {
            responseText: preCheck.blockReason,
            identityMode: requestedMode,
            sourceType: "general_ai",
            sourceReferences: [],
            requiresHumanInput: preCheck.escalationType === "live_guidance",
            escalationType: preCheck.escalationType ?? null,
            safetyStatus: preCheck.safetyCategory ? "blocked" : "needs_review",
            disclosure: IDENTITY_DISCLOSURES[requestedMode],
            agentUsed: "SafetyPolicyAgent",
            usageUnitsConsumed: usedUnits,
          });
          return;
        }

        const context = retrievePermittedContext(
          familyId,
          member,
          prompt,
          store.memories.get(familyId) ?? [],
          store.guidance.get(familyId) ?? []
        );

        const raw = await aiGenerator({
          taskType,
          requestedMode,
          prompt,
          requester: member,
          digitalBabaIdentity: identity,
          context,
        });

        const checked = runSafetyPostCheck({
          prompt,
          raw,
          requestedMode,
          requester: member,
          permittedContext: context,
          taskType,
        });

        store.addUsage({
          id: store.createId("use"),
          familyId,
          actorUid: authUser.uid,
          service: "ai_turn",
          units: 1,
          estimatedCostUsdCents: 1,
          periodKey,
          createdAtIso: new Date().toISOString(),
        });

        sendJson(res, 200, {
          ...checked,
          usageUnitsConsumed: usedUnits + 1,
        });
        return;
      }

      if (req.method === "POST" && path === "/api/v1/media/validate") {
        const body = await readJsonBody(req);
        const familyId = requireNonEmptyString(body.familyId, "familyId", 128);
        const mimeType = requireNonEmptyString(body.mimeType, "mimeType", 64);
        const payloadBase64 = requireNonEmptyString(body.payloadBase64, "payloadBase64", 600_000);
        const family = store.getFamily(familyId);
        if (!family || !family.memberUids.includes(authUser.uid)) {
          throw new HttpError(403, "TENANT_ISOLATION_DENIED", "User is not an active member of this family.");
        }
        const validation = validateMediaPayload(mimeType, payloadBase64);
        if (!validation.valid) {
          throw new HttpError(400, "MEDIA_VALIDATION_FAILED", validation.reason || "Invalid media file.");
        }
        const sharingScope =
          body.sharingScope === "private" || body.sharingScope === "selected_members" ? body.sharingScope : "family";
        const mediaId = store.createId("med");
        const record = {
          id: mediaId,
          familyId,
          ownerUid: authUser.uid,
          mimeType: validation.mimeType,
          byteSize: validation.byteSize,
          sharingScope,
          scanStatus: "validated" as const,
          createdAtIso: new Date().toISOString(),
        };
        store.addMediaMetadata(record);
        store.addUsage({
          id: store.createId("use"),
          familyId,
          actorUid: authUser.uid,
          service: "media_upload",
          units: 1,
          estimatedCostUsdCents: 1,
          periodKey: currentPeriodKey(),
          createdAtIso: new Date().toISOString(),
        });
        sendJson(res, 200, { media: record });
        return;
      }

      if (req.method === "POST" && path === "/api/v1/privacy/export") {
        const body = await readJsonBody(req);
        const familyId = requireNonEmptyString(body.familyId, "familyId", 128);
        const family = store.getFamily(familyId);
        if (!family || !family.memberUids.includes(authUser.uid)) {
          throw new HttpError(403, "TENANT_ISOLATION_DENIED", "User cannot export data for another family.");
        }
        const memories = (store.memories.get(familyId) ?? []).filter(
          (m) => m.ownerUid === authUser.uid || (m.reviewStatus === "approved" && m.readableByUids.includes(authUser.uid))
        );
        const privateMsgs = store.privateMessages.get(authUser.uid) ?? [];
        const media = (store.mediaRegistry.get(familyId) ?? []).filter((m) => m.ownerUid === authUser.uid);

        sendJson(res, 200, {
          exportedAtIso: new Date().toISOString(),
          requesterUid: authUser.uid,
          familyId,
          familyName: family.name,
          memories,
          privateCompanionMessages: privateMsgs,
          mediaMetadata: media,
        });
        return;
      }

      if (req.method === "GET" && path === "/api/v1/usage/summary") {
        const familyId = url.searchParams.get("familyId") || "";
        const family = store.getFamily(familyId);
        if (!family || !family.adultUids.includes(authUser.uid)) {
          throw new HttpError(403, "FORBIDDEN", "Only authorized family adults can inspect usage metering.");
        }
        const periodKey = currentPeriodKey();
        const plan = PLAN_QUOTAS[family.planId];
        const consumedUnits = store.getMonthlyUsageUnits(familyId, periodKey);
        sendJson(res, 200, {
          familyId,
          periodKey,
          planId: plan.planId,
          displayName: plan.displayName,
          monthlyAiUnitsLimit: plan.monthlyAiUnitsLimit,
          consumedUnits,
          remainingUnits: Math.max(0, plan.monthlyAiUnitsLimit - consumedUnits),
        });
        return;
      }

      throw new HttpError(404, "NOT_FOUND", `Route not found: ${req.method} ${path}`);
    } catch (err) {
      if (err instanceof HttpError) {
        sendJson(res, err.status, { error: err.message, code: err.code });
      } else {
        sendJson(res, 500, { error: "Internal server error.", code: "INTERNAL_ERROR" });
      }
    }
  });

  return { server, store };
}

function setSecurityHeaders(res: ServerResponse): void {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
}

function sendJson(res: ServerResponse, status: number, data: unknown): void {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(data));
}

async function readJsonBody(req: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of req) {
    const buf = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buf.length;
    if (total > MAX_BODY_BYTES) {
      throw new HttpError(413, "PAYLOAD_TOO_LARGE", "Request body exceeds maximum allowed size.");
    }
    chunks.push(buf);
  }
  if (chunks.length === 0) return {};
  try {
    const parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    return typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    throw new HttpError(400, "INVALID_JSON", "Malformed JSON request body.");
  }
}

function requireNonEmptyString(val: unknown, field: string, maxLen: number): string {
  if (typeof val !== "string" || val.trim().length === 0 || val.length > maxLen) {
    throw new HttpError(400, "VALIDATION_ERROR", `Invalid or missing field: ${field}`);
  }
  return val.trim();
}

function requireStringOrDefault(val: unknown, fallback: string, maxLen: number): string {
  if (typeof val === "string" && val.trim().length > 0 && val.length <= maxLen) {
    return val.trim();
  }
  return fallback.slice(0, maxLen);
}

function parseRole(val: unknown): FamilyRole {
  if (val === "father" || val === "wife" || val === "child") return val;
  throw new HttpError(400, "VALIDATION_ERROR", "role must be father, wife, or child");
}

function parseAgeBand(val: unknown, role: FamilyRole): "adult" | "6-8" | "9-12" | "13-15" | "16-17" {
  if (val === "adult" || val === "6-8" || val === "9-12" || val === "13-15" || val === "16-17") return val;
  return role === "child" ? "9-12" : "adult";
}

function isPlanId(val: unknown): val is PlanId {
  return val === "free" || val === "family_starter" || val === "family_plus" || val === "family_premium";
}

function parseTaskType(val: unknown, mode: IdentityMode): OrchestratorTaskType {
  if (
    val === "DIGITAL_BABA_CHAT" ||
    val === "COMPANION_CHAT" ||
    val === "KIDS_LEARNING" ||
    val === "KIDS_STORYLAND"
  ) {
    return val;
  }
  return mode === "DIGITAL_BABA" ? "DIGITAL_BABA_CHAT" : "COMPANION_CHAT";
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT || 8080);
  const { server } = createBabaServer();
  server.listen(port, () => {
    console.log(`BABA Backend listening on http://localhost:${port}`);
  });
}
