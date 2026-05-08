---
name: commit-pr-greptile
description: Commit changes, push, create PR, and poll Greptile for review with automatic fix application. Use when completing a feature and wanting automated code review.
---

# Commit, PR, and Greptile Review

## Goal

1. Commit and push current changes
2. Create a GitHub PR
3. Poll Greptile for review (up to 7 attempts, 60s intervals)
4. Apply safe suggestions and update the PR

---

## Step 0 — Pre-flight Checks

```bash
# Confirm we are in a git repo
git rev-parse --show-toplevel

# Check current branch
git branch --show-current

# Confirm gh CLI is authenticated
gh auth status

# Check for staged or unstaged changes
git status --short
```

If there are no changes to commit:
> "No changes detected to commit. Exiting."

If not on a feature branch (e.g., on `main` or `master`):
> "You appear to be on the main branch. Please switch to a feature branch first."

---

## Step 1 — Commit and Push Changes

1. **Stage all changes**:
   ```bash
   git add -A
   ```

2. **Generate commit message**:
   - Review changed files: `git status --short`
   - Review recent changes: `git diff --staged --stat`
   - Create a descriptive commit message (imperative mood, detailed bullet points)

3. **Commit**:
   ```bash
   git commit -m "<commit message>"
   ```

   If commit fails due to pre-commit hooks:
   - If it's ktfmt/formatting: run `./gradlew ktfmtFormat`, stage, retry
   - If it's other linting: fix, stage, retry
   - If it fails twice: inform user and stop

4. **Push to remote**:
   ```bash
   git push -u origin <current-branch>
   ```

   If push fails due to hooks, check the error:
   - If it's ktfmt/formatting: run `./gradlew ktfmtFormat`, stage, retry
   - If it's other linting: fix, stage, retry
   - If it's remote rejection: inform user and stop

---

## Step 2 — Create Pull Request

1. **Check if PR already exists and is open**:
   ```bash
   gh pr view --json number,url,title,state 2>/dev/null
   ```

   - If the command fails (no PR) → proceed to create one
   - If `state` is `"OPEN"` → use the existing PR, skip creation
   - If `state` is `"CLOSED"` or `"MERGED"` → create a new one

2. **If no open PR exists**, create one:
   - Extract info from commits: `git log origin/main..HEAD --oneline`
   - Generate PR title (short, imperative, <70 chars)
   - Generate PR body with summary and changes sections

   ```bash
   gh pr create --title "<title>" --body "<generated body>"
   ```

3. **Record PR number** from output

---

## Step 3 — Poll Greptile Review

**Constants:**
- `MAX_POLL_ATTEMPTS = 7`
- `POLL_INTERVAL = 60` seconds

**Initial wait**: Sleep 60 seconds after PR creation to allow Greptile to start

**Polling loop** (attempts 1 to MAX_POLL_ATTEMPTS):

1. Call Greptile MCP tool to check for comments:
   ```
   mcp__greptile__list_merge_request_comments(
       name: "owner/repo",
       remote: "github",
       defaultBranch: "main",
       prNumber: <N>,
       greptileGenerated: true
   )
   ```

2. **Check review completion** with:
   ```bash
   gh pr view <N> --comments
   ```
   Look for Greptile summary comment with confidence score.

3. **If review is complete** (has Greptile summary):
   - Extract confidence score (format: "Confidence Score: X/5")
   - If score is 5/5 → proceed to Step 4 with "no issues found"
   - If score is <5 and there are comments → proceed to Step 4 with comments
   - If score is <5 but no actionable comments → inform user and exit

4. **If review not complete yet**:
   - Show progress: "Waiting for Greptile review (attempt N/7)..."
   - Sleep POLL_INTERVAL seconds
   - Continue loop

5. **If MAX_POLL_ATTEMPTS exhausted**:
   - Check one final time for any comments
   - If still no review: inform user "Greptile review did not complete after 7 attempts (7 minutes). PR is open at <url>."
   - Exit

---

## Step 4 — Apply Greptile Suggestions

**If confidence score is 5/5 and no comments**:
```
✅ Greptile review complete with perfect score (5/5)
No issues found - PR is ready to merge!
PR: <url>
```
Exit successfully.

**If there are Greptile comments**:

For each comment:

1. **Parse comment details**: file path, line number(s), suggestion text, confidence level
2. **Read affected code** to understand context
3. **Classify suggestion**:

   **✅ Auto-apply (safe):**
   - Formatting/style fixes, unused imports/variables, simple typos
   - Obvious null checks, missing error handling (simple cases)
   - Naming improvements, simple logic corrections

   **⚠️ Needs confirmation:**
   - Refactoring >20 lines, architectural changes, behavior changes
   - Security-sensitive code, database/API changes
   - Deletions of >10 lines, file renames/moves

4. **For safe suggestions**: apply the fix, add to batch
5. **For risky suggestions**: show proposed change, ask user "Apply this change? (yes/no/skip)"

---

## Step 5 — Test and Commit Fixes

After processing all (or a batch of) suggestions:

1. **Run project checks**: `./gradlew ktfmtFormat && ./gradlew test`

2. **If tests pass** ✅:
   ```bash
   git add -A
   git commit -m "Apply Greptile review suggestions

   - <list of fixes applied>"
   git push
   ```

3. **If tests fail** ❌:
   - Show failure output
   - Ask user: "Tests failed after applying Greptile suggestions. Options:
     1. Fix manually
     2. Revert last commit
     3. Skip tests and commit anyway
     4. Abort"

4. **Update PR** after successful push:
   - Comment on PR: "Applied Greptile review suggestions ✅"

---

## Safety Rules

- **Never auto-apply** changes to: security-sensitive code, database migrations, API contracts, build/deployment configs, files with >50 line changes
- **Always verify** with tests before pushing
- **Commit in batches**: If >5 suggestions, group by file/concern
- **Stop if tests fail twice** — ask user instead of retrying
- **Preserve code formatting**: Run ktfmtFormat if project uses it

---

## Error Handling

| Situation | Action |
|-----------|--------|
| No changes to commit | Exit early with message |
| Push rejected by hooks | Fix issues, retry once, then ask user |
| PR creation fails | Show error, ask user to resolve |
| Existing PR is closed/merged | Ignore it, create a new PR |
| Greptile not available | Inform user to enable Greptile plugin |
| Review timeout (7 attempts) | Report timeout, provide PR URL |
| Merge conflicts on push | Do not force-push, ask user to resolve |
| Can't parse Greptile comment | Skip it, inform user |
| Ambiguous fix suggestion | Ask user for clarification |

---

## Final Summary

After completing, show:

```
## Greptile PR Review Complete

**PR**: #<N> — <title>
**URL**: <pr-url>
**Branch**: <branch-name>
**Confidence Score**: <X>/5

### ✅ Changes Applied
- [file:line] <fix description>

### ⚠️ Skipped (needed confirmation)
- [file:line] <suggestion> — <reason>

### ❌ Not Addressed
- [file:line] <comment> — <reason>

### Test Results
- Status: <passed/failed/skipped>
```
