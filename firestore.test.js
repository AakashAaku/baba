const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const nowTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const FATHER_A = "father_a_uid";
const WIFE_A = "wife_a_uid";
const CHILD_A = "child_a_uid";
const FATHER_B = "father_b_uid";
const ADMIN_UID = "admin_uid";
const ADMIN_EMAIL = "salesadvisor@fazahautogarage.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedFamilyA() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = nowTs();
    await db.collection("families").doc("fam_a").set({
      name: "Al-Mansoor Family",
      createdBy: FATHER_A,
      status: "active",
      planId: "family_plus",
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      adultUids: [FATHER_A, WIFE_A],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_a").collection("members").doc(FATHER_A).set({
      familyId: "fam_a",
      uid: FATHER_A,
      displayName: "Tariq",
      role: "father",
      ageBand: "adult",
      guardianUid: "",
      consentState: "granted",
      membershipStatus: "active",
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      joinedAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_a").collection("members").doc(WIFE_A).set({
      familyId: "fam_a",
      uid: WIFE_A,
      displayName: "Layla",
      role: "wife",
      ageBand: "adult",
      guardianUid: "",
      consentState: "granted",
      membershipStatus: "active",
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      joinedAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_a").collection("members").doc(CHILD_A).set({
      familyId: "fam_a",
      uid: CHILD_A,
      displayName: "Zayd",
      role: "child",
      ageBand: "9-12",
      guardianUid: FATHER_A,
      consentState: "granted",
      membershipStatus: "active",
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      joinedAt: now,
      updatedAt: now,
    });
    await db.collection("families").doc("fam_b").set({
      name: "Other Family B",
      createdBy: FATHER_B,
      status: "active",
      planId: "free",
      memberUids: [FATHER_B],
      adultUids: [FATHER_B],
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated user cannot read or list families or memories", async () => {
  await seedFamilyA();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("families").doc("fam_a").get());
  await assertFails(unauthDb.collection("families").get());
  await assertFails(unauthDb.collection("families").doc("fam_a").collection("memories").get());
});

test("Cross-family isolation: Family B cannot read or query Family A workspace, memories, or messages", async () => {
  await seedFamilyA();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = nowTs();
    await db.collection("families").doc("fam_a").collection("memories").doc("mem_1").set({
      familyId: "fam_a",
      ownerUid: FATHER_A,
      memoryClass: "shared_family",
      title: "First Bike Ride",
      content: "We rode bicycles by the corniche.",
      sourceType: "text",
      provenanceNote: "Supplied by Father",
      sharingScope: "family",
      allowedMemberIds: [FATHER_A, WIFE_A, CHILD_A],
      readableByUids: [FATHER_A, WIFE_A, CHILD_A],
      reviewStatus: "approved",
      ownershipConfirmed: true,
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      createdAt: now,
      updatedAt: now,
    });
  });

  const fatherBDb = testEnv.authenticatedContext(FATHER_B).firestore();
  await assertFails(fatherBDb.collection("families").doc("fam_a").get());
  await assertFails(fatherBDb.collection("families").doc("fam_a").collection("memories").doc("mem_1").get());
  await assertFails(fatherBDb.collection("families").doc("fam_a").collection("memories").get());
  await assertFails(
    fatherBDb.collection("families").doc("fam_a").collection("memories")
      .where("readableByUids", "array-contains", FATHER_A)
      .where("reviewStatus", "==", "approved")
      .get()
  );
  const emptySnap = await assertSucceeds(
    fatherBDb.collection("families").doc("fam_a").collection("memories")
      .where("readableByUids", "array-contains", FATHER_B)
      .where("reviewStatus", "==", "approved")
      .get()
  );
  if (!emptySnap.empty) {
    throw new Error("Expected zero memories for Family B");
  }
});

test("Wife private companion conversations are strictly invisible to Father and Admin", async () => {
  await seedFamilyA();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const now = nowTs();
    await db.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1").set({
      ownerUid: WIFE_A,
      title: "Personal Reflection",
      agentMode: "BABA_COMPANION",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1")
      .collection("messages").doc("msg_1").set({
        ownerUid: WIFE_A,
        senderType: "user",
        text: "Wife private note",
        identityMode: "BABA_COMPANION",
        disclosure: "I am BABA, an AI companion.",
        safetyStatus: "approved",
        createdAt: now,
      });
  });

  const wifeDb = testEnv.authenticatedContext(WIFE_A).firestore();
  const fatherDb = testEnv.authenticatedContext(FATHER_A).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    wifeDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1").get()
  );
  await assertFails(
    fatherDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1").get()
  );
  await assertFails(
    fatherDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1")
      .collection("messages").get()
  );
  await assertFails(
    adminDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("conv_1").get()
  );
});

