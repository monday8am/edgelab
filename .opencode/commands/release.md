---
description: Trigger a GitHub Actions release build for an app
agent: build
subtask: true
---

Trigger the release workflow on GitHub Actions for the EdgeLab project.

Usage: `/release <app> <version_name> <version_code> [create_release]`

- `$1` — app: `explorer` or `copilot`
- `$2` — version_name (e.g. `1.2.0`)
- `$3` — version_code (positive integer, e.g. `12`)
- `$4` — create_release: `true` or `false` (optional, default `false`)

Steps:

1. Verify `gh` is authenticated: `gh auth status`
2. Validate `$1` is exactly `explorer` or `copilot`, and `$3` is a positive integer
3. Trigger the workflow on `main`:

```
gh workflow run release.yml --ref main \
  -f app=$1 \
  -f version_name=$2 \
  -f version_code=$3 \
  -f create_release=$4
```

4. Poll the run every 30s for up to 5 minutes by running `gh run list --workflow release.yml --limit 1 --json conclusion,headBranch,databaseId` until conclusion is non-null
5. Report the outcome (success/failure + link to run)
