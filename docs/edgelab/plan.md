# EdgeLab Playground — Small Plan

> **Source of truth for the EdgeLab reactivation.** Produced by a `/grill-with-docs` session (2026-08-15). The decisions behind this plan are recorded in `CONTEXT.md` (Decisions) and `docs/adr/0001`, `0002`. The higher-level roadmap lives in `roadmap.md`; this is the concrete phased plan.

## The one job

EdgeLab is an on-device function-calling **Playground** for Android devs. A dev supplies one or more fake tools (**Probes**), sends prompts to a model, and reads the **Trace** — an annotated transcript of how the model called the tools and integrated their results. Not a model marketplace, not a multi-engine benchmark factory. One clear aspect: does function calling work, on this model, on this phone.

## Mode priority (ADR-0001)

1. **Playground** — primary mode. The thing the app is for.
2. **Test Suite** — secondary. The current app's shape (predefined scenarios, pass/fail); kept for credibility and as a fallback, lightly demoted in the nav.
3. **Benchmark** — opt-in *side effect* of consenting runs, not a mode the dev enters.

## Architecture constraints (ADR-0002)

- **LiteRT-LM is the sole *on-device* backend.** No llama.cpp / MediaPipe LLM / MNN. Chosen because litert-lm makes function-calling testing easy. "Sole" means on-device only — a **cloud** backend is permitted for the onboarding leg.
- **Only `.litertlm` artifacts run on-device.** "Add Gemma 4 on-device" waits on a `.litertlm` artifact existing. Non-litert models are a *different* project's conversion problem.
- **Model Catalog**: curated, size-bounded default list (`ModelCatalog.ALL_MODELS`) + bring-your-own via the dev's HuggingFace account. Both gated to `.litertlm`.

## Reuse — what already exists (don't rebuild)

The Probe machinery is **built and tested** in `:agent` / `:data`:

- `ToolHandler` / `OpenApiToolHandler` — fake tools that record calls and return mock responses. *This is the Playground's Probe engine.*
- `ToolCall(name, args, timestamp)` — the unit of evidence in a Trace.
- `ToolSpecification` / `FunctionSpec(name, description, parameters, mock)` + `mockToolResponses` — `@Serializable`, OpenAI-style. *This is the Probe authoring data model, verbatim.*
- `ToolHandlerFactory.createOpenApiHandler(spec, mockResponse)` — constructor; no new factory needed.
- `CopilotAgent.initialize(List<…>)` → `setToolsAndResetConversation` — multi-Probe sessions already supported.
- `data/.../tool_tests.json` — already-authored, correct `ToolSpecification` objects (`get_location`, `get_weather`, …). *Seed the preset library for free.*
- `ModelSelectorScreen` + `HuggingFaceOAuthManager` + `ModelDownloadManager` — the local-download + HF bring-your-own path, already working.

What **doesn't** exist: the in-app UI to *drive* the Probe engine interactively (define a Probe, send a prompt, read the Trace). That is the core of this plan.

## Probe & Trace contract

- A **Probe** = full `ToolSpecification`: name + description + input-parameter schema (JSON) + mock output. No artificial cap on count per session (engine supports N); the real bound is authoring tedium.
- **Authoring day-one**: preset library (1-tap add, seeded from `tool_tests.json`, tweak after add) + paste-import (paste an OpenAI-style tool JSON; `@Serializable` deserialization validates it). Structured property-by-property form builder and AI-assisted generation are *later iterations*.
- **Trace** = transcript + inline annotations, **no verdicts** (no pass/fail; the Test Suite owns verdicts). Per turn: dev prompt → model text → `[tool-call card: name, args pretty-printed with types ✅]` → `[mock tool output]` → model's next text, tagged `[used tool output]` / `[ignored tool output]`.

## Onboarding (option d — cloud-first, then local)

A new dev reaches the **Playground with zero download**: an online model runs the first prompts so the dev learns the game immediately. Once they understand, they download a local `.litertlm` and switch to on-device. The download fills time the dev is already engaged, and yields a cloud↔local comparison for free.

- **Cloud leg = Gemini via Firebase AI Logic (maintainer-funded), model `gemini-3.5-flash-lite`.** Google holds the API key server-side; the app ships no key and the dev supplies none. Model choice follows the price/quality analysis in `docs/edgelab/research-cloud-models-interactions-api.md`.
- **Honest about the comparison**: local AI is weaker today. That gap *is* why this app exists. No hiding it.
- **BYOK** is a later iteration.

**Binding decision (resolved at wire time, 2026-08-16).** The deferred "which Firebase surface" question is settled: **the `firebase-ai` SDK, not a hand-written Cloud Function.** Firebase AI Logic already does the one job a custom proxy would have done — keep the key off the client — so there is no server code to write, deploy, or fund. Rejected: a Functions REST/callable proxy (all the same key-hiding, plus a deployed service to own).

Correcting a premise this plan was written on: Firebase was **not** already a dependency. It appeared in the version catalog but was fully commented out in `app/explorer/build.gradle.kts`, and only as Crashlytics. `firebase-ai` is a genuinely new dependency.

**Maintainer setup — done (2026-08-27).** Firebase project `edge-agent-lab` exists with the AI Logic backend enabled; `google-services.json` is gitignored locally and injected in CI from a GitHub secret (`ci.yml`/`release.yml` decode it into `app/explorer/`); the google-services plugin auto-applies when the file exists (PR #108). App Check is installed (`ExplorerApp`): debug builds use the debug provider with the token registered in the console, release uses Play Integrity. Cloud leg verified end-to-end on device — findings and pricing research in `docs/edgelab/research-cloud-models-interactions-api.md`.

## V1 cut line — the Minimal Delight Loop

**Start small, preserve the whole plan.** v1 ships only the Minimal Delight Loop — one screen, one backend, one authoring path, one result — and every other piece is a shippable increment that composes on top.

### Phase 1 — v1 (Minimal Delight Loop)

- [x] Cloud Playground (Gemini via Firebase AI Logic) as the primary nav entry — default target, zero download
- [x] Preset Probe library, seeded from `data/.../tool_tests.json` (1-tap add) — *tweak-after-add still missing*
- [x] Send prompt → annotated Trace — transcript + tool-call cards + `[used/ignored tool output]` tags (heuristic judge in `ToolOutputUsage`)
- [x] Firebase surface for the Gemini proxy — resolved to the `firebase-ai` SDK (see Onboarding above); live and verified on device (2026-08-27), App Check included

**Out of scope for v1**: local-model download, paste-import, Benchmark, Test-Suite reskin.

### Phase 2 — Full Playground prime path

- [ ] Local-model download path (curated `.litertlm` catalog + HF bring-your-own) + cloud↔local switch
- [ ] Paste-import authoring (OpenAI-style tool JSON → validated `ToolSpecification`)

### Phase 3 — Test Suite demotion

- [ ] Lightly reskin Test Suite as a secondary nav entry; keep its code intact

### Phase 4 — Later / optional

- [ ] Benchmark: explicit per-upload "Share anonymous results" button (no background telemetry; the tap *is* consent)
- [ ] BYOK for the cloud leg (dev pastes their own Gemini/OpenAI key)
- [ ] Structured Probe schema-builder form (property-by-property editor)
- [ ] AI-assisted Probe generation (NL description → `ToolSpecification`)

## Carrying forward

This plan does **not** touch CyclingCopilot (see `docs/cyclingcopilot/plan.md` Reality Check). The two share architecture (`CONTEXT.md`) but have independent plans.