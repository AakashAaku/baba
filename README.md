# BABA — AI-Powered Family Connection Platform

BABA connects families separated across borders (such as a father/husband working abroad, wife, and children) with strict **AI identity transparency**, **zero-trust multi-tenant privacy**, **6-language localization (before and after sign-in)**, and **deterministic child-safety guardrails**.

---

## Architecture & Repository Layout

```text
.
├── app/                         # Android Kotlin + Jetpack Compose + Material 3 client
│   ├── google-services.json     # Firebase project client config (replace when forking)
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── ai/          # 6 logical agents, Gemini Firebase AI Logic provider, safety filters
│       │   │   ├── core/        # Auth, App Check, media validator, locale & timezone helpers
│       │   │   ├── data/        # Firestore repositories & snapshot listeners
│       │   │   ├── domain/      # Strongly-typed models & identity/role enums
│       │   │   └── ui/          # Screens (Auth, Setup, Father, Wife, Kids, Hub, Governance) & ViewModel
│       │   └── res/
│       │       ├── drawable-nodpi/
│       │       ├── values/      # English (Default) strings + firebase_applet_config.xml
│       │       ├── values-ne/   # Nepali (नेपाली)
│       │       ├── values-hi/   # Hindi (हिन्दी)
│       │       ├── values-bn/   # Bengali / Bangladeshi (বাংলা)
│       │       ├── values-ur/   # Urdu / Pakistani (اردو, RTL)
│       │       └── values-ar/   # Arabic (العربية, RTL)
│       └── test/                # JVM unit tests, Robolectric Compose CUJs, emulator rule tests
├── backend/                     # Optional Node.js 22+ TypeScript REST server (Cloud Run ready)
├── firestore.rules              # Zero-trust multi-tenant Cloud Firestore security rules
├── firestore.test.js            # Node rules-unit-testing suite against Firestore Emulator
├── firebase-blueprint.json      # Schema blueprint matching Firestore collections & rules
└── firebase.json                # Local Firebase Emulator Suite configuration
```

---

## Core Trust, Privacy & Localization Capabilities

- **Three Explicit Identity Modes (Never Faked)**
  - **Digital Baba (AI):** Answers using strictly approved Father identity settings, provenance-tagged memories, and approved guidance (`source_references`). Missing or conflicting answers trigger a **Live Guidance** request to the real father rather than guessing.
  - **BABA Companion (AI):** Independent AI support for Wife (owner-only private conversations unreadable by Father/Admin) and age-banded Kids **Learning Academy** & **Storyland**.
  - **Real Baba (Human):** Verified human messages, scheduled video-call requests, check-ins, and recorded M4A voice notes (no synthetic voice cloning in v1; audio never auto-plays).
- **Pre-Login & Post-Login Multi-Language Support (6 Languages)**
  - Switchable **before sign-in** (carried seamlessly into `UserAccount.locale` upon Google Sign-In) and **after sign-in** from the top bar, Family Setup, or Safety & Privacy governance settings:
  - `en` English (Default) · `ne` Nepali · `hi` Hindi · `bn` Bengali (Bangladesh) · `ur` Urdu (Pakistan, RTL) · `ar` Arabic (RTL).
- **Separated-Family Daily Tools**
  - **Distance Bridge:** Live local times and dayparts for **Father Abroad** vs. **Home Family** across common diaspora timezones (`Asia/Kathmandu`, `Asia/Kolkata`, `Asia/Dhaka`, `Asia/Karachi`, `Asia/Dubai`, `Asia/Riyadh`, `Asia/Kuala_Lumpur`, `Europe/London`, `America/New_York`, `America/Los_Angeles`).
  - **Adult-Authorized Child Onboarding:** Children (`age_band` `5-8`, `9-12`, `13-17`) can only be created by an adult family member with recorded guardian consent.
  - **Deterministic Child Safety:** Multilingual pre-checks block prompt injection, cross-tenant snooping, child secrecy cues, and medical/legal emergencies before invoking the LLM.

