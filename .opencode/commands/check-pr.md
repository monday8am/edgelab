---
description: Check a PR for Greptile review comments, status checks, and completeness
agent: build
subtask: true
model: opencode-go/kimi-k2.6
---

Load the `check-pr` skill and follow its instructions.

Use the skill tool to load it:

```
skill check-pr
```

Pass the PR number if provided as `$ARGUMENTS`, otherwise detect the PR for the current branch.