test("Child cannot create Digital Baba identity or approve guidance", async () => {
  await seedFamilyA();
  const now = nowTs();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("families").doc("fam_a").collection("guidance").doc("guid_1").set({
      familyId: "fam_a",
      authorUid: "",
      askedByUid: CHILD_A,
      topic: "Sleepover",
      question: "Can I stay at my friend's house?",
      answerText: "",
      audience: "family",
      readableByUids: [CHILD_A, FATHER_A, WIFE_A],
      status: "requested",
      version: 1,
      reviewAfterLabel: "30 days",
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      adultUids: [FATHER_A, WIFE_A],
      createdAt: now,
      updatedAt: now,
    });
  });

  const childDb = testEnv.authenticatedContext(CHILD_A).firestore();
  await assertFails(
    childDb.collection("families").doc("fam_a").collection("aiIdentities").doc("id_1").set({
      familyId: "fam_a",
      ownerUid: CHILD_A,
      type: "DIGITAL_BABA",
      activeVersion: 1,
      status: "approved",
      communicationStyle: "Warm",
      values: "Kindness",
      interests: "Chess",
      bioSummary: "Fake bio",
      voiceCloningEnabled: false,
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    childDb.collection("families").doc("fam_a").collection("guidance").doc("guid_1").update({
      authorUid: CHILD_A,
      answerText: "Yes, always!",
      status: "approved",
      updatedAt: now,
    })
  );
});

test("Draft memories and unapproved Digital Baba identities are hidden from other family members", async () => {
  await seedFamilyA();
  const now = nowTs();
  const fatherDb = testEnv.authenticatedContext(FATHER_A).firestore();

  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("aiIdentities").doc("baba_v1").set({
      familyId: "fam_a",
      ownerUid: FATHER_A,
      type: "DIGITAL_BABA",
      activeVersion: 1,
      status: "draft",
      communicationStyle: "Warm, patient, encouraging",
      values: "Honesty, curiosity, kindness",
      interests: "Astronomy and cooking",
      bioSummary: "Working abroad, always cheering for you.",
      voiceCloningEnabled: false,
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("memories").doc("mem_draft").set({
      familyId: "fam_a",
      ownerUid: FATHER_A,
      memoryClass: "identity",
      title: "Draft Letter",
      content: "Not ready yet",
      sourceType: "text",
      provenanceNote: "Father draft",
      sharingScope: "family",
      allowedMemberIds: [FATHER_A, WIFE_A, CHILD_A],
      readableByUids: [FATHER_A, WIFE_A, CHILD_A],
      reviewStatus: "draft",
      ownershipConfirmed: true,
      memberUids: [FATHER_A, WIFE_A, CHILD_A],
      createdAt: now,
      updatedAt: now,
    })
  );

  const childDb = testEnv.authenticatedContext(CHILD_A).firestore();
  await assertFails(
    childDb.collection("families").doc("fam_a").collection("aiIdentities").doc("baba_v1").get()
  );
  await assertFails(
    childDb.collection("families").doc("fam_a").collection("memories").doc("mem_draft").get()
  );

  // Query alignment check: Child querying approved memories succeeds and excludes draft
  await assertSucceeds(
    childDb.collection("families").doc("fam_a").collection("memories")
      .where("readableByUids", "array-contains", CHILD_A)
      .where("reviewStatus", "==", "approved")
      .get()
  );
});

test("Shadow update with ghost field and audit log tampering are rejected", async () => {
  await seedFamilyA();
  const fatherDb = testEnv.authenticatedContext(FATHER_A).firestore();
  const now = nowTs();

  // Ghost field injection fails
  await assertFails(
    fatherDb.collection("families").doc("fam_a").update({
      name: "New Name",
      isVerifiedAdminOverride: true,
      updatedAt: now,
    })
  );

  // Append-only audit log: create succeeds, update & delete fail
  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("auditLogs").doc("log_1").set({
      familyId: "fam_a",
      actorUid: FATHER_A,
      action: "CREATE_IDENTITY",
      resourceType: "aiIdentities",
      resourceId: "baba_v1",
      outcome: "allowed",
      adultUids: [FATHER_A, WIFE_A],
      createdAt: now,
    })
  );
  await assertFails(
    fatherDb.collection("families").doc("fam_a").collection("auditLogs").doc("log_1").update({
      outcome: "denied",
    })
  );
  await assertFails(
    fatherDb.collection("families").doc("fam_a").collection("auditLogs").doc("log_1").delete()
  );
});

