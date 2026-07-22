---
name: add-or-update-ci-workflow
description: Workflow command scaffold for add-or-update-ci-workflow in android-multi-app-framework.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /add-or-update-ci-workflow

Use this workflow when working on **add-or-update-ci-workflow** in `android-multi-app-framework`.

## Goal

Adds or updates a GitHub Actions CI workflow for Android, including configuration, scripts, and tests.

## Common Files

- `.github/workflows/*.yml`
- `scripts/ci/*.py`
- `scripts/ci/*.sh`
- `scripts/ci/*_test.py`
- `scripts/ci/*_test.sh`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Edit or create a workflow YAML file in .github/workflows/
- Update or add supporting scripts in scripts/ci/
- Update or add corresponding test scripts in scripts/ci/
- Optionally update related documentation

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.