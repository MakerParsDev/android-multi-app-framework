---
name: document-or-plan-ci-workflow
description: Workflow command scaffold for document-or-plan-ci-workflow in android-multi-app-framework.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /document-or-plan-ci-workflow

Use this workflow when working on **document-or-plan-ci-workflow** in `android-multi-app-framework`.

## Goal

Documents or plans new CI workflows or enhancements, typically in markdown files under docs/.

## Common Files

- `docs/superpowers/plans/*.md`
- `docs/superpowers/specs/*.md`
- `.github/workflows/README.md`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Create or update a plan/spec document in docs/superpowers/plans/ or docs/superpowers/specs/
- Optionally add or update documentation in .github/workflows/README.md or related docs

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.