---

## Prerequisites

- **JDK:** 11+ (AGP 9.1.1 / Kotlin 2.2.10)
- **Android SDK:** `compileSdk 36`, `minSdk 24`, `targetSdk 36`
- **Node.js:** `>= 22.0.0` (uses native `--experimental-strip-types`)
- **Firebase CLI:** Installed (`npx firebase` / global `firebase-tools`) for local Auth + Firestore emulators and rules deployment

---

## Forker & Developer Setup Guide

If you fork this repository or connect it to your own Firebase project, follow these steps in order:

### 1. Set a Unique Android `applicationId`
In `app/build.gradle.kts`, update `defaultConfig.applicationId` to your own unique reverse-domain package ID:
```kotlin
android {
    namespace = "com.example" // Keep unchanged so R class & directories stay intact
    defaultConfig {
        applicationId = "com.yourorg.baba.prod"
    }
}
```

### 2. Create & Configure Your Firebase Project
1. Create a Firebase project and register an **Android app** matching your `applicationId`.
2. **Generate Debug & Release Signing Fingerprints (SHA-1 & SHA-256)** and add them under **Firebase Console → Project Settings → Your Android App** (required for Google Sign-In / Credential Manager):
   ```bash
   # Debug keystore (for local builds)
   keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android

   # Release upload keystore (for Play Store / release builds)
   keytool -list -v -keystore /path/to/your-upload-key.jks -alias upload
   ```
3. Download the updated `google-services.json` from Firebase Console and replace `app/google-services.json`. Verify it contains both an Android OAuth client (`client_type: 1`) and Web OAuth client (`client_type: 3`, used by `default_web_client_id`).
4. **Enable Firebase Authentication:** Turn on **Google** as a Sign-In provider (anonymous auth is intentionally disabled).
5. **Provision Named Cloud Firestore Database:**
   - The Android client reads the database ID from `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`).
   - Either create a Firestore database matching that ID in your project, or update `firestore_database_id` (and `firebase.json`) to match your database ID (e.g., `(default)` or your named database).
6. **Enable Firebase AI Logic:** Enable the Firebase AI Logic / Gemini Developer API in Firebase Console (no client-side `GEMINI_API_KEY` is needed on Android; requests authenticate via Firebase App Check).
7. **Configure Firebase App Check:**
   - **Debug / UAT builds:** Run the app, copy the `DebugAppCheckProvider` UUID printed in Logcat, and register it under **Firebase Console → App Check → Manage debug tokens** (or pass `FIREBASE_APPCHECK_DEBUG_TOKEN` via launch intent extra).
   - **Release builds:** Set your reCAPTCHA Enterprise site key in `app/src/main/res/values/firebase_applet_config.xml` (`recaptcha_site_key`) and/or enable **Play Integrity** in Firebase Console.

### 3. Update Platform Operator Email & Deploy Firestore Rules
1. Open `firestore.rules` and update the operator allowlist inside `isPlatformOperator()` from the template email to your operator email if needed.
2. Deploy rules to your Firebase project:
   ```bash
   firebase use <your-firebase-project-id>
   firebase deploy --only firestore:rules
   ```

> **Security Note on API Keys & Secrets:** Do **not** store API keys inside `local.properties` or commit keystores/secrets. Use Firebase AI Logic (App Check) on Android, AI Studio's **Secrets panel** when working inside AI Studio, and environment variables on Cloud Run for `/backend`.

---

## Running Automated Tests Locally

Run all three verification suites before any release or pull request:

### 1. Android Unit, Robolectric Compose CUJ & Emulator Integration Tests (23 tests)
Running Gradle inside `firebase emulators:exec` spins up isolated Auth (`127.0.0.1:9099`) and Firestore (`127.0.0.1:8085`) emulators automatically so `BabaRepositoryRuleTest` runs live against `firestore.rules`:
```bash
npx firebase emulators:exec --only auth,firestore "gradle :app:testDebugUnitTest --no-daemon"
```
*(If emulators are already running on ports `8085` and `9099`, you can run `gradle :app:testDebugUnitTest` directly.)*

