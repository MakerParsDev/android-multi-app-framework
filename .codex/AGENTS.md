# Codex repository baseline

This repository keeps only deterministic, reviewable Codex settings under version control.

## Repository skill

- Repo skill: `.agents/skills/android-multi-app-framework/SKILL.md`
- Claude-facing companion: `.claude/skills/android-multi-app-framework/SKILL.md`

## Security boundary

Keep credentials and optional MCP server definitions in `~/.codex/config.toml`, not in this repository.

The tracked `.codex/config.toml` must not launch `npx -y` packages, use mutable `@latest` references, or define remote MCP endpoints. Repository workflows enforce this boundary with `scripts/ci/ecc_bundle_policy.py`.

## Multi-agent roles

- Explorer: read-only evidence gathering
- Reviewer: correctness, security, and regression review
- Docs researcher: API and release-note verification

## Workflow scaffolds

- `.claude/commands/feature-development.md`
- `.claude/commands/add-or-update-ci-workflow.md`
- `.claude/commands/document-or-plan-ci-workflow.md`
