# Research — Cloud leg: blocked API, cheapest model, Interactions API

*Researched 2026-08-27 against primary sources (Google AI docs, Firebase Android release notes, live API probes with the project's own API key + App Check debug token).*

## TL;DR

1. **The first cloud call fails because `aiplatform.googleapis.com` (Agent Platform API) is not enabled** on `edge-agent-lab` — not because of the model id. Fix: enable it at
   `https://console.developers.google.com/apis/api/aiplatform.googleapis.com/overview?project=edge-agent-lab`.
   (Alternative: switch AI Logic to the "Gemini Developer API" service in Firebase console settings, which doesn't require this API — but the project is currently set up on the Agent Platform path.)
2. **Cheapest usable model: `gemini-2.5-flash-lite`** ($0.10/$0.40 per 1M in/out). Best quality-per-price for tool calling: `gemini-3.1-flash-lite` ($0.25/$1.50). Everything is **free-tier eligible**, so Playground testing costs nothing either way.
3. **Interactions API is a Gemini-API-only surface (GA June 2026).** Firebase AI Logic / `firebase-ai` Kotlin does not expose it — adopting it means bypassing Firebase's server-side key proxy, which contradicts ADR 0002's security posture. Not for v1.

## What the live probes showed

With the API key from `google-services.json` + a debug App Check token (exchanged via `exchangeDebugToken` — which also proves the App Check console registration works):

- `generativelanguage.googleapis.com` (plain Gemini API) → **403 blocked** by key restrictions (key is scoped to Firebase AI Logic only — as intended).
- `firebasevertexai.googleapis.com` (AI Logic proxy) with App Check → App Check **accepted**, then every model returned:
  > `PERMISSION_DENIED: Agent Platform API has not been used in project edge-agent-lab before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/aiplatform.googleapis.com/overview?project=edge-agent-lab`

  The failure is identical for all model ids, so `gemini-2.5-flash` availability is still unverified until the API is enabled.

## Model landscape & pricing (Paid tier, per 1M tokens; source: ai.google.dev/gemini-api/docs/pricing)

| Model | Input | Output | Notes |
|---|---|---|---|
| `gemini-2.5-flash-lite` | $0.10 | $0.40 | cheapest overall; "fastest, most budget-friendly multimodal in the 2.5 family" |
| `gemini-3.1-flash-lite` | $0.25 | $1.50 | "frontier-class performance at a fraction of the cost"; optimized for high-volume agentic tasks |
| `gemini-2.5-flash` | $0.30 | $2.50 | current `DEFAULT_CLOUD_MODEL` |
| `gemini-3.5-flash-lite` | $0.30 | $2.50 | fastest 3.5 |
| `gemini-3.6-flash` | $0.75 | $3.75 | promo pricing through Dec 31 2026 ($1.50/$7.50 in 2027) |
| `gemini-3.7-flash` | $0.75 | $3.75 | latest/most capable Flash; same promo pricing |
| `gemini-3.5-flash` | $1.50 | $9.00 | legacy 3.5 flagship |

All text models above have a **free tier** ("Free of charge" with rate limits), so dev/test on the Playground costs $0 regardless of choice. Free-tier content may be used for product improvement (paid tier: not used).

**Recommendation:** keep `gemini-2.5-flash` for the first verified call (cheapest 2.5-tier with strong tool calling), then consider `gemini-3.1-flash-lite` as the default if its tool calling holds up on the seeded probes — 5–7× cheaper than 3.6/3.7 Flash, and those add nothing the Playground's contract needs.

## Interactions API (source: ai.google.dev/gemini-api/docs/interactions)

- GA since June 2026; Google's recommended interface for all new Gemini work. `generateContent` is now "legacy but fully supported."
- Adds: server-side conversation state (`previous_interaction_id`, lower multi-turn cost via cache hits), observable execution steps, `background=true` for long tasks, one endpoint for models *and* managed agents.
- Storage: interactions retained 55 days (paid) / 1 day (free) by default; `store=false` opts out (but disables state/background features).

**Why we're not adopting it now:** it exists only on the Gemini Developer API / Agent Platform surfaces. The `firebase-ai` Android SDK (17.16.0 is latest; we're on 17.12.1) has no Interactions surface — its newest AI-Logic additions are TemplateChat "Chat interactions" (server prompt templates), which is a different feature. Using the Interactions API from the app would mean shipping a Gemini API key in the client or building a backend proxy — both contradict ADR 0002 (Firebase AI Logic chosen precisely so the key stays server-side). Revisit if the Playground grows into a managed-agent host; until then, `Chat`/`generateContent` remains fully supported.

## Firebase AI Logic backend rename (source: firebase.google.com/support/release-notes/android)

Vertex AI was renamed **Gemini Enterprise Agent Platform**; `GenerativeBackend.vertexAI` is deprecated in favor of `GenerativeBackend.agentPlatform` (SDK #8437; also changes the default location to `global`). Our code uses `GenerativeBackend.googleAI()`, which is unaffected by the rename but routes through the same proxy — which is why the project needs `aiplatform.googleapis.com` enabled.

## Billing: which provider path you're on decides free vs paid (source: firebase.google.com/docs/ai-logic/pricing)

Enabling `aiplatform.googleapis.com` is itself free, but it's only demanded because the project routes via the **Agent Platform (Vertex AI)** provider:

| AI Logic provider | Billing | Free tier |
|---|---|---|
| **Gemini Developer API** (switchable in AI Logic settings) | No Cloud Billing — project stays on no-cost Spark plan | ✅ free tier for the Flash models, rate-limited |
| **Agent Platform (Vertex AI)** — current path | Blaze (Cloud Billing) + prepayment for new billing accounts | ❌ pay-as-you-go only |

Real-world cost even on Blaze is trivial (a Playground turn ≈ 2–4K tokens ≈ fractions of a cent), but the free tier via the Gemini Developer API provider is the cleanest for dev/testing. Note free tier = "limited access to certain models"; `2.5-flash`/`flash-lite` are safely included.

## Immediate next steps

1. Anton: enable `aiplatform.googleapis.com` for `edge-agent-lab` (URL above), wait a few minutes for propagation.
2. Re-probe `gemini-2.5-flash:generateContent` (or just run the Playground's first cloud turn).
3. If it resolves, proceed with the seeded-probe verification from the handoff; evaluate `gemini-3.1-flash-lite` tool calling after.
