---
description: Iteratively fix a PR against Greptile review until 5/5 confidence
agent: build
subtask: true
model: opencode-go/kimi-k2.6
---

Load the `greploop` skill and follow its instructions.

Use the skill tool to load it:

```
skill greploop
```

Pass the PR number if provided as `$ARGUMENTS`, otherwise detect the PR for the current branch.
