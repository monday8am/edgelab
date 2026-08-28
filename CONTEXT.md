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

_Hard-to-reverse decisions, stated as what holds today. Where an ADR exists it carries the alternatives we rejected and why — not repeated here._

**Architecture**

- **Pure-Kotlin module boundary** — `:data`, `:agent`, `:presentation` have zero `android.*` imports; Android impls live in `:core`.
- **Manual DI, no frameworks** — `CoreDependencies` + `Dependencies`. AGENTS.md Anti-Patterns forbid Hilt/Koin/Dagger.
- **ImmutableList/ImmutableMap in all UiState** — `kotlinx.collections.immutable` for Compose stability.
- **Single-model mode for CopilotAgent** — one model handles both tool-calling and response (memory/latency). Designed to accept a second engine later.

**Product shape**

- **Playground primary, Test Suite secondary, Benchmark an opt-in side effect** — ADR-0001. Test Suite code stays intact and gets demoted in the nav, not rewritten.
- **LiteRT-LM is the sole *on-device* backend; only `.litertlm` runs locally** — ADR-0002. A cloud backend is permitted for onboarding; that's a different category, in service of the on-device thesis.
- **v1 = the Minimal Delight Loop; the full plan is preserved, not executed at once** — ADR-0003. `docs/edgelab/plan.md` holds the phases.
- **Onboarding = cloud-first, then local download.** A new dev reaches the Playground with zero download, then switches to a local `.litertlm` once they understand the game. Honest about the comparison: local AI is weaker today, and that gap *is* why this app exists.

**Cloud leg** (live since 2026-08-27)

- **Gemini via Firebase AI Logic, `firebase-ai` SDK, maintainer-funded.** AI Logic keeps the key off the client, so there is no proxy to write, deploy, or fund. Provider path: **Gemini Developer API** (free tier, Spark plan), default model `gemini-3.5-flash-lite`, App Check enforced (debug token registered / release Play Integrity). Details and pricing: `docs/edgelab/research-cloud-models-interactions-api.md`.
- **Out of scope for v1: the Interactions API** — Gemini-API-only, so reaching it would put a key client-side or need a backend proxy. BYOK is a later iteration.

**Probe & Trace**

- **A Probe is a full `ToolSpecification`** — name + description + parameter schema + mock output, reusing the Test Suite data model verbatim. The mock output is what makes multi-turn tool-result integration probeable.
- **No cap on Probes per session** — the engine already supports N; the real bound is authoring tedium.
- **Authoring = preset library + paste-import day-one**, seeded from `data/.../tool_tests.json`. Structured form builder and AI-assist are later iterations.
- **Trace = transcript + inline annotations, no verdicts.** Per turn: prompt → model text → tool-call card (name, args with types) → mock output → next text, tagged `[used tool output]` / `[ignored tool output]`. Verdicts belong to the Test Suite.
- **Benchmark = explicit per-upload action.** A "Share anonymous results" button uploads one record only when tapped. Zero background telemetry; the tap *is* the consent.

## Open gaps

_Gaps reorganized by phase after the grill. See `docs/edgelab/plan.md` for the full phased plan._

### CyclingCopilot (out of scope for this plan — see `docs/cyclingcopilot/plan.md` Reality Check)

- **CopilotAgent not wired** — brain built/tested in `:agent`, never constructed in `:app:copilot`; `sendTextMessage()` still mocks.
- **Timestamp GPS playback missing** — `SimulatedGpsSource` ignores the `t` field; `elapsedMs`/`RideContext` unused in running app.
- **Onboarding URLs TODO** — placeholder in `OnboardViewModel`.

### Phase 1 (v1 — Minimal Delight Loop)

Shipped 2026-08. See `docs/edgelab/roadmap.md`.

### Phase 2 (Full Playground prime path)

- **Model catalog TODO** — `Dependencies.modelRepository` returns `ALL_MODELS`; `CopilotModelCatalogProvider` not created. (Note: the EdgeLab explorer `ServiceLocator` may already differ; verify at Phase 2 start.)
- Build the local `.litertlm` download + cloud↔local switch; add paste-import authoring.
