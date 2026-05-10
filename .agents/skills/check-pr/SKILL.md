---
name: check-pr
description: Check a PR for Greptile review comments, status checks, and completeness. Use after creating a PR to see if Greptile has reviewed it.
---

# Check PR for Greptile Review

Use the Greptile MCP tool to check for review comments on the current PR.

## Steps

1. **Detect the PR for the current branch** (or use the provided PR number):
   ```bash
   gh pr view --json number,url,title,state --jq '.number'
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

3. **Check review status**:
   ```bash
   gh pr view <N> --comments
   gh pr checks <N>
   ```
   Look for Greptile summary comment with confidence score.

4. **Report results**:
   - Confidence score (format: "Confidence Score: X/5")
   - Number of Greptile-generated comments
   - Whether status checks pass

## Usage

- `/skill:check-pr` — Check the PR for the current branch
- `/skill:check-pr <number>` — Check a specific PR number
