# EdgeLab

Android research lab for on-device agentic AI. Two apps sharing multi-module architecture:
- **EdgeLab** — model testing and tool-calling validation
- **CyclingCopilot** — on-device AI cycling assistant

## Module Dependency Graph

```
:data (Pure Kotlin) <- :agent (Pure Kotlin/JVM) <- :presentation (Pure Kotlin) <- :core (Android library) <- :app:explorer
                                                                                                           <- :app:copilot
```

Strict unidirectional. No module may depend on a module to its right.

## Critical Rules

1. `:data`, `:agent`, `:presentation` are **pure Kotlin**. Zero `android.*` imports.
2. `:core` is an Android library. `:app:*` are Android apps. Android code lives only there.
3. DI uses manual factory methods (`CoreDependencies` + app `Dependencies` object). No Hilt, no Koin, no Dagger.
4. Run `./gradlew ktfmtFormat` before every commit. Pre-commit hook enforces formatting.
5. Use `ImmutableList`/`ImmutableMap` from `kotlinx.collections.immutable` in all UiState data classes.
6. All dependency versions go in `gradle/libs.versions.toml`. Never hardcode in `build.gradle.kts`.
7. Every feature follows the 3-layer ViewModel pattern: interface + impl in `:presentation`, wrapper in `:app:*`. See `docs/patterns.md`.
8. Every new ViewModel requires unit tests. Every new Composable screen requires `@Preview`. See `presentation/CLAUDE.md` for specifics.

## Commands

### Build
- `./gradlew build` — all modules
- `./gradlew :app:explorer:assembleDebug` — EdgeLab APK
- `./gradlew :app:copilot:assembleDebug` — CyclingCopilot APK
- `./gradlew clean` — clean

### Test
- `./gradlew test` — all unit tests
- `./gradlew :presentation:test` — presentation tests
- `./gradlew :agent:test` — agent tests
- `./gradlew :data:test` — data tests
- `./gradlew :core:testDebugUnitTest` — core tests

### Format
- `./gradlew ktfmtFormat` — auto-fix formatting
- `./gradlew ktfmtCheck` — check only

### Install
- `./gradlew :app:explorer:installDebug` — install EdgeLab
- `./gradlew :app:copilot:installDebug` — install CyclingCopilot

### Release
- `./gradlew :app:explorer:bundleRelease` — EdgeLab AAB (requires upload keystore)
- `./gradlew :app:copilot:bundleRelease` — CyclingCopilot AAB (requires upload keystore)
- GitHub Actions: **Release Build** workflow (manual trigger, select app + version)

### Signing
- Uses **Google Play App Signing** — Google holds the app signing key, we hold upload keys.
- Each app has its own upload keystore and properties file:
  - `signing/explorer-upload.keystore` + `signing/explorer-upload.properties`
  - `signing/copilot-upload.keystore` + `signing/copilot-upload.properties`
- All signing files are gitignored. CI uses per-app GitHub secrets (`EDGELAB_UPLOAD_*` / `COPILOT_UPLOAD_*`).

## Documentation

| Document | Purpose |
|----------|---------|
| [`docs/patterns.md`](docs/patterns.md) | Code patterns with examples and anti-patterns |
| [`docs/testing.md`](docs/testing.md) | Test patterns, fakes, verification workflow |
| [`docs/dependencies.md`](docs/dependencies.md) | Build dependency constraints and gotchas |
| [`docs/architecture.md`](docs/architecture.md) | Module contents — all classes and interfaces |
| [`docs/edgelab/roadmap.md`](docs/edgelab/roadmap.md) | EdgeLab feature roadmap |
| [`docs/cyclingcopilot/roadmap.md`](docs/cyclingcopilot/roadmap.md) | CyclingCopilot feature roadmap |
| [`docs/cyclingcopilot/ui-architecture.md`](docs/cyclingcopilot/ui-architecture.md) | CyclingCopilot screen designs and specs |

## Scoped CLAUDE.md

`presentation/CLAUDE.md` contains module-specific rules. Read it before modifying presentation code.

## Keeping Docs Current

When you add/rename/remove:
- A module → update module graph in this file + `docs/architecture.md`
- A ViewModel or Screen → update screen tables in `docs/architecture.md`
- A dependency with exclusions or constraints → update `docs/dependencies.md`
- A code pattern that agents keep getting wrong → add to `docs/patterns.md` anti-patterns

## Workflow Automation

Agent workflows live in `.agent/workflows/` with `// turbo-all` auto-approval. Run `ls .agent/workflows/` to see available workflows.
