# CLAUDE.md Refinement Plan

## Context

EdgeLab's CLAUDE.md (88 lines) is loaded every conversation. Per [official best practices](https://code.claude.com/docs/en/best-practices#write-an-effective-claude-md):

> "For each line, ask: *Would removing this cause Claude to make mistakes?* If not, cut it."
> "Bloated CLAUDE.md files cause Claude to ignore your actual instructions!"

**Include:** Bash commands Claude can't guess, code style that differs from defaults, common gotchas, architectural decisions.
**Exclude:** Anything Claude can infer from code, standard conventions, long explanations, info that changes frequently.

The goal is **better, not bigger**. The docs/ layer is excellent — the real problem is CLAUDE.md doesn't tell the agent *when* to read them, and a few high-value gotchas aren't surfaced.

## Design Principle

**Every line must prevent a mistake Claude would otherwise make.** If Claude already does it right without the instruction, cut it. Domain knowledge only relevant sometimes → skills or docs/, not CLAUDE.md.

Target: **~95 lines** (current: 88). Small net change — a few additions, a few cuts.

---

## Audit: Keep / Cut / Add

### Keep (Claude can't infer these)
- Project description + module graph (architectural, non-obvious)
- Rules 1-8 (all prevent real mistakes)
- Commands (bash commands Claude can't guess)
- Signing (relevant for releases, only 5 lines)
- Scoped CLAUDE.md (important trigger)
- Keeping Docs Current (prevents doc rot)
- Workflow Automation (useful)

### Cut
- **3 roadmap/UI-architecture entries** from Documentation table — project planning docs, not code conventions. Claude doesn't need them every session. (-3 rows)

### Add
- **3 critical rules** (~3 lines): read-before-write trigger, CancellationException, old-code boundary
- **Anti-Patterns table** (~12 lines): "common gotchas" from real mistakes in this codebase
- **Verification workflow** (~5 lines): testing instructions Claude can't guess
- **Slash commands** (~2 lines)
- **2 bullets** to Keeping Docs Current

Net: +22 lines added, -3 rows cut, doc table reformatted → **~95-100 lines total**

---

## Changes

### File: `CLAUDE.md`

#### 1. Add 3 rules to Critical Rules

After rule 8:
```
9. IMPORTANT: Read `docs/patterns.md` before adding new classes. Read `docs/testing.md` before writing tests.
10. Always rethrow `CancellationException` — never swallow in catch blocks.
11. Rules apply to **new and modified code**. In existing files, follow the file's current patterns unless explicitly refactoring.
```

Rule 9 is the most impactful — turns passive doc references into a mandatory trigger. Rule 11 solves old-code boundary in one line.

#### 2. Add Anti-Patterns section (after Critical Rules)

```markdown
## Anti-Patterns

| Don't | Do Instead |
|-------|-----------|
| Side effects inside `MutableStateFlow.update{}` | Extract before `update{}` — lambda may CAS-retry |
| Redundant `flowOn` when `withContext` handles dispatch | Pick one |
| Import `androidx.lifecycle` in `:presentation` | Only in `:app:*` wrappers |
| Hardcode `Dispatchers.IO` in ViewModel | Accept `ioDispatcher` param |
| Missing `@Volatile` for cross-thread mutable vars | `@Volatile` or `AtomicReference` |
| Boolean flags in test fakes (`called = true`) | Int counters (`callCount++`) |
| `println()` or `android.util.Log` | Kermit `Logger.withTag()` |
```

Every row is a real mistake caught in this codebase. Rows that duplicate existing rules (ImmutableList is rule 5) or standard Kotlin knowledge (GlobalScope/runBlocking) are excluded.

#### 3. Add Verification section (after Commands)

```markdown
## Verification

| When | What | Command |
|------|------|---------|
| Every commit | Format | `./gradlew ktfmtFormat` |
| Every commit | Tests | `./gradlew test` |
| Every commit | Build both apps | `./gradlew :app:explorer:assembleDebug :app:copilot:assembleDebug` |

Fail → fix → re-run from top.
```

#### 4. Replace Documentation table with active triggers

```markdown
## Documentation

Read these **before** the corresponding task:

| Before doing this | Read first |
|-------------------|-----------|
| Adding a new class or pattern | [`docs/patterns.md`](docs/patterns.md) |
| Writing or modifying tests | [`docs/testing.md`](docs/testing.md) |
| Adding a dependency | [`docs/dependencies.md`](docs/dependencies.md) |
| Adding a module or screen | [`docs/architecture.md`](docs/architecture.md) |
```

Removes 3 roadmap/UI-architecture entries (not needed every session). Changes passive "Purpose" column to active "Before doing this" trigger. `presentation/CLAUDE.md` is already covered by the Scoped CLAUDE.md section.

#### 5. Add slash commands to Workflow Automation

```markdown
### Slash Commands
- `/commit-pr-greptile` — Commit, create PR, poll Greptile review, auto-fix.
```

#### 6. Add 2 bullets to Keeping Docs Current

```
- A new convention → add to `docs/patterns.md`
- A recurring mistake → add to Anti-Patterns table above
```

---

### File: `presentation/CLAUDE.md`

One addition under Anti-patterns:
```
See also the Anti-Patterns table in root `CLAUDE.md`.
```

---

### No changes to `docs/` files

They're already excellent. Rule 9 ensures Claude reads them at the right time.

---

## Verification

1. Read final CLAUDE.md — every line must fail the "would removing this cause mistakes?" test
2. Count lines — should be ~95-100
3. Confirm all referenced file paths exist
