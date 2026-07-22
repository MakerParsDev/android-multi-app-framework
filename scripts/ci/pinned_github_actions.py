#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator, Sequence

import yaml

FULL_SHA = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True)
class PinFinding:
    path: Path
    line: int
    message: str


def iter_steps(value: Any) -> Iterator[dict[str, Any]]:
    if isinstance(value, dict):
        if "uses" in value:
            yield value
        for child in value.values():
            yield from iter_steps(child)
    elif isinstance(value, list):
        for child in value:
            yield from iter_steps(child)


def line_for(text: str, needle: str) -> int:
    for number, line in enumerate(text.splitlines(), start=1):
        if needle in line:
            return number
    return 1


def action_owner_repo(action_path: str) -> str:
    parts = action_path.split("/")
    if len(parts) < 2:
        return action_path
    return "/".join(parts[:2])


def load_manifest(repo: Path) -> dict[str, dict[str, str]]:
    path = repo / "config/pinned-github-actions.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RuntimeError("pinned action manifest must be an object")
    for name, metadata in value.items():
        if not isinstance(name, str) or not isinstance(metadata, dict):
            raise RuntimeError("invalid pinned action manifest entry")
        sha = metadata.get("sha")
        version = metadata.get("version")
        if not isinstance(sha, str) or not FULL_SHA.fullmatch(sha):
            raise RuntimeError(f"invalid pinned SHA for {name}")
        if not isinstance(version, str) or not version.strip():
            raise RuntimeError(f"missing pinned version for {name}")
    return value


def validate_pinned_actions(repo: Path) -> list[PinFinding]:
    repo = repo.resolve()
    manifest = load_manifest(repo)
    findings: list[PinFinding] = []
    paths = sorted(
        [
            *(repo / ".github/workflows").glob("*.y*ml"),
            *(repo / ".github/actions").rglob("action.y*ml"),
        ]
    )
    for path in paths:
        text = path.read_text(encoding="utf-8")
        document = yaml.safe_load(text)
        if not isinstance(document, dict):
            continue
        for step in iter_steps(document):
            uses = step.get("uses")
            if not isinstance(uses, str) or uses.startswith("./") or "@" not in uses:
                continue
            action_path, sha = uses.rsplit("@", 1)
            approved_name = action_owner_repo(action_path)
            metadata = manifest.get(approved_name)
            relative = path.relative_to(repo)
            if metadata is None:
                findings.append(
                    PinFinding(
                        relative,
                        line_for(text, uses),
                        f"external action is not approved: {approved_name}",
                    )
                )
                continue
            approved_sha = metadata["sha"]
            if sha != approved_sha:
                findings.append(
                    PinFinding(
                        relative,
                        line_for(text, uses),
                        f"{approved_name} must use approved SHA {approved_sha}",
                    )
                )
    return sorted(findings, key=lambda item: (str(item.path), item.line, item.message))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        findings = validate_pinned_actions(args.repo)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    for finding in findings:
        print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
