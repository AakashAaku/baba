# BABA — AI-Powered Family Connection Platform

Android (Kotlin + Jetpack Compose + Material 3) implementation backed by Firebase Authentication (Google Sign-In via Credential Manager), Cloud Firestore (multi-tenant zero-trust security rules), and Firebase AI Logic (`gemini-3.8-flash` with App Check).

## Core Architecture & Truthfulness Safeguards
- **Hybrid Identity Model with Explicit Disclosure**:
  - **Digital Baba (AI)**: Grounded strictly in Father-approved identity attributes, provenance-tagged memories, and approved guidance with `source_references`. Missing or conflicting answers trigger **Live Guidance** escalation rather than inventing opinions.
  - **BABA Companion (AI)**: Independent AI support for Wife (owner-only private conversations inaccessible to Father/Admin), Storyland, and age-band Learning Academy.
  - **Real Baba (Human)**: Genuine human text & local voice-note recordings (synthetic voice cloning is disabled in v1; audio never auto-plays).
- **Six Logical AI Agents (`com.example.ai`)**: Father Identity Agent, Family Companion Agent, Children Learning Agent, Storytelling Agent, strict permission-filtering Memory Agent, and deterministic Safety & Policy Agent.

## Local Testing & Verification

1. **Tier 1 — Firestore Security Rules Tests (Node + Emulator)**:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
2. **Tier 2 & Unit / UI CUJ Tests (Robolectric + Emulator)**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. **Interactive App Preview**:
   Run via Android Studio / AI Studio Streaming Emulator using Google Sign-In (`CredentialManager`).
