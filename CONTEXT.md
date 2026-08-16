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
| **EdgeLab** (app) | Model-testing & tool-calling validation app (`:app:explorer`, package `com.monday8am.edgelab.explorer`). Published to Play Store. |
| **CyclingCopilot** | On-device AI cycling assistant (`:app:copilot`, package `com.monday8am.edgelab.copilot`). Currently pre-MVP. |
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

## Open gaps (pre-grill)

- **CopilotAgent not wired** — brain built/tested in `:agent`, never constructed in `:app:copilot`; `sendTextMessage()` still mocks. See `docs/cyclingcopilot/plan.md` Reality Check.
- **Timestamp GPS playback missing** — `SimulatedGpsSource` ignores the `t` field; `elapsedMs`/`RideContext` unused in running app.
- **Model catalog TODO** — `Dependencies.modelRepository` returns `ALL_MODELS`; `CopilotModelCatalogProvider` not created.
- **Onboarding URLs TODO** — placeholder in `OnboardViewModel`.
