---
name: release-trigger
description: Trigger a GitHub Actions release build for EdgeLab or CyclingCopilot. Use when preparing a release build.
---

# Release Workflow Trigger

Usage: `/skill:release-trigger <app> <version_name> <version_code> [create_release]`

- `$1` — app: `explorer` or `copilot`
- `$2` — version_name (e.g. `1.2.0`)
- `$3` — version_code (positive integer, e.g. `12`)
- `$4` — create_release: `true` or `false` (optional, default `false`)

## Steps

1. Verify `gh` is authenticated: `gh auth status`

2. Validate inputs: `$1` is `explorer` or `copilot`, `$3` is a positive integer

3. Capture the current latest workflow run's `databaseId` to avoid matching stale runs:
   ```bash
   gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId // 0'
   ```

4. Trigger the workflow on `main`. If `$4` is empty, omit the `create_release` flag:

   ```bash
   gh workflow run release.yml --ref main \
     -f app=$1 \
     -f version_name=$2 \
     -f version_code=$3
   ```
   If `$4` is provided and non-empty, add: `-f create_release=$4`

5. Poll every 30s for up to 5 minutes. Use the captured `databaseId` to only match runs created *after* the trigger:

   ```bash
   gh run list --workflow release.yml --limit 3 \
     --json databaseId,conclusion,headBranch \
     -q '.[] | select(.databaseId > <captured_id> and .headBranch == "main") | .conclusion // empty' | head -1
   ```

   Repeat until conclusion is non-null or timeout.

6. Report the outcome (success/failure + link to run)
