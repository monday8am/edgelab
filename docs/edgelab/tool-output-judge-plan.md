# Tool-Output Judge — Implementation Plan

> **Goal:** replace the brittle `[used/ignored tool output]` tag with a **two-layer judge**: deterministic heuristic as the fast path, **one** Firebase AI Logic second-opinion for the "ignored" case, run asynchronously so the conversation never blocks. The seam stays loose enough to slot in another judge (Gemini Nano / N judges in parallel) later.

## Where things stand

- Handoff (`edgelab-handoff-2026-08-29.md`): task #1 "Improve tool-call success detection".
- Spike on branch `feature/tool-output-usage-semantic` (uncommitted WIP): proved the heuristic can't win (coords→city gazetteer, month-name normalizer, always the next case) **and** surfaced a real bug — `Json.parseToJsonElement("2024-02-15")` returns a `JsonLiteral` instead of throwing, so a bare date-string mock contributes **zero** evidence. Judge, don't parse.
- Decision (from discussion): heuristic for "used" = done; "ignored" = escalate once to Cloud Gemini. Cloud runs offline-reject (`INDETERMINATE`) on error; Nano/N-in-parallel is for later.

## Three-state verdict

```
enum class ToolOutputVerdict { USED, APPARENTLY_IGNORED, IGNORED }
```

- `USED` — heuristic found literal evidence → final, no LLM.
- `APPARENTLY_IGNORED` — heuristic found no literal evidence → shown while LLM is pending.
- `IGNORED` — LLM returned a verdict (or `USED` if LLM agrees the output was integrated).
  LLM error / offline → stays `APPARENTLY_IGNORED` (degraded but honest, never fabricated).

`TraceEntry.ModelText.usedToolOutput: ToolOutputVerdict?` — null only when no tool was called.

## Design

### 1. `ToolOutputJudge` in `:agent` (pure Kotlin)

```kotlin
fun interface ToolOutputJudge { suspend fun isUsed(mock: String, text: String): Boolean }
```

- `HeuristicToolOutputJudge` — wraps `ToolOutputUsage` (+ the `JsonLiteral` date-string fix).
- `LlmToolOutputJudge(chatFactory: CloudChatFactory)` — YES/NO classifier prompt, fresh session per check; `CloudChatFactory` stays provider-free in `:agent`; Firebase adapter in `:core`.

Composite decision lives in the caller, not a wrapper class — the branch is one `if`:
```kotlin
val verdict = if (heuristic.isUsed(mock, text)) USED else APPARENTLY_IGNORED // then async LLM
```

Prompt (single-shot — open a fresh session, never reuse the conversation chat; reply contract is YES/NO only):
```
You are a strict judge. Tool output: {mock}. Assistant answer: {text}.
Did the answer use the tool output, even paraphrased, rounded, or reformatted?
Reply YES or NO only.
```

### 2. `PlaygroundViewModelImpl` — owns the async second opinion

The only consumer changed. Heuristic verdict is computed synchronously in `executeTurn` (before appending the trace, not inside `viewModelState.update{}`). If `APPARENTLY_IGNORED`, launch a child coroutine that:

1. calls `judgeFactory(target).isUsed(mock, text)` on `ioDispatcher`,
2. swaps the **single** trace entry immutably via `viewModelState.update { it.copy(trace = it.trace.map { e -> if (e.id == id) e.copy(usedToolOutput = newVerdict) else e }.toPersistentList()) }`,
3. rethrows `CancellationException`.

`judgeFactory: (PlaygroundTarget) -> ToolOutputJudge` injected alongside `backendFactory`; Cloud → composite, Local → heuristic-only (no cloud bleed for pure-local runs until Nano).

`ModelText` stays an immutable `data class`; `ToolOutputUsageTest` / `PlaygroundViewModelTest` stay green (verdict declared before append).

### 3. Cloud wiring in `:core`

`CoreDependencies.createJudgeFactory(...)` returns the factory. `FirebaseAiChatFactory.open(emptyList())` used for the judge (no tools). One fresh `generativeModel` per call; the heuristic fast-path usually skips it.

Later iterations (explicitly deferred): `NanoToolOutputJudge` (on-device impl of the same interface) and `ParallelToolOutputJudge(judges, policy)` — both swap in at `CoreDependencies` with zero `:presentation` change.

## Testing (`docs/testing.md`)

- `ToolOutputUsageTest`: add regression test for the `JsonLiteral` date-string fix.
- `LlmToolOutputJudgeTest`: fake `CloudChatFactory` returning scripted YES/NO/"Yes."; robust parse.
- `PlaygroundViewModelTest` (extend): `FakeToolOutputJudge` with `Int callCount` (AGENTS.md), assert verdict transitions `APPARENTLY_IGNORED → USED/IGNORED`, no `update{}` side-effect, `dispose()` called.

## Anti-pattern checklist

- `ioDispatcher` injected, no hardcoded `Dispatchers.IO`.
- Judge verdict computed outside `viewModelState.update{}`.
- `CancellationException` rethrown; exception logged with Kermit tag.
- No new DI framework; factory in `CoreDependencies` only.

## Out of scope (next iteration)

- Gemini Nano impl / N-judges in parallel / BYOK for the judge leg.
- Offline `APPARENTLY_IGNORED` copy polish in the UI.

## Risk frame

- **Data surface:** judge sends prompt+mock to cloud — same data the user consents to on Cloud target; Local defaults to heuristic-only.
- **Probabilistic verdict:** deliberate, acceptable for a display-only tag; heuristic still resolves the common case deterministically.
