import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { createSignedDevToken } from "../src/auth.ts";
import { createBabaServer, currentPeriodKey } from "../src/server.ts";
import type { InMemoryBackendStore } from "../src/store.ts";

const TEST_SECRET = "test-only-hmac-secret";
let baseUrl = "";
let serverRef: ReturnType<typeof createBabaServer>["server"];
let storeRef: InMemoryBackendStore;

before(async () => {
  const { server, store } = createBabaServer({ authSecret: TEST_SECRET });
  serverRef = server;
  storeRef = store;
  await new Promise<void>((resolve) => {
    serverRef.listen(0, "127.0.0.1", () => resolve());
  });
  const addr = serverRef.address();
  if (!addr || typeof addr === "string") {
    throw new Error("Failed to bind ephemeral port");
  }
  baseUrl = `http://127.0.0.1:${addr.port}`;
});

after(async () => {
  await new Promise<void>((resolve, reject) => {
    serverRef.close((err) => (err ? reject(err) : resolve()));
  });
});

beforeEach(() => {
  storeRef.families.clear();
  storeRef.members.clear();
  storeRef.invitations.clear();
  storeRef.aiIdentities.clear();
  storeRef.memories.clear();
  storeRef.guidance.clear();
  storeRef.privateMessages.clear();
  storeRef.usageRecords.clear();
  storeRef.safetyEvents.clear();
  storeRef.mediaRegistry.clear();
});

function authHeader(uid: string, email: string, emailVerified = true): Record<string, string> {
  const tok = createSignedDevToken({ uid, email, emailVerified, name: uid }, TEST_SECRET);
  return { Authorization: `Bearer ${tok}`, "Content-Type": "application/json" };
}

test("unauthenticated request to protected routes returns 401", async () => {
  const res = await fetch(`${baseUrl}/api/v1/families`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Unauthorized Family" }),
  });
  assert.equal(res.status, 401);

  const health = await fetch(`${baseUrl}/api/v1/health`);
  assert.equal(health.status, 200);
});

test("hashed invitation workflow verifies SHA-256 token and blocks wrong email/token", async () => {
  const famRes = await fetch(`${baseUrl}/api/v1/families`, {
    method: "POST",
    headers: authHeader("father_1", "father@baba.test"),
    body: JSON.stringify({ name: "Al-Noor Family", role: "father" }),
  });
  assert.equal(famRes.status, 201);
  const { family } = (await famRes.json()) as { family: { id: string } };

  const invRes = await fetch(`${baseUrl}/api/v1/invitations`, {
    method: "POST",
    headers: authHeader("father_1", "father@baba.test"),
    body: JSON.stringify({ familyId: family.id, invitedEmail: "wife@baba.test", role: "wife" }),
  });
  assert.equal(invRes.status, 201);
  const invitePayload = (await invRes.json()) as { invitationId: string; oneTimeInviteToken: string };

  // Stored invitation only holds SHA-256 hash, never raw token
  const storedInvite = storeRef.invitations.get(invitePayload.invitationId);
  assert.ok(storedInvite);
  assert.equal(storedInvite.tokenHash.length, 64);
  assert.notEqual(storedInvite.tokenHash, invitePayload.oneTimeInviteToken);

  // Wrong email rejected with 403
  const wrongEmail = await fetch(`${baseUrl}/api/v1/invitations/${invitePayload.invitationId}/accept`, {
    method: "POST",
    headers: authHeader("stranger_1", "stranger@baba.test"),
    body: JSON.stringify({ token: invitePayload.oneTimeInviteToken }),
  });
  assert.equal(wrongEmail.status, 403);

  // Wrong token rejected with 403
  const wrongToken = await fetch(`${baseUrl}/api/v1/invitations/${invitePayload.invitationId}/accept`, {
    method: "POST",
    headers: authHeader("wife_1", "wife@baba.test"),
    body: JSON.stringify({ token: "tampered_token" }),
  });
  assert.equal(wrongToken.status, 403);

  // Valid email & token accepted
  const acceptOk = await fetch(`${baseUrl}/api/v1/invitations/${invitePayload.invitationId}/accept`, {
    method: "POST",
    headers: authHeader("wife_1", "wife@baba.test"),
    body: JSON.stringify({ token: invitePayload.oneTimeInviteToken, displayName: "Layla" }),
  });
  assert.equal(acceptOk.status, 200);
});

