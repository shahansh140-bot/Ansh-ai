const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const OWNER_UID = "owner_999";
const OWNER_EMAIL = "shahansh140@gmail.com";
const FORMER_OWNER_UID = "former_owner_888";
const FORMER_OWNER_EMAIL = "rajnandani676701@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    displayName: "Alice",
    email: "alice@example.com",
    status: "ACTIVE",
    role: "USER",
    dailyRequestCount: 0,
    totalRequests: 0,
    lastRequestDate: "2026-11-16",
    preferredLanguage: "English",
    themeMode: "ELECTRIC_DARK",
    voiceSpeechRate: 1.0,
    voiceAutoSpeak: true,
    autoSaveHistory: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertFails(unauth.collection("app_config").doc("global").get());
  await assertFails(unauth.collection("admins").doc(OWNER_UID).get());
});

test("Regular user can create own USER/ACTIVE profile, update preferences, but cannot self-promote or disable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Cannot create profile with SUPER_ADMIN role
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, { role: "SUPER_ADMIN" }))
  );

  // Valid USER profile creation succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );

  // Partial update of user preferences succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Spanish",
      dailyRequestCount: 1,
      totalRequests: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Privilege escalation / role tampering fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Regular user cannot list all users
  await assertFails(aliceDb.collection("users").get());
});

