#!/usr/bin/env python3
"""Validate repository-local AI assistant configuration safety boundaries."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    message: str


FORBIDDEN_CONFIG_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"^\s*\[mcp_servers\.", re.MULTILINE), "repo-local MCP servers are forbidden"),
    (re.compile(r'^\s*command\s*=\s*["\']npx["\']\s*$', re.MULTILINE), "auto-installing npx -y commands are forbidden"),
    (re.compile(r"@latest\b"), "mutable @latest package references are forbidden"),
    (re.compile(r"https://mcp\.", re.IGNORECASE), "remote MCP endpoints are forbidden in repo config"),
)


def line_for(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def validate(repo: Path) -> list[Finding]:
    repo = repo.resolve()
    config = repo / ".codex/config.toml"
    guide = repo / ".codex/AGENTS.md"
    findings: list[Finding] = []

    try:
        config_text = config.read_text(encoding="utf-8")
    except FileNotFoundError:
        findings.append(Finding(config.relative_to(repo), 1, "missing Codex repository config"))
        config_text = ""

    for pattern, message in FORBIDDEN_CONFIG_PATTERNS:
        for match in pattern.finditer(config_text):
            findings.append(Finding(config.relative_to(repo), line_for(config_text, match.start()), message))

    try:
        guide_text = guide.read_text(encoding="utf-8")
    except FileNotFoundError:
        findings.append(Finding(guide.relative_to(repo), 1, "missing Codex repository guide"))
        guide_text = ""

    required_guide_pattern = re.compile(
        r"Keep credentials and optional MCP server definitions in `?~/\.codex/config\.toml`?"
    )
    if not required_guide_pattern.search(guide_text):
        findings.append(
            Finding(
                guide.relative_to(repo),
                1,
                "guide must keep credentials and optional MCP definitions in the user config",
            )
        )

    return sorted(findings, key=lambda item: (str(item.path), item.line, item.message))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    findings = validate(args.repo)
    for finding in findings:
        print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
