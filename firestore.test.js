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

test("Unauthenticated user cannot read or write watchlists", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/watchlists`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/watchlists`).doc("wl_1").set({
      userId: ALICE_UID,
      name: "Tech",
      pinnedToTop: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user can create, list, and update own watchlist", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const wlRef = aliceDb.collection(`users/${ALICE_UID}/watchlists`).doc("wl_main");
  const now = new Date();

  await assertSucceeds(
    wlRef.set({
      userId: ALICE_UID,
      name: "Core Watchlist",
      pinnedToTop: true,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/watchlists`).get());
  await assertSucceeds(
    wlRef.update({
      name: "Core Leaders",
      updatedAt: new Date(),
    })
  );
});

test("Cross-user access to watchlists and preferences is denied", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection(`users/${BOB_UID}/watchlists`).doc("bob_wl").set({
      userId: BOB_UID,
      name: "Bob Holdings",
      pinnedToTop: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/watchlists`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/watchlists`).doc("bob_wl").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/watchlists`).doc("bob_wl").delete()
  );
});

test("Shadow update with ghost field or invalid assetClass fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const wlRef = aliceDb.collection(`users/${ALICE_UID}/watchlists`).doc("wl_1");
  const now = new Date();
  await assertSucceeds(
    wlRef.set({
      userId: ALICE_UID,
      name: "Tech",
      pinnedToTop: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    wlRef.update({
      name: "Tech Updated",
      isAdmin: true,
      updatedAt: new Date(),
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/watchlist_items`).doc("item_1").set({
      userId: ALICE_UID,
      watchlistId: "wl_1",
      symbol: "AAPL",
      displayName: "Apple Inc.",
      assetClass: "INVALID_CLASS",
      pinnedToTop: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/watchlist_items`).doc("item_orphan").set({
      userId: ALICE_UID,
      watchlistId: "missing_wl",
      symbol: "AAPL",
      displayName: "Apple Inc.",
      assetClass: "STOCK",
      pinnedToTop: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/watchlist_items`).doc("item_valid").set({
      userId: ALICE_UID,
      watchlistId: "wl_1",
      symbol: "AAPL",
      displayName: "Apple Inc.",
      assetClass: "STOCK",
      pinnedToTop: true,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Preferences validate allowed refresh intervals and theme modes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const prefRef = aliceDb.collection(`users/${ALICE_UID}/preferences`).doc("app");
  const now = new Date();

  await assertFails(
    prefRef.set({
      userId: ALICE_UID,
      themeMode: "MARKET_AUTO",
      marketOpenMinutesEt: 570,
      marketCloseMinutesEt: 960,
      refreshIntervalSeconds: 7,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    prefRef.set({
      userId: ALICE_UID,
      themeMode: "MARKET_AUTO",
      marketOpenMinutesEt: 570,
      marketCloseMinutesEt: 960,
      refreshIntervalSeconds: 30,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Price alerts enforce ownership, validation, and safe field updates", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertFails(unauthDb.collection(`users/${ALICE_UID}/price_alerts`).get());

  const alertRef = aliceDb.collection(`users/${ALICE_UID}/price_alerts`).doc("alert_nvda");
  await assertSucceeds(
    alertRef.set({
      userId: ALICE_UID,
      symbol: "NVDA",
      displayName: "NVIDIA Corp.",
      condition: "ABOVE",
      targetPrice: 150.5,
      enabled: true,
      triggered: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Trigger update succeeds
  await assertSucceeds(
    alertRef.update({
      triggered: true,
      lastTriggeredPrice: 151.2,
      updatedAt: new Date(),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection(`users/${ALICE_UID}/price_alerts`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/price_alerts`).doc("alert_nvda").delete());

  // Invalid condition or targetPrice fails
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/price_alerts`).doc("bad_alert").set({
      userId: ALICE_UID,
      symbol: "AAPL",
      displayName: "Apple Inc.",
      condition: "EQUALS",
      targetPrice: 220,
      enabled: true,
      triggered: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("FCM tokens and portfolio holdings enforce owner CRUD and reject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const tokenRef = aliceDb.collection(`users/${ALICE_UID}/fcm_tokens`).doc("android_primary");
  await assertSucceeds(
    tokenRef.set({
      userId: ALICE_UID,
      token: "fcm_token_sample_android_1234567890",
      platform: "android",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/fcm_tokens`).get());

  const holdingRef = aliceDb.collection(`users/${ALICE_UID}/portfolio_holdings`).doc("pos_spy");
  await assertSucceeds(
    holdingRef.set({
      userId: ALICE_UID,
      symbol: "SPY",
      displayName: "SPDR S&P 500 ETF",
      assetClass: "ETF",
      shares: 12.5,
      avgCostBasis: 540.25,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field rejected on update
  await assertFails(
    holdingRef.update({
      shares: 15,
      isAdminOverride: true,
      updatedAt: new Date(),
    })
  );

  // Valid update passes
  await assertSucceeds(
    holdingRef.update({
      shares: 15,
      avgCostBasis: 545.0,
      updatedAt: new Date(),
    })
  );

  await assertFails(bobDb.collection(`users/${ALICE_UID}/portfolio_holdings`).get());
});