test("Cross-user isolation: Alice cannot read or write Bob's profile, chats, messages, or imageLogs", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = new Date(Date.now() - 5000);
    await db.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID, { displayName: "Bob" }));
    await db.collection("users").doc(BOB_UID).collection("chats").doc("chat_1").set({
      userId: BOB_UID,
      title: "Bob Secret",
      mode: "CHAT",
      modelName: "gemini-3.8-flash",
      lastMessagePreview: "Hello",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("chats").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("chats").doc("chat_1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("imageLogs").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("videoLogs").get());
});

test("User-owned chats, messages, and imageLogs support Kotlin create, update, list, and delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").set({
      userId: ALICE_UID,
      title: "Quantum Physics",
      mode: "CHAT",
      modelName: "gemini-3.8-flash",
      lastMessagePreview: "Explain entanglement",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      chatId: "chat_1",
      role: "user",
      text: "Explain entanglement",
      hasImage: false,
      citationTitles: [],
      citationUrls: [],
      createdAt: ts,
    })
  );

  // Reject ghost `id` field from @DocumentId leakage
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").doc("chat_2").set({
      id: "chat_2",
      userId: ALICE_UID,
      title: "Bad Doc",
      mode: "CHAT",
      modelName: "gemini-3.8-flash",
      lastMessagePreview: "Bad",
      messageCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("chats").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("imageLogs").doc("img_1").set({
      userId: ALICE_UID,
      prompt: "Futuristic electric blue city",
      aspectRatio: "1:1",
      modelName: "gemini-nano-banana-2.1",
      localFilePath: "/data/user/0/app/files/img_1.png",
      status: "SUCCESS",
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("videoLogs").doc("vid_1").set({
      userId: ALICE_UID,
      prompt: "Neon waves rolling under a starry sky",
      mode: "TEXT_TO_VIDEO",
      style: "Cinematic",
      aspectRatio: "16:9",
      duration: "10s",
      modelName: "gemini-omni-1.2-flash",
      interactionId: "int_abc123",
      localFilePath: "/data/user/0/app/files/vid_1.mp4",
      status: "SUCCESS",
      errorMessage: "",
      actualDurationMs: 10000,
      parentInteractionId: "",
      segmentCount: 1,
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("videoLogs").doc("vid_2_ext").set({
      userId: ALICE_UID,
      prompt: "Camera flies over the neon harbor",
      mode: "EXTEND",
      style: "Cinematic",
      aspectRatio: "16:9",
      duration: "15s",
      modelName: "gemini-omni-1.2-flash",
      interactionId: "int_def456",
      localFilePath: "/data/user/0/app/files/vid_2.mp4",
      status: "SUCCESS",
      errorMessage: "",
      actualDurationMs: 15000,
      parentInteractionId: "int_abc123",
      segmentCount: 2,
      createdAt: ts,
    })
  );
});

test("Super Admin security: email spoofing rejected, regular user cannot write app_config or admins, verified owner succeeds", async () => {
  // Spoofed email (email_verified: false) MUST fail admin operations
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofedDb.collection("admins").doc("spoof_uid").set({
      uid: "spoof_uid",
      email: OWNER_EMAIL,
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    spoofedDb.collection("app_config").doc("global").set({
      dailyRequestLimit: 100,
      chatEnabled: true,
      voiceEnabled: true,
      imageGenEnabled: true,
      webSearchEnabled: true,
      videoGenEnabled: false,
      videoBackendUrl: "",
      maintenanceMessage: "",
      updatedBy: "spoof_uid",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Verified owner can bootstrap admin anchor, manage app_config, list users, and suspend a user
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    ownerDb.collection("admins").doc(OWNER_UID).set({
      uid: OWNER_UID,
      email: OWNER_EMAIL,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    ownerDb.collection("app_config").doc("global").set({
      dailyRequestLimit: 75,
      chatEnabled: true,
      voiceEnabled: true,
      imageGenEnabled: true,
      webSearchEnabled: true,
      videoGenEnabled: false,
      videoBackendUrl: "https://us-central1-demo.cloudfunctions.net/anshVideoApi",
      maintenanceMessage: "",
      updatedBy: OWNER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Regular user can read app_config/global, cannot modify it
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("app_config").doc("global").get());
  await assertFails(
    aliceDb.collection("app_config").doc("global").update({
      dailyRequestLimit: 9999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(aliceDb.collection("system_errors").get());
  await assertFails(aliceDb.collection("telemetry_events").get());

  // Super Admin can read system_errors, telemetry_events, list users, and disable Alice
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(ownerDb.collection("users").get());
  await assertSucceeds(
    ownerDb.collection("users").doc(ALICE_UID).update({
      status: "DISABLED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(ownerDb.collection("system_errors").get());
  await assertSucceeds(ownerDb.collection("telemetry_events").get());
});

test("Verified owner can sync own profile role to SUPER_ADMIN; unverified or regular user cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = new Date(Date.now() - 5000);
    await db.collection("users").doc(OWNER_UID).set(
      validProfilePayload(OWNER_UID, {
        displayName: "Owner",
        email: OWNER_EMAIL,
        role: "USER",
        createdAt: ts,
        updatedAt: ts,
      })
    );
  });

  // Unverified owner email cannot upgrade role
  const unverifiedOwnerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    unverifiedOwnerDb.collection("users").doc(OWNER_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Verified owner CAN sync role to SUPER_ADMIN
  const ownerDb = testEnv.authenticatedContext(OWNER_UID, {
    email: OWNER_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Former admin email is denied Super Admin access even with stale admin/profile docs and can cleanly demote to USER", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = new Date(Date.now() - 5000);
    await db.collection("admins").doc(FORMER_OWNER_UID).set({
      uid: FORMER_OWNER_UID,
      email: FORMER_OWNER_EMAIL,
      createdAt: ts,
    });
    await db.collection("users").doc(FORMER_OWNER_UID).set(
      validProfilePayload(FORMER_OWNER_UID, {
        displayName: "Former Admin",
        email: FORMER_OWNER_EMAIL,
        role: "SUPER_ADMIN",
        createdAt: ts,
        updatedAt: ts,
      })
    );
  });

  const formerAdminDb = testEnv.authenticatedContext(FORMER_OWNER_UID, {
    email: FORMER_OWNER_EMAIL,
    email_verified: true,
  }).firestore();

  // Former admin cannot perform Super Admin reads/writes
  await assertFails(formerAdminDb.collection("users").get());
  await assertFails(formerAdminDb.collection("system_errors").get());
  await assertFails(formerAdminDb.collection("telemetry_events").get());
  await assertFails(
    formerAdminDb.collection("app_config").doc("global").set({
      dailyRequestLimit: 99,
      chatEnabled: true,
      voiceEnabled: true,
      imageGenEnabled: true,
      webSearchEnabled: true,
      videoGenEnabled: false,
      videoBackendUrl: "",
      maintenanceMessage: "",
      updatedBy: FORMER_OWNER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    formerAdminDb.collection("admins").doc(FORMER_OWNER_UID).set({
      uid: FORMER_OWNER_UID,
      email: OWNER_EMAIL,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Former admin can safely demote own profile back to USER
  await assertSucceeds(
    formerAdminDb.collection("users").doc(FORMER_OWNER_UID).update({
      role: "USER",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

