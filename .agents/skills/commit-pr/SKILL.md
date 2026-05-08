---
name: commit-pr
description: Commit changes, push to remote, and create a GitHub PR. Use when completing a feature and ready to open a pull request.
---

# Commit, Push, and Create PR

## Goal

1. Run pre-flight checks
2. Commit current changes
3. Push to remote
4. Create or reuse a GitHub PR
5. Print PR URL and exit

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
   - Create a descriptive commit message (imperative mood, detailed bullet points)

4. **Commit**:
   ```bash
   git commit -m "<commit message>"
   ```

   If commit fails due to pre-commit hooks:
   - If it's ktfmt/formatting: run `./gradlew ktfmtFormat`, stage, retry
   - If it's other linting: fix, stage, retry
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
   - Extract info from commits: `git log origin/main..HEAD --oneline`
   - Generate PR title (short, imperative, <70 chars)
   - Generate PR body with summary and changes sections

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
