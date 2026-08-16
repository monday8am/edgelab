# EdgeLab Context

This file is a living glossary and context map for the EdgeLab project.
It is populated lazily by `/grill-with-docs` and `/domain-modeling` as terms and decisions are resolved.

## Project

Android research lab for on-device agentic AI. Two apps sharing a multi-module architecture:

- **EdgeLab** — model testing and tool-calling validation
- **CyclingCopilot** — on-device AI cycling assistant

## Glossary

_Core terms drawn from the codebase as of 2026-08-15. Expand lazily during grilling._

| Term | Meaning |
|------|---------|
| **EdgeLab** (app) | On-device function-calling playground for Android devs (`:app:explorer`, package `com.monday8am.edgelab.explorer`). Published to Play Store. |
| **CyclingCopilot** | On-device AI cycling assistant (`:app:copilot`, package `com.monday8am.edgelab.copilot`). Currently pre-MVP. |
| **Playground** | EdgeLab's primary mode. A dev supplies one or more Probes (default 1, no artificial cap — the engine already supports N tools) + prompts, picks a model, watches the model call Probes live. The "result" is the Trace. |
| **Test Suite** | EdgeLab's secondary mode. A predefined set of function-calling scenarios run against a model, showing pass/fail + tool-call traces. The current app's shape; kept for credibility/reproducibility. |
| **Benchmark** | EdgeLab's opt-in side effect. Anonymous per-model performance data from consenting devs, publishable online/in-app. Not a mode the dev enters; a consequence of Play + Test Suite runs. |
| **Model Catalog** | The set of models presented for discovery/selection. Two sources: a hand-curated, size-bounded default list (`ModelCatalog.ALL_MODELS`) and the dev's own HuggingFace account (via `HuggingFaceModelRepository`). Both gated to `.litertlm` artifacts by the inference backend. |
| **Probe** | The Playground's fake tool. Does no real work; defined by the dev; its only job is to record whether/how the model invoked it. Backed by the existing `OpenApiToolHandler` + `ToolSpecification` infra in `:agent`/`:data` (which already tracks calls and returns mock responses) — the UX to *drive* it interactively is what's missing. _Avoid_: mock tool, stub, fake tool, test tool. |
| **ToolCall** | The unit of evidence: the model invoked a named Probe with these args at this time. (Mirrors `agent.tools.ToolCall`.) _Avoid_: invocation, call. |
| **Trace** | The ordered sequence of ToolCalls (and the model's text turns) from one Playground session, possibly chaining across multiple Probes. The Trace *is* the Playground's result — the dev reads it to judge function calling, replacing the Test Suite's pass/fail report. _Avoid_: log, report, results. |
| **LocalInferenceEngine** | `:agent` interface for LiteRT-LM inference (`litertlm-jvm`). Android impl in `:core` (`litertlm-android`). |
| **CopilotAgent** | `:agent` orchestrator: question → tool calls → natural-language reply. `initialize(routeId)` registers tools; `ask(question, rideContext)` runs one turn. Built & tested, **not yet wired** to the UI. |
| **CyclingToolExecutor** | `:agent` dispatcher for 6 tools (`get_ride_status`, `get_segment_ahead`, `get_weather_forecast`, `get_route_alternatives`, `find_nearby_poi`, `get_rider_profile`). Reads segment/weather/route repos. |
| **RideContext** | Snapshot of rider state passed to tools (position, speed, distance, `elapsedMs`, `rideStartHour`). Defined/tested in `:agent`, **never built** by the running app yet. |
| **GpsSource / SimulatedGpsSource** | GPS abstraction in `:presentation`. `SimulatedGpsSource` advances fixed steps per tick (not yet using route `t` timestamps). |
| **3-layer ViewModel** | Interface + impl in `:presentation`; Android wrapper in `:app:*`. No Hilt/Koin. See `docs/patterns.md`. |
| **CoreDependencies / Dependencies** | Manual DI: `CoreDependencies` (`:core`) factory methods; per-app `Dependencies` objects wire them. |
| **Strade Bianche** | Bundled demo route: `route.json` (3207 coords with `lat`/`lng`/`alt`/`t`), `segments.json`, `weather.json`. |

## Decisions

_Hard-to-reverse decisions. Record as ADRs in `docs/adr/` and link here._

- **Single-model mode for CopilotAgent** — one model handles both tool-calling and response (memory/latency). Designed to accept a second engine later. Plan risk row reflects this.
- **Pure-Kotlin module boundary** — `:data`, `:agent`, `:presentation` have zero `android.*` imports; Android impls live in `:core`. Enforced across the codebase.
- **Manual DI, no frameworks** — `CoreDependencies` + `Dependencies`. AGENTS.md Anti-Patterns forbid Hilt/Koin/Dagger.
- **ImmutableList/ImmutableMap in all UiState** — `kotlinx.collections.immutable` for Compose stability.
- **EdgeLab mode priority: Playground primary, Test Suite secondary, Benchmark opt-in side effect** (ADR-0001). Evolve the current Test Suite app rather than reorient around it: keep Test Suite code intact, add Playground as the new primary nav entry, lightly reskin Test Suite to demote it. Reversing later would mean deleting shipped work. See `docs/adr/0001-edgelab-mode-priority.md`.
- **LiteRT-LM is the sole *on-device* inference backend; a cloud backend is permitted for onboarding** (ADR-0002). "Sole" = no llama.cpp/MediaPipe/MNN locally. A cloud path lets a dev reach the Playground with zero download and switch to a local `.litertlm` once they understand the game — a different category (cloud), in service of the on-device thesis. Only `.litertlm` artifacts run on-device; non-litert models are a *different* project's conversion problem. See `docs/adr/0002-litert-lm-sole-backend.md`.
- **Playground onboarding = cloud-first, then local download (option d).** A new dev starts on an online model so the Playground works with zero download; once they understand the game they download a local `.litertlm` and switch. Dissolves the first-run tension: the download fills time the dev is already engaged, and yields a cloud↔local comparison for free. We rejected (a) explanation+gate (re-introduces wall-of-text onboarding), (b) read-only Playground+model gate (risk of feeling stalled), (c) bundled tiny model (no good tiny `.litertlm` exists; the ~200MB ones are "completely dumb"; APK weight ages out fast). Honest about the comparison: local AI is weaker today — that gap *is* why this app exists.
- **Cloud onboarding leg = Gemini Flash via a Firebase Functions proxy (maintainer-funded).** The maintainer holds the API key behind a small Firebase surface; the app calls the proxy with zero dev-facing key. Gemini Flash: cheapest model with genuinely good tool-calling, and reuses the existing Firebase dependency (no new vendor). Rejected keyless/free-tier alone (function-calling free tiers largely need *someone's* key) and HF Inference (weakest function-calling quality — bad onboarding demo). BYOK is a later iteration. **Impl details deferred** — many ways to consume the service (Functions REST endpoint, Genkit, callable, etc.); pick the binding when the playground UI is wired.
- **Trace contract = transcript + inline annotations, no verdicts** (resolves P4 "results aren't clear"). Per turn: dev prompt → model text → [tool-call card: name, args pretty-printed with types ✅] → [mock tool output] → model's next text, tagged `[used tool output]` / `[ignored tool output]`. Foregrounds exactly what a function-calling probe cares about — did the model call, with what args, and did it integrate the returned value. Reuses `ToolCall` (name/args/timestamp already recorded) + the known mock output; only rendering is new. Rejected (a) raw transcript (a chat log, underwhelming) and (c) live `ValidationRule` per turn (re-imports the authoring tedium Q7 solved).
- **Benchmark = explicit per-upload action** (resolves the opt-in mechanic). After each Playground/Test run, a "Share anonymous results" button uploads one record (model id, device class, latency, tool-call success counts) only when the dev taps it. Zero background telemetry; the tap *is* the consent. Chosen for the privacy-sensitive Android-dev audience — zero silent phone-home, no data-safety-disclosure surface. Rejected (b) global opt-in + background upload (telemetry-under-another-name; distrust from the exact user base) and (c) opt-in + reviewable outbox (adds an outbox UI for marginal benefit over explicit per-upload).
- **Probe authoring = full ToolSpecification** — a Probe is authored as name + description + input-parameter schema (JSON) + mock output, reusing the Test Suite's `ToolSpecification`/`FunctionSpec`/`mockToolResponses` data model verbatim. Rejected name+description-only (can't probe argument correctness — `ToolCall.args` always empty) and name+description+params without output (can't probe multi-turn integration of tool results). The mock-output field is what makes a follow-up prompt's reaction-to-tool-output probeable.
- **No artificial cap on Probes per session** — once >1 is permitted (the engine already supports N via `setToolsAndResetConversation(List<…>)`), a UI cap has no code benefit; the real bound is authoring tedium, not a count limit. Bounded-set and unlimited are the same from the code perspective.
- **Probe authoring = preset library + paste-import (day-one); structured form / AI-assist are later iterations.** Presets are seeded from the already-authored `ToolSpecification` objects in `data/.../tool_tests.json` (e.g. `get_location`, `get_weather`) — correct by construction, 1-tap add then tweak. Paste-import reuses `ToolSpecification`'s `@Serializable` deserialization to validate an OpenAI-style JSON the dev already has. Both kill the "too much / not correct" tedium without a heavy property-by-property form builder.
- **Phased cut line: v1 = Minimal Delight Loop, full plan preserved (ADR-0003).** The whole grill is kept as the handoff plan (`docs/edgelab/plan.md`); execution starts small. v1 ships one screen (Playground) + one backend (cloud Gemini via Firebase proxy) + one authoring path (preset library) + one result (annotated Trace). Local-model download, paste-import, Benchmark, and Test-Suite reskin are later phases that compose on top. Chosen to keep the plan honestly "small" and to de-risk the cloud onboarding backend in isolation before layering local-model complexity. See `docs/adr/0003-v1-minimal-delight-loop.md`.

## Open gaps

_Gaps reorganized by phase after the grill. See `docs/edgelab/plan.md` for the full phased plan._

### CyclingCopilot (out of scope for this plan — see `docs/cyclingcopilot/plan.md` Reality Check)

- **CopilotAgent not wired** — brain built/tested in `:agent`, never constructed in `:app:copilot`; `sendTextMessage()` still mocks.
- **Timestamp GPS playback missing** — `SimulatedGpsSource` ignores the `t` field; `elapsedMs`/`RideContext` unused in running app.
- **Onboarding URLs TODO** — placeholder in `OnboardViewModel`.

### Phase 1 (v1 — Minimal Delight Loop)

- **Playground UX missing** — the Probe infra (`ToolHandler`/`OpenApiToolHandler`/`ToolCall`/`ValidationRule`) is built and tested in `:agent`/`:data`; build the in-app UI to define a Probe from the preset library, send a prompt, and read the annotated Trace.
- **Cloud onboarding backend not built** — implement a small Firebase surface (form TBD — Functions/Genkit/callable) holding the maintainer's Gemini API key; app calls it with zero dev-facing key.

### Phase 2 (Full Playground prime path)

- **Model catalog TODO** — `Dependencies.modelRepository` returns `ALL_MODELS`; `CopilotModelCatalogProvider` not created. (Note: the EdgeLab explorer `ServiceLocator` may already differ; verify at Phase 2 start.)
- Build the local `.litertlm` download + cloud↔local switch; add paste-import authoring.

### Resolved by the grill (no longer gaps)

- **P1–P5 + Benchmark opt-in** — all resolved; decisions recorded above. P1→curated+size-bounded catalog (ADR-0002). P2→only `.litertlm`, add when artifact exists. P3→cloud-first onboarding (option d). P4→Trace contract (annotations, no verdicts). P5→retired (only one *on-device* engine; cloud permitted). Benchmark→explicit per-upload.
