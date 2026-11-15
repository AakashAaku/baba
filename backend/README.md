# BABA Server-Side Backend (`/backend`)

Standalone Node.js 22+ TypeScript REST service implementing BABA's server-side API contract (`openapi.yaml`), matching the Android client domain models and Firestore zero-trust policies.

## Implemented Endpoints
- `GET /api/v1/health` — Public health check
- `POST /api/v1/families` — Authenticated family workspace creation
- `POST /api/v1/invitations` — Adult-only expiring invitation creation (persists SHA-256 `tokenHash`)
- `POST /api/v1/invitations/:inviteId/accept` — Constant-time hash + verified email check
- `POST /api/v1/ai/respond` — Plan quota gate + UsageRecord metering + pre/post safety checks + authorized semantic retrieval + Gemini 2.5 Flash REST proxy (`GEMINI_API_KEY`)
- `POST /api/v1/media/validate` — MIME allowlist, `<= 420 KB` check, executable/XSS header blocking
- `POST /api/v1/privacy/export` — Requester-scoped JSON export archive
- `GET /api/v1/usage/summary` — Adult-only monthly quota & metering summary

## Running Locally & Testing
```bash
cd backend
npm test
npm start
```

## Cloud Run Deployment Notes
Set `PORT=8080`, `BABA_AUTH_SECRET`, and `GEMINI_API_KEY` in Cloud Run service environment variables.
