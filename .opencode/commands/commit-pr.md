---
description: Commit changes, push, and create a GitHub PR. Lightweight alternative to /commit-pr-greptile — no Greptile review loop.
agent: build
subtask: true
---

# Commit, Push, and Create PR

You are an autonomous coding agent that commits code, pushes it, and creates a GitHub PR.

## Goal

1. Run pre-flight checks
2. Commit current changes
3. Push to remote
4. Create or reuse a GitHub PR
5. Print PR URL and exit

---

## Step 0 — Pre-flight Checks

Run these checks first:

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

## Step 1 — Format, Stage, and Commit

1. **Run formatting** if the project uses ktfmt:
   ```bash
   ./gradlew ktfmtFormat
   ```

2. **Stage all changes**:
   ```bash
   git add -A
   ```

3. **Generate commit message**:
   - Review changed files: `git status --short`
   - Review diff stats: `git diff --staged --stat`
   - Create a descriptive commit message following this format:
     ```
     <Short summary (imperative mood)>

     <Detailed bullet points of changes>
     ```

4. **Commit**:
   ```bash
   git commit -m "<commit message>"
   ```

   If commit fails due to pre-commit hooks:
   - If it's ktfmt/formatting: run `./gradlew ktfmtFormat`, stage changes, and retry commit
   - If it's other linting: fix the issues, stage, and retry
   - If it fails twice: inform user and stop

---

## Step 2 — Push

```bash
git push -u origin <current-branch>
```

If push is rejected due to remote having newer commits:
- Do NOT force-push
- Ask user to resolve the merge conflict first

---

## Step 3 — Create or Reuse Pull Request

1. **Check if an open PR already exists**:
   ```bash
   gh pr view --json number,url,title,state 2>/dev/null
   ```

   - If the command fails (no PR) → proceed to create one
   - If `state` is `"OPEN"` → reuse existing PR, skip creation
   - If `state` is `"CLOSED"` or `"MERGED"` → create a new PR

2. **If no open PR exists**, create one:
   - Extract info from commits: `git log $(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's|^refs/remotes/origin/||' || echo main)..HEAD --oneline`
   - Generate PR title (short, imperative, <70 chars)
   - Generate PR body with:
     - Summary section
     - Changes section (bullet points)
     - Technical details if applicable

   ```bash
   gh pr create --title "<title>" --body "<generated body>"
   ```

3. **Print result**:
   ```
   ✅ PR ready
   #<N> — <title>
   URL: <pr-url>
   Branch: <branch-name>
   ```

---

## Error Handling

| Situation | Action |
|-----------|--------|
| No changes to commit | Exit early with message |
| On main/master branch | Ask user to switch to a feature branch |
| Push rejected by hooks | Fix issues, retry once, then ask user |
| PR creation fails | Show error, ask user to resolve |
| Existing PR is closed/merged | Ignore it, create a new PR |
| Remote has newer commits | Do not force-push, ask user to pull/resolve |

---

## Final Summary

After completing, show:

```
## PR Created

**PR**: #<N> — <title>
**URL**: <pr-url>
**Branch**: <branch-name>
**Commits**:
- <sha> — <commit message>
```