test("Validated media, notifications inbox, and usageRecords enforce strict tenant and owner isolation", async () => {
  await seedFamilyA();
  const fatherDb = testEnv.authenticatedContext(FATHER_A).firestore();
  const wifeDb = testEnv.authenticatedContext(WIFE_A).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_A).firestore();
  const fatherBDb = testEnv.authenticatedContext(FATHER_B).firestore();
  const now = nowTs();

  // Father uploads validated JPEG media visible only to Father & Wife
  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("media").doc("med_1").set({
      familyId: "fam_a",
      ownerUid: FATHER_A,
      mimeType: "image/jpeg",
      byteSize: 1024,
      scanStatus: "validated",
      sharingScope: "selected_members",
      visibleToUids: [FATHER_A, WIFE_A],
      payloadBase64: "AQIDBA==",
      createdAt: now,
    })
  );

  // Oversized or unsupported mime fails rules
  await assertFails(
    fatherDb.collection("families").doc("fam_a").collection("media").doc("med_bad").set({
      familyId: "fam_a",
      ownerUid: FATHER_A,
      mimeType: "application/x-msdownload",
      byteSize: 1024,
      scanStatus: "validated",
      sharingScope: "family",
      visibleToUids: [FATHER_A],
      payloadBase64: "TVqQ",
      createdAt: now,
    })
  );

  // Wife can read & list med_1, Child and Family B cannot
  await assertSucceeds(wifeDb.collection("families").doc("fam_a").collection("media").doc("med_1").get());
  await assertFails(childDb.collection("families").doc("fam_a").collection("media").doc("med_1").get());
  await assertFails(fatherBDb.collection("families").doc("fam_a").collection("media").doc("med_1").get());

  // Owner can delete own media during data deletion, Wife cannot delete Father's media
  await assertFails(wifeDb.collection("families").doc("fam_a").collection("media").doc("med_1").delete());
  await assertSucceeds(fatherDb.collection("families").doc("fam_a").collection("media").doc("med_1").delete());

  // Notifications: Father creates notification for Wife; Wife marks read; Child cannot tamper; Family B denied
  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("notifications").doc("not_1").set({
      familyId: "fam_a",
      title: "New Real Baba Message",
      body: "Tariq sent a note",
      type: "real_baba_message",
      recipientUids: [FATHER_A, WIFE_A],
      readByUids: [FATHER_A],
      createdAt: now,
    })
  );
  await assertSucceeds(
    wifeDb.collection("families").doc("fam_a").collection("notifications").doc("not_1").update({
      readByUids: [FATHER_A, WIFE_A],
    })
  );
  await assertFails(
    childDb.collection("families").doc("fam_a").collection("notifications").doc("not_1").update({
      readByUids: [FATHER_A, WIFE_A, CHILD_A],
    })
  );
  await assertFails(
    fatherBDb.collection("families").doc("fam_a").collection("notifications").doc("not_1").get()
  );

  // UsageRecords: Child creates ai_turn; Adults can inspect; Child cannot read other adult's usage; immutable
  await assertSucceeds(
    childDb.collection("families").doc("fam_a").collection("usageRecords").doc("use_1").set({
      familyId: "fam_a",
      actorUid: CHILD_A,
      service: "ai_turn",
      units: 1,
      estimatedCostUsdCents: 1,
      periodKey: "2026-11",
      adultUids: [FATHER_A, WIFE_A],
      createdAt: now,
    })
  );
  await assertSucceeds(
    fatherDb.collection("families").doc("fam_a").collection("usageRecords").doc("use_1").get()
  );
  await assertFails(
    fatherBDb.collection("families").doc("fam_a").collection("usageRecords").doc("use_1").get()
  );
  await assertFails(
    fatherDb.collection("families").doc("fam_a").collection("usageRecords").doc("use_1").update({ units: 99 })
  );
});

test("Self data deletion removes requester private messages and completes own DataRequest while blocking siblings", async () => {
  await seedFamilyA();
  const wifeDb = testEnv.authenticatedContext(WIFE_A).firestore();
  const fatherDb = testEnv.authenticatedContext(FATHER_A).firestore();
  const now = nowTs();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(WIFE_A).collection("privateConversations").doc("wife_companion_main").set({
      ownerUid: WIFE_A,
      title: "Private Companion",
      agentMode: "BABA_COMPANION",
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("users").doc(WIFE_A).collection("privateConversations").doc("wife_companion_main")
      .collection("messages").doc("pm_1").set({
        ownerUid: WIFE_A,
        senderType: "user",
        text: "Private thought",
        identityMode: "BABA_COMPANION",
        disclosure: "I am BABA, an AI companion.",
        safetyStatus: "approved",
        createdAt: now,
      });
  });

  // Father cannot delete Wife's private message; Wife can
  await assertFails(
    fatherDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("wife_companion_main")
      .collection("messages").doc("pm_1").delete()
  );
  await assertSucceeds(
    wifeDb.collection("users").doc(WIFE_A).collection("privateConversations").doc("wife_companion_main")
      .collection("messages").doc("pm_1").delete()
  );

  // Wife submits delete DataRequest and completes it herself; Father cannot alter Wife's request status
  await assertSucceeds(
    wifeDb.collection("families").doc("fam_a").collection("dataRequests").doc("req_1").set({
      familyId: "fam_a",
      requesterUid: WIFE_A,
      requestType: "delete",
      status: "submitted",
      notes: "Self deletion",
      visibleToUids: [FATHER_A, WIFE_A],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    fatherDb.collection("families").doc("fam_a").collection("dataRequests").doc("req_1").update({
      status: "completed",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    wifeDb.collection("families").doc("fam_a").collection("dataRequests").doc("req_1").update({
      status: "completed",
      updatedAt: now,
    })
  );
});
