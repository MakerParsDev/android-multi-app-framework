#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote, urlparse


def run_git(repo_root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout


def normalize_relpath(path: str) -> str:
    return path.replace("\\", "/").lstrip("./")


def file_uri_to_path(value: str) -> Path:
    parsed = urlparse(value)
    if parsed.scheme != "file":
        return Path(value)
    raw_path = unquote(parsed.path)
    if raw_path.startswith("/") and len(raw_path) > 2 and raw_path[2] == ":":
        raw_path = raw_path[1:]
    return Path(raw_path)


def collect_changed_files(repo_root: Path, base_ref: str | None) -> set[str]:
    if base_ref:
        ref = f"origin/{base_ref}"
        diff = run_git(repo_root, "diff", "--name-only", f"{ref}...HEAD")
    else:
        has_prev = subprocess.run(
            ["git", "rev-parse", "--verify", "HEAD~1"],
            cwd=repo_root,
            text=True,
            capture_output=True,
        )
        if has_prev.returncode == 0:
            diff = run_git(repo_root, "diff", "--name-only", "HEAD~1", "HEAD")
        else:
            diff = run_git(repo_root, "ls-files")

    return {
        normalize_relpath(line)
        for line in diff.splitlines()
        if line.strip()
    }


def resolve_artifact_path(
    repo_root: Path,
    original_uri_base_ids: dict[str, dict[str, str]],
    artifact_location: dict[str, str],
) -> str | None:
    uri = artifact_location.get("uri")
    if not uri:
        return None

    uri_base_id = artifact_location.get("uriBaseId")
    if uri_base_id and uri_base_id in original_uri_base_ids:
        base_uri = original_uri_base_ids[uri_base_id].get("uri")
        if base_uri:
            base_path = file_uri_to_path(base_uri)
            full_path = (base_path / Path(uri)).resolve()
            try:
                return normalize_relpath(str(full_path.relative_to(repo_root.resolve())))
            except ValueError:
                pass

    candidate = Path(uri)
    if candidate.is_absolute():
        try:
            return normalize_relpath(str(candidate.resolve().relative_to(repo_root.resolve())))
        except ValueError:
            return None

    uri_text = normalize_relpath(uri)
    repo_parts = repo_root.parts
    repo_marker = normalize_relpath("/".join(repo_parts[-3:]))
    if repo_marker in uri_text:
        return normalize_relpath(uri_text.split(repo_marker, 1)[1].lstrip("/"))
    return uri_text


def collect_matching_results(repo_root: Path, changed_files: set[str]) -> dict[str, list[str]]:
    matches: dict[str, list[str]] = defaultdict(list)
    sarif_files = repo_root.glob("**/build/reports/ktlint/**/*.sarif")

    for sarif_file in sarif_files:
        data = json.loads(sarif_file.read_text(encoding="utf-8"))
        for run in data.get("runs", []):
            original_uri_base_ids = run.get("originalUriBaseIds", {})
            for result in run.get("results", []):
                locations = result.get("locations", [])
                if not locations:
                    continue

                physical = locations[0].get("physicalLocation", {})
                artifact = physical.get("artifactLocation", {})
                relpath = resolve_artifact_path(repo_root, original_uri_base_ids, artifact)
                if not relpath or relpath not in changed_files:
                    continue

                region = physical.get("region", {})
                line = region.get("startLine", "?")
                message = result.get("message", {}).get("text", "Unknown ktlint issue")
                rule_id = result.get("ruleId", "unknown-rule")
                matches[relpath].append(f"L{line}: [{rule_id}] {message}")

    return matches


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail CI if changed files contain ktlint findings.")
    parser.add_argument("--repo-root", default=".", help="Repository root path.")
    parser.add_argument("--base-ref", default=None, help="Base ref name for PR diff, e.g. main.")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    changed_files = collect_changed_files(repo_root, args.base_ref)
    matches = collect_matching_results(repo_root, changed_files)

    if not matches:
        print("No ktlint findings in changed files.")
        return 0

    print("ktlint findings detected in changed files:")
    for path in sorted(matches):
        print(f"- {path}")
        for issue in matches[path]:
            print(f"  {issue}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
