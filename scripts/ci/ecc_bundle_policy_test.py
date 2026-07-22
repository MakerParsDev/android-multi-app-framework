#!/usr/bin/env python3
from __future__ import annotations

import tempfile
from pathlib import Path

from ecc_bundle_policy import validate


def write_repo(root: Path, config: str, guide: str) -> None:
    (root / ".codex").mkdir(parents=True)
    (root / ".codex/config.toml").write_text(config, encoding="utf-8")
    (root / ".codex/AGENTS.md").write_text(guide, encoding="utf-8")


def test_safe_repo_passes() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        write_repo(
            root,
            'approval_policy = "on-request"\nsandbox_mode = "workspace-write"\n',
            "Keep credentials and optional MCP server definitions in ~/.codex/config.toml\n",
        )
        assert validate(root) == []


def test_repo_local_mcp_and_latest_fail() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        write_repo(
            root,
            '[mcp_servers.context7]\ncommand = "npx"\nargs = ["-y", "@upstash/context7-mcp@latest"]\n',
            "unsafe\n",
        )
        messages = [finding.message for finding in validate(root)]
        assert "repo-local MCP servers are forbidden" in messages
        assert "auto-installing npx -y commands are forbidden" in messages
        assert "mutable @latest package references are forbidden" in messages
        assert any("user config" in message for message in messages)


def test_repository_bundle_passes() -> None:
    repo = Path(__file__).resolve().parents[2]
    assert validate(repo) == []


def main() -> int:
    for test in (test_safe_repo_passes, test_repo_local_mcp_and_latest_fail, test_repository_bundle_passes):
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