test("AI respond enforces tenant isolation, memory permissions, safety blocks, and monthly quota", async () => {
  const fam = storeRef.createFamily({
    ownerUid: "father_a",
    ownerName: "Tariq",
    familyName: "Family A",
    role: "father",
    planId: "free",
  });
  storeRef.members.get(fam.id)?.set("child_a", {
    familyId: fam.id,
    uid: "child_a",
    displayName: "Zayd",
    role: "child",
    ageBand: "9-12",
    consentState: "granted",
    membershipStatus: "active",
  });
  fam.memberUids.push("child_a");

  storeRef.memories.set(fam.id, [
    {
      id: "mem_private_dad",
      familyId: fam.id,
      ownerUid: "father_a",
      title: "Private Dad Note",
      content: "TOP_SECRET_DAD_ONLY",
      sharingScope: "private",
      allowedMemberIds: ["father_a"],
      readableByUids: ["father_a"],
      reviewStatus: "approved",
      ownershipConfirmed: true,
    },
    {
      id: "mem_story_ok",
      familyId: fam.id,
      ownerUid: "father_a",
      title: "Rocket Model",
      content: "We built a paper rocket together.",
      sharingScope: "family",
      allowedMemberIds: ["father_a", "child_a"],
      readableByUids: ["father_a", "child_a"],
      reviewStatus: "approved",
      ownershipConfirmed: true,
    },
  ]);

  // Cross-family member receives 403
  const crossFam = await fetch(`${baseUrl}/api/v1/ai/respond`, {
    method: "POST",
    headers: authHeader("stranger_b", "b@baba.test"),
    body: JSON.stringify({
      familyId: fam.id,
      taskType: "COMPANION_CHAT",
      requestedMode: "BABA_COMPANION",
      prompt: "Hello",
    }),
  });
  assert.equal(crossFam.status, 403);

  // Child secrecy blocked before model call and logs SafetyEvent
  const secrecy = await fetch(`${baseUrl}/api/v1/ai/respond`, {
    method: "POST",
    headers: authHeader("child_a", "child@baba.test"),
    body: JSON.stringify({
      familyId: fam.id,
      taskType: "KIDS_LEARNING",
      requestedMode: "BABA_COMPANION",
      prompt: "Please keep this secret from mom and dad.",
    }),
  });
  assert.equal(secrecy.status, 200);
  const secrecyJson = (await secrecy.json()) as { safetyStatus: string };
  assert.equal(secrecyJson.safetyStatus, "blocked");
  assert.equal(storeRef.safetyEvents.get(fam.id)?.length, 1);

  // Storyland cites only permitted memory, never private Dad note
  const story = await fetch(`${baseUrl}/api/v1/ai/respond`, {
    method: "POST",
    headers: authHeader("child_a", "child@baba.test"),
    body: JSON.stringify({
      familyId: fam.id,
      taskType: "KIDS_STORYLAND",
      requestedMode: "BABA_COMPANION",
      prompt: "Tell me a story about building a rocket",
    }),
  });
  assert.equal(story.status, 200);
  const storyJson = (await story.json()) as { sourceReferences: string[]; responseText: string };
  assert.deepEqual(storyJson.sourceReferences, ["mem_story_ok"]);
  assert.ok(!storyJson.responseText.includes("TOP_SECRET_DAD_ONLY"));

  // Free plan quota (30 units) blocks once exhausted with 429
  storeRef.addUsage({
    id: "bulk_usage",
    familyId: fam.id,
    actorUid: "child_a",
    service: "ai_turn",
    units: 29,
    estimatedCostUsdCents: 29,
    periodKey: currentPeriodKey(),
    createdAtIso: new Date().toISOString(),
  });
  const overQuota = await fetch(`${baseUrl}/api/v1/ai/respond`, {
    method: "POST",
    headers: authHeader("child_a", "child@baba.test"),
    body: JSON.stringify({
      familyId: fam.id,
      taskType: "COMPANION_CHAT",
      requestedMode: "BABA_COMPANION",
      prompt: "One more question",
    }),
  });
  assert.equal(overQuota.status, 429);
});

test("media validation rejects executables and SVG scripts while allowing valid JPEG", async () => {
  const fam = storeRef.createFamily({
    ownerUid: "father_a",
    ownerName: "Tariq",
    familyName: "Media Family",
    role: "father",
  });

  const mzPayload = Buffer.from("MZ\x90\x00fake-exe").toString("base64");
  const badExe = await fetch(`${baseUrl}/api/v1/media/validate`, {
    method: "POST",
    headers: authHeader("father_a", "f@baba.test"),
    body: JSON.stringify({ familyId: fam.id, mimeType: "image/jpeg", payloadBase64: mzPayload }),
  });
  assert.equal(badExe.status, 400);

  const svgPayload = Buffer.from("<svg><script>alert(1)</script></svg>").toString("base64");
  const badSvg = await fetch(`${baseUrl}/api/v1/media/validate`, {
    method: "POST",
    headers: authHeader("father_a", "f@baba.test"),
    body: JSON.stringify({ familyId: fam.id, mimeType: "image/png", payloadBase64: svgPayload }),
  });
  assert.equal(badSvg.status, 400);

  const okJpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10]).toString("base64");
  const goodRes = await fetch(`${baseUrl}/api/v1/media/validate`, {
    method: "POST",
    headers: authHeader("father_a", "f@baba.test"),
    body: JSON.stringify({ familyId: fam.id, mimeType: "image/jpeg", payloadBase64: okJpeg }),
  });
  assert.equal(goodRes.status, 200);
});

test("privacy export packages only requester-authorized data and excludes sibling private notes", async () => {
  const fam = storeRef.createFamily({
    ownerUid: "father_a",
    ownerName: "Tariq",
    familyName: "Export Family",
    role: "father",
  });
  fam.memberUids.push("wife_a");
  storeRef.members.get(fam.id)?.set("wife_a", {
    familyId: fam.id,
    uid: "wife_a",
    displayName: "Layla",
    role: "wife",
    ageBand: "adult",
    consentState: "granted",
    membershipStatus: "active",
  });
  storeRef.privateMessages.set("wife_a", [
    { id: "pm_w", ownerUid: "wife_a", text: "WIFE_CONFIDENTIAL_DIARY", createdAtIso: new Date().toISOString() },
  ]);
  storeRef.privateMessages.set("father_a", [
    { id: "pm_f", ownerUid: "father_a", text: "Father personal note", createdAtIso: new Date().toISOString() },
  ]);

  const exportRes = await fetch(`${baseUrl}/api/v1/privacy/export`, {
    method: "POST",
    headers: authHeader("father_a", "f@baba.test"),
    body: JSON.stringify({ familyId: fam.id }),
  });
  assert.equal(exportRes.status, 200);
  const body = (await exportRes.json()) as { privateCompanionMessages: Array<{ text: string }> };
  assert.equal(body.privateCompanionMessages.length, 1);
  assert.equal(body.privateCompanionMessages[0].text, "Father personal note");
});
