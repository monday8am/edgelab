# Research — Cloud leg: provider path, model choice, Interactions API

*Researched 2026-08-27 (Google AI docs, Firebase Android release notes, live API probes). Updated 2026-08-28: the cloud leg is live.*

## Provider path: Gemini Developer API

AI Logic offers two provider paths, and the choice decides billing:

| AI Logic provider | Billing | Free tier |
|---|---|---|
| **Gemini Developer API** — our path | No Cloud Billing; project stays on Spark | ✅ free tier for the Flash models, rate-limited |
| Agent Platform (Vertex AI) | Blaze + prepayment for new billing accounts | ❌ pay-as-you-go only |

The project started on Agent Platform and every call failed with `PERMISSION_DENIED: Agent Platform API has not been used in project edge-agent-lab`. Enabling `aiplatform.googleapis.com` would have fixed it but commits the project to Blaze with no free tier, so we switched providers instead. `GenerativeBackend.googleAI()` in `FirebaseAiChatFactory` is the matching backend.

**Two consequences for the code, both easy to get wrong:**

- Function responses must use `role = "user"`. `role = "function"` is an Agent-Platform-ism the Developer API rejects outright; a `FunctionResponsePart` is what marks the turn as a tool result, not the role string. **If the provider is ever switched back, this line flips back too.**
- `GenerativeBackend.vertexAI` is deprecated in favour of `agentPlatform` after the Vertex → "Gemini Enterprise Agent Platform" rename (SDK #8437, also defaults location to `global`). We're unaffected while on `googleAI()`.

Also established by the probes: the API key in `google-services.json` is correctly scoped — direct `generativelanguage.googleapis.com` calls return 403, only the AI Logic proxy works.

## Model pricing (Paid tier, per 1M tokens; source: ai.google.dev/gemini-api/docs/pricing)

| Model | Input | Output | Notes |
|---|---|---|---|
| `gemini-2.5-flash-lite` | $0.10 | $0.40 | cheapest overall |
| `gemini-3.1-flash-lite` | $0.25 | $1.50 | Google positions it at high-volume agentic work |
| `gemini-2.5-flash` | $0.30 | $2.50 | previous default |
| `gemini-3.5-flash-lite` | $0.30 | $2.50 | **current `DEFAULT_CLOUD_MODEL`** |
| `gemini-3.6-flash` | $0.75 | $3.75 | promo pricing through Dec 31 2026 ($1.50/$7.50 after) |
| `gemini-3.7-flash` | $0.75 | $3.75 | latest Flash; same promo pricing |
| `gemini-3.5-flash` | $1.50 | $9.00 | legacy 3.5 flagship |

All of these are free-tier eligible on our provider path, so Playground dev/testing costs $0 whichever we pick. (Free-tier content may be used for product improvement; paid-tier content isn't.)

**Open question.** `gemini-3.5-flash-lite` was picked when the first turn was made to work and it verifiably does the job, but it isn't what this table argues for: `gemini-3.1-flash-lite` is 1.7× cheaper on output for a model aimed squarely at agentic use. Costs nothing while we're on the free tier — worth an A/B on the seeded probes before we ever aren't.

## Interactions API — out of scope for v1

GA since June 2026 and Google's recommended interface for new Gemini work (`generateContent` is now "legacy but fully supported"). It adds server-side conversation state (`previous_interaction_id`, cheaper multi-turn via cache hits), observable execution steps, `background=true` for long tasks, and one endpoint for models *and* managed agents.

We can't reach it without giving up what ADR-0002 bought us. It exists only as a direct Gemini API / Agent Platform surface, and `firebase-ai` (17.12.1 via `firebase-bom` 34.14.1; 17.16.0 is latest) doesn't expose it — its similarly-named TemplateChat "Chat interactions" is server prompt templates, a different feature. Using it would mean a client-side key or a backend proxy, which is the thing AI Logic was chosen to avoid. Revisit if the Playground grows into a managed-agent host.
