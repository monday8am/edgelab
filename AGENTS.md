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
8. Every new ViewModel requires unit tests. Every new Composable screen requires `@Preview`. See `presentation/AGENTS.md` for specifics.
9. IMPORTANT: Read `docs/patterns.md` before adding new classes. Read `docs/testing.md` before writing tests.
10. Always rethrow `CancellationException` — never swallow in catch blocks.
11. Rules apply to **new and modified code**. In existing files, follow the file's current patterns unless explicitly refactoring.

## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| Side effects inside `MutableStateFlow.update{}` | Extract before `update{}` — lambda may CAS-retry |
| Redundant `flowOn` when `withContext` handles dispatch | Pick one |
| Wrapping suspend calls in `scope.launch(ioDispatcher)` | `scope.launch` — downstream layer dispatches itself |
| Import `androidx.lifecycle` in `:presentation` | Only in `:app:*` wrappers |
| Hardcode `Dispatchers.IO` in ViewModel | Accept `ioDispatcher` param |
| Missing `@Volatile` for cross-thread mutable vars | `@Volatile` or `AtomicReference` |
| Boolean flags in test fakes (`called = true`) | Int counters (`callCount++`) |
| `println()` or `android.util.Log` | Kermit `Logger.withTag()` |

## Documentation

Read these **before** the corresponding task:

| Before doing this | Read first |
|-------------------|-----------|
| Adding a new class or pattern | [`docs/patterns.md`](docs/patterns.md) |
| Writing or modifying tests | [`docs/testing.md`](docs/testing.md) |
| Adding a dependency | [`docs/dependencies.md`](docs/dependencies.md) |
| Adding a module or screen | [`docs/architecture.md`](docs/architecture.md) |

## Scoped AGENTS.md

`presentation/AGENTS.md` contains module-specific rules. Read it before modifying presentation code.

## Agent Skills

Load these skills when needed:

| Skill | When to load |
|-------|-------------|
| `edgelab-commands` | Build, test, format, install, or verify changes |
| `edgelab-testing` | Writing or modifying tests |
| `edgelab-release` | Preparing a release build |
| `edgelab-workflows` | Adding modules/screens or using agent workflows |
