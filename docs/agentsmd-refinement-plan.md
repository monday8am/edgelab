# AGENTS.md Refinement Plan

## Context

EdgeLab's AGENTS.md was refined from its original Claude Code format. Per [Agent Skills best practices](https://agentskills.io/specification):

> "For each line, ask: *Would removing this cause an agent to make mistakes?* If not, cut it."

**Include:** Bash commands agents can't guess, code style that differs from defaults, common gotchas, architectural decisions.
**Exclude:** Anything agents can infer from code, standard conventions, long explanations, info that changes frequently.

The goal is **better, not bigger**.

## Design Principle

**Every line must prevent a mistake an agent would otherwise make.** Domain knowledge only relevant sometimes → skills or docs/, not AGENTS.md.

---

## History

This doc records the original refinement plan that shaped the current AGENTS.md. The following changes were applied:

### Added to Critical Rules
- Read `docs/patterns.md` before adding new classes, `docs/testing.md` before writing tests
- Always rethrow `CancellationException`
- Rules apply to new and modified code only; existing files keep their patterns

### Added Anti-Patterns table
Every row is a real mistake caught in this codebase:
- Side effects in `MutableStateFlow.update{}`, redundant `flowOn`, Android imports in pure Kotlin modules, hardcoded dispatchers, missing `@Volatile`, boolean flags in test fakes, `println()`/`Log` instead of Kermit

### Added Verification workflow
Format → test → build both apps, in order, before every commit

### Documentation table replaced with active triggers
Passive "Purpose" column → active "Before doing this" triggers

### Slash commands added
`/commit-pr`, `/skill:check-pr`, `/skill:greploop` documented in workflow section

### Keeping Docs Current expanded
- New convention → add to `docs/patterns.md`
- Recurring mistake → add to Anti-Patterns table

---

## Verification (applied)

1. ✅ Every line in AGENTS.md prevents a real mistake
2. ✅ ~95 lines
3. ✅ All referenced file paths exist