### 2. Firestore Security Rules Suite (8 tests)
Verifies unauthenticated denial, cross-family isolation, Wife private conversation secrecy, child privilege boundaries, shadow-field tamper prevention, validated media rules, and self-erasure:
```bash
npx firebase emulators:exec --only firestore "node --test firestore.test.js"
```

### 3. Build Debug APK Verification
```bash
gradle :app:assembleDebug
```

### 4. Optional Backend API Suite & Local Server (`/backend`, 5 tests)
If deploying the optional Node.js REST service (Cloud Run / external integrations):
```bash
cd backend
npm test

# Start locally with optional Gemini API key
PORT=8080 BABA_AUTH_SECRET="dev-secret" GEMINI_API_KEY="your-key" npm start
```
See `backend/README.md` and `backend/openapi.yaml` for endpoint contracts.

---

## UAT Demo Walkthrough Checklist

Use two Google accounts (or two Android devices / emulators) to verify every core user journey:
1. **Pre-Login Localization:** On the welcome screen, tap any of the 6 language chips (`English`, `नेपाली`, `हिन्दी`, `বাংলা`, `اردو`, `العربية`), verify live UI & RTL switching, then sign in with Google as **Father** (`Create Family Workspace`).
2. **Invite Wife & Co-Parent:** Under **Father → Family & Kids**, invite the second Gmail address as **Wife** and share or copy the single-use expiring invite.
3. **Accept Invite & Set Diaspora Timezones:** Sign in with the Wife Gmail account, accept the pending invitation, and open **Hub → Distance Bridge** to set Home vs. Father Abroad timezones, send a 1-tap Safe Check-In, or schedule a Video Call.
4. **Digital Baba & Live Guidance Escalation:**
   - Have Father configure **Digital Baba Identity**, approve a shared **Family Memory** (text or photo), and answer a **Live Guidance** topic.
   - Switch to **Kids** (`Add Child` under Father → **Switch to Child View**), ask a question covered by approved guidance (grounded response with `source_references`), and ask a topic with no guidance (escalates to Live Guidance instead of inventing Father's stance).
   - Test multilingual child-secrecy blocking (e.g., Nepali/Hindi/Urdu "don't tell mom/dad").
5. **Genuine Voice Notes & Privacy Isolation:**
   - Record a **Real Baba** or **Wife Shared Planning** voice note (grants `RECORD_AUDIO` permission) and verify manual play/pause.
   - Open **Wife → Companion AI** and verify private messages are strictly inaccessible from Father's workspace.
6. **Governance & Erasure:** Under **Safety**, export a scoped JSON privacy archive, test member suspend/reactivate, or trigger self-data erasure.

---

## Production Release Checklist (Google Play Store)

1. **Signing & Secrets:** Provide release upload signing credentials via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or export AAB via AI Studio, and register **Play App Signing** + **Upload Key** SHA-1/SHA-256 in Firebase Console.
2. **Release Build Hardening:** In `app/build.gradle.kts`, enable `isMinifyEnabled = true` and `isShrinkResources = true` for `release`, test thoroughly with ProGuard/R8 rules (`app/proguard-rules.pro`), and increment `versionCode` / `versionName`.
3. **Firebase Production Readiness:** Deploy `firestore.rules`, verify any required Firestore composite indexes during multi-user QA, enable **Play Integrity** in Firebase App Check, and enforce App Check for Firestore & AI Logic.
4. **Google Play Policy & Families Compliance:** Complete Play Console **Data Safety** (Auth, encrypted transport, voice/photo storage, in-app self-erasure), publish a **Privacy Policy URL**, and review **Google Play Families Policy** disclosures if targeting children alongside parents.
