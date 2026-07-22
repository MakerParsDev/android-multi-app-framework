# ECC bundle security review

Reviewed on 2026-07-22 after the generated ECC bundle was merged.

## Findings

The generated repository skill and agent role files contain no credentials and do not grant automatic Git or deployment permissions. The original `.codex/config.toml`, however, defined repository-wide external MCP servers, launched packages with `npx -y`, used mutable `@latest` references, and enabled a remote MCP endpoint. Those settings could execute changed third-party packages whenever a developer opened the repository.

## Remediation

- Removed all tracked MCP server definitions.
- Kept the repository sandbox at `workspace-write` with approval policy `on-request`.
- Kept only local multi-agent role declarations.
- Required credentials and optional MCP definitions to live in the user's `~/.codex/config.toml`.
- Added `scripts/ci/ecc_bundle_policy.py` and regression tests to prevent repo-local MCP servers, `npx -y`, mutable `@latest` packages, and remote MCP endpoints from returning.

## Accepted files

The generated repository skills, read-only explorer/reviewer/docs-researcher roles, identity metadata, and command scaffolds remain tracked. They are advisory development aids and receive no production credentials or automatic push permissions.
