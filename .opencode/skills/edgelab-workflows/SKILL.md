---
name: edgelab-workflows
description: Agent workflow automation, documentation maintenance checklists, and slash commands for the EdgeLab project. Load this skill when adding modules, screens, or using agent workflows.
---

# EdgeLab Workflow Reference

## Agent Workflows

Agent workflows live in `.agent/workflows/` with `// turbo-all` auto-approval.

Run `ls .agent/workflows/` to see available workflows.

## Keeping Docs Current

When you add/rename/remove:

| Change | Update |
|--------|--------|
| A module | Module graph in root `CLAUDE.md` + `docs/architecture.md` |
| A ViewModel or Screen | Screen tables in `docs/architecture.md` |
| A dependency with exclusions/constraints | `docs/dependencies.md` |
| A code pattern agents keep getting wrong | `docs/patterns.md` anti-patterns |
| A new convention | `docs/patterns.md` |
| A recurring mistake | Anti-Patterns table in root `CLAUDE.md` |

## Slash Commands

| Command | Description |
|---------|-------------|
| `/commit-pr-greptile` | Commit, create PR, poll Greptile review, auto-fix |
