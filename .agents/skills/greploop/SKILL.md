---
name: greploop
description: Iteratively fix a PR against Greptile review until 5/5 confidence. Polls Greptile comments, applies safe fixes, pushes, and repeats.
---

# Greploop — Fix PR to Greptile 5/5

Iteratively apply Greptile review suggestions until the PR reaches a 5/5 confidence score.

## Steps

1. **Get PR number** (from arguments or detect current branch):
   ```bash
   gh pr view --json number,url --jq '.number'
   ```

2. **Poll Greptile for review comments**:
   ```
   mcp__greptile__list_merge_request_comments(
       name: "owner/repo",
       remote: "github",
       defaultBranch: "main",
       prNumber: <N>,
       greptileGenerated: true
   )
   ```

3. **Check confidence score** from Greptile summary comment:
   - If **5/5** → Done. Report success and exit.
   - If **<5/5 or no comments yet** → Wait 60s, poll again (up to 7 attempts).

4. **Apply safe suggestions** from Greptile comments:
   - **Auto-apply**: formatting, unused imports, simple typos, null checks, naming
   - **Ask user**: refactoring >20 lines, architectural changes, behavior changes, security-sensitive code

5. **Run project checks**:
   ```bash
   ./gradlew ktfmtFormat && ./gradlew test
   ```

6. **Commit and push fixes**:
   ```bash
   git add -A
   git commit -m "Apply Greptile review suggestions
   - <list of fixes applied>"
   git push
   ```

7. **Comment on PR**: "Applied Greptile review suggestions ✅"

8. **Loop back** to step 2 until 5/5 or no more actionable comments.

## Safety Rules

- Never auto-apply: security code, DB migrations, API contracts, build/deploy configs
- Stop if tests fail twice — ask user
- Commit in batches if >5 suggestions
- Run `ktfmtFormat` before committing

## Usage

- `/skill:greploop` — Fix the PR for the current branch
- `/skill:greploop <number>` — Fix a specific PR number
