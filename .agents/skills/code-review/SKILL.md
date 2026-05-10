---
name: code-review
description: Review uncommitted changes, commits, branch diffs, or PRs against EdgeLab conventions. Load this skill when asked to review code.
---

# EdgeLab Code Review

## What to Review

Based on the input provided:

1. **No arguments (default)**: Review all uncommitted changes
   - `git diff` for unstaged, `git diff --cached` for staged
   - `git status --short` for untracked files
2. **Commit hash**: Review that commit
   - `git show <hash>`
3. **Branch name**: Compare current branch to specified branch
   - `git diff <branch>...HEAD`
4. **PR URL or number**: Review the PR
   - `gh pr view <args>` + `gh pr diff <args>`

## Gather Context

**Diffs alone are not enough.** After getting the diff:
- Read the entire file(s) being modified for full context
- Use `git status --short` to identify untracked files, then read their contents
- Check project convention files: `AGENTS.md`, `docs/patterns.md`, `docs/testing.md`, `docs/architecture.md`

## EdgeLab-Specific Rules

When reviewing, verify compliance with:

- **Module boundaries**: `:data`, `:agent`, `:presentation` are pure Kotlin — zero `android.*` imports. Android code belongs only in `:core` and `:app:*`.
- **DI**: Manual factory methods only (`CoreDependencies` + app `Dependencies`). No Hilt, Koin, Dagger.
- **Formatting**: `./gradlew ktfmtFormat` must pass. Pre-commit hook enforces this.
- **Immutable collections**: `ImmutableList`/`ImmutableMap` from `kotlinx.collections.immutable` required in all UiState data classes.
- **ViewModel pattern**: Interface + impl in `:presentation`, Android wrapper in `:app:*`. Impl uses own `CoroutineScope`, not `viewModelScope`.
- **CancellationException**: Must always be rethrown — never swallowed in catch blocks.
- **Hardcoded dispatchers**: ViewModels must accept `ioDispatcher` param, not hardcode `Dispatchers.IO`.
- **MutableStateFlow.update**: No side effects inside `update{}` lambda (may CAS-retry).
- **Tests**: Every new ViewModel requires unit tests. Every new Composable screen requires `@Preview`.
- **Dependencies**: All versions go in `gradle/libs.versions.toml`. Never hardcode in `build.gradle.kts`.

## What to Look For

**Bugs** — Primary focus:
- Logic errors, off-by-one mistakes, incorrect conditionals
- Missing guards, incorrect branching, unreachable code paths
- Null/empty inputs, error conditions, race conditions
- Security issues: injection, auth bypass, data exposure
- Broken error handling: swallowed failures, unexpected throws
- CancellationException being swallowed (critical)
- Side effects inside `MutableStateFlow.update{}`
- Android imports in `:data`, `:agent`, or `:presentation`

**Structure** — Does the code fit?
- Follows existing patterns and conventions?
- Uses established abstractions?
- Excessive nesting (flatten with early returns)?
- Missing `@Preview` for new Composables
- Missing unit tests for new ViewModels
- Using `List` instead of `ImmutableList` in UiState

**Performance** — Only flag if obviously problematic:
- O(n²) on unbounded data, N+1 queries, blocking I/O on hot paths
- Redundant `flowOn` when `withContext` handles dispatch

**Behavior Changes** — Raise if possibly unintentional.

## Before Flagging

- Only review the changes — do not review pre-existing code
- Don't flag something as a bug if unsure — investigate first
- Don't invent hypothetical problems — explain realistic scenarios
- Verify actual violations, not perceived ones
- Acceptable: a `let` if the alternative is convoluted
- Excessive nesting is always a legitimate concern

## Output

1. Be direct and clear about why something is a bug
2. Clearly communicate severity — do not overstate
3. Explain the scenarios/inputs that trigger the issue
4. Tone: matter-of-fact, not accusatory or overly positive
5. No flattery — only comments that help the reader
