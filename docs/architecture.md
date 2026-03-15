# Architecture

Technical documentation for the EdgeLab project. Covers module structure, key components, integration patterns, and implementation details for developers and AI agents working on this codebase.

**Important:** This is a prototype built for fast iteration, experimentation, and learning.

---

## Module Structure

```
:data ← :agent ← :presentation ← :core ← :app:explorer
                                        ← :app:copilot
```

Six modules with a strict unidirectional dependency graph:

| Module | Type | Package |
|--------|------|---------|
| `:data` | Pure Kotlin | `com.monday8am.edgelab.data` |
| `:agent` | Pure Kotlin/JVM | `com.monday8am.edgelab.agent` |
| `:presentation` | Pure Kotlin (KMP-ready) | `com.monday8am.edgelab.presentation` |
| `:core` | Android library | `com.monday8am.edgelab.core` |
| `:app:explorer` | Android app | `com.monday8am.edgelab.explorer` |
| `:app:copilot` | Android app | `com.monday8am.edgelab.copilot` |

---

## `:data` — Data Models & Interfaces

Pure Kotlin, no Android dependencies. Defines repository interfaces, data models, and HuggingFace API integration.

Key interfaces: `AuthRepository`, `ModelRepository`, `ModelCatalogProvider`, `TestRepository`. Browse `data/src/` for full contents.

---

## `:agent` — Agent Logic & Tool Calling

Pure Kotlin/JVM. Depends on `:data` and LiteRT-LM JVM. Contains the inference engine interface, tool handling, and Koog framework integration.

Key interface: `LocalInferenceEngine` — abstract engine with `initialize`, `prompt`, `promptStreaming`, `setToolsAndResetConversation`, `closeSession`. Browse `agent/src/` for full contents.

---

## `:presentation` — ViewModels & State Management

Pure Kotlin, KMP-ready (no Android dependencies). Depends on `:data` and `:agent`. All ViewModels follow the 3-layer pattern described in [`docs/patterns.md`](patterns.md).

Key interface: `ModelDownloadManager` — abstract download manager (implemented in `:core`). Browse `presentation/src/` for full contents.

---

## `:core` — Android Infrastructure

Android library. Exposes `:data`, `:agent`, and `:presentation` transitively via `api` dependencies. Apps only need `implementation(project(":core"))`.

Implements interfaces from upstream modules with Android-specific infrastructure: LiteRT-LM inference, WorkManager downloads, AppAuth OAuth, DataStore + Tink storage. Entry point is `CoreDependencies` — factory object for all shared services. Browse `core/src/` for full contents.

---

## `:app:explorer` — EdgeLab App

**Package**: `com.monday8am.edgelab.explorer`
**Application ID**: `com.monday8am.edgelab.explorer`
**OAuth redirect**: `edgelab://oauth/callback`

Model testing and tool-calling validation platform.

### Screens

| Screen | ViewModel wrapper | Purpose |
|--------|------------------|---------|
| `ModelSelectorScreen` | `AndroidModelSelectorViewModel` | Browse, download, and manage models |
| `TestScreen` | `AndroidTestViewModel` | Run tool-calling test suites |
| `TestDetailsScreen` | `AndroidTestDetailsViewModel` | Browse test catalog with domain filtering |
| `AuthorManagerScreen` | `AndroidAuthorManagerViewModel` | Add/remove HuggingFace authors |

### Navigation
- `Routes.kt` — type-safe sealed class route definitions
- `AppNavigation.kt` — Compose `NavHost` graph

### DI
- `Dependencies.kt` — wires `CoreDependencies` factories into app-level singletons

---

## `:app:copilot` — CyclingCopilot App

**Package**: `com.monday8am.edgelab.copilot`
**Application ID**: `com.monday8am.edgelab.copilot`
**OAuth redirect**: `copilot://oauth/callback`

On-device AI cycling assistant. See [`docs/cyclingcopilot/ui-architecture.md`](cyclingcopilot/ui-architecture.md) for full screen specs.

### Current State
- **Screen 1 (Onboard)**: Complete — model download flow with HuggingFace sign-in
- **Screen 2 (Ride Setup)**: Complete — GPS mode, route selection, playback config
- **Screen 3 (Live Ride)**: Complete — map, HUD, chat panel, playback controls

### Screens

| Screen | ViewModel wrapper | Purpose |
|--------|------------------|---------|
| `OnboardScreen` | `AndroidOnboardViewModel` | HF sign-in + model download (2 models) |
| `RideSetupScreen` | `AndroidRideSetupViewModel` | GPS mode, route selection, playback config |

---

## Key Patterns

See [`docs/patterns.md`](patterns.md) for code patterns with concrete examples and anti-patterns.

---

## LiteRT-LM Integration

- `Engine.initialize()` loads model with `EngineConfig` (modelPath, backend, maxNumTokens)
- `Conversation` created via `engine.createConversation(ConversationConfig)`
- `ConversationConfig` parameters: `samplerConfig` (topK, topP, temperature), `systemMessage`, `tools`
- `sendMessageAsync()` extension bridges `MessageCallback` to Kotlin coroutines
- **CRITICAL**: Runtime context cannot exceed the model's compiled context window. The `.litertlm` model has hardcoded KV cache buffers set at export time. Exceeding them causes `GATHER_ND index out of bounds` errors.

### Default Parameters
| Parameter | Default |
|-----------|---------|
| Context length | 4096 tokens |
| Max output tokens | 512 tokens |
| Temperature | 0.7 |
| Top-K | 40 |
| Top-P | 0.9 |

---

## Configuration

| Setting | Value |
|---------|-------|
| `minSdk` | 31 (Android 12) |
| `targetSdk` / `compileSdk` | 36 |
| Kotlin toolchain | Java 17 (all modules) |
| Recommended JDK | 17 or 21 |

### Version Catalog
See `gradle/libs.versions.toml` for current dependency versions. See [`docs/dependencies.md`](dependencies.md) for dependency constraints and gotchas.

---

## On-Device Function Calling — Current State

Tool calling on mobile remains experimental as of early 2026.

**Best tested accuracy**: ~68% single-turn function call (Qwen3-0.6B)
**Core limitation**: 0.6B parameter models cannot reliably handle tool-calling JSON generation and reasoning simultaneously.
**Runtime constraint**: LiteRT-LM conversion pipeline requires CPU-only processing; only dynamic-8bit quantization is supported on GPU L4.

See [Part #2](https://monday8am.com/blog/2025/12/10/function-calling-edge-ai.html) and [Part #3](https://monday8am.com/blog/2026/02/08/lets-talk-about-functiongemma.html) for full analysis.

---

## Adding a New App

1. Create `app/newapp/` with a `build.gradle.kts` (copy from `app/copilot/`, update namespace and applicationId)
2. Set a unique OAuth redirect scheme in manifest placeholders
3. Create `MainActivity` and `Dependencies.kt` using `CoreDependencies` factories
4. Add to `settings.gradle.kts`: `include(":app:newapp")`

All infrastructure is available via `implementation(project(":core"))`.
