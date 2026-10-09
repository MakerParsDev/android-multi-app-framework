#!/usr/bin/env python3
"""Fail-closed PR impact selection for Android flavors and side projects."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from resolve_ci_flavor_matrix import load_flavors

ANDROID_PREFIXES = (
    "app/", "core/", "feature/", "performance/", "buildSrc/", "gradle/",
    "scripts/ci/", ".ci/", "config/", ".github/", "maintenance/", "tests/",
)
ANDROID_FILES = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "gradlew", "gradlew.bat", ".mergify.yml", "AGENTS.md", "codecov.yml",
}
ANDROID_SUFFIXES = (".kt", ".kts", ".java", ".xml", ".gradle", ".properties")


def classify_paths(paths: list[str], flavors: list[str]) -> dict[str, str]:
    side_projects = any(path.startswith("side-projects/") for path in paths)
    android_paths = [
        path for path in paths
        if not path.startswith("side-projects/")
        and (
            path.startswith(ANDROID_PREFIXES)
            or path in ANDROID_FILES
            or path.endswith(ANDROID_SUFFIXES)
        )
    ]
    if not android_paths:
        return {
            "has_code": "false",
            "has_side_project_changes": str(side_projects).lower(),
            "flavors_json": "[]",
            "all_flavors_json": "[]",
        }

    # A flavor-only change may select that flavor. Any shared, unknown or
    # control-plane change instead checks every flavor; never silently skip it.
    selected: set[str] = set()
    for path in android_paths:
        matching = [
            flavor for flavor in flavors
            if path.startswith(f"app/src/{flavor}/")
        ]
        if len(matching) != 1:
            selected = set(flavors)
            break
        selected.add(matching[0])

    compact = lambda values: json.dumps(values, separators=(",", ":"))
    return {
        "has_code": "true",
        "has_side_project_changes": str(side_projects).lower(),
        "flavors_json": compact([flavor for flavor in flavors if flavor in selected]),
        "all_flavors_json": compact(flavors),
    }


def changed_paths(repo: Path, base_ref: str) -> list[str]:
    if not base_ref or base_ref.startswith("-") or "/" in base_ref and ".." in base_ref:
        raise ValueError("invalid pull-request base reference")
    result = subprocess.run(
        ["git", "diff", "--name-only", "-z", f"origin/{base_ref}...HEAD"],
        cwd=repo, check=True, capture_output=True,
    )
    return [path.decode("utf-8") for path in result.stdout.split(b"\0") if path]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--base-ref", required=True)
    args = parser.parse_args()
    try:
        repo = args.repo.resolve()
        paths = changed_paths(repo, args.base_ref)
        if not paths:
            raise ValueError("empty PR diff; refusing to skip required checks")
        # Only load the Android catalog when Android work is in scope.
        side_only = all(path.startswith("side-projects/") for path in paths)
        docs_only = all(path.endswith(".md") and not path.startswith(ANDROID_PREFIXES) for path in paths)
        flavors = [] if side_only or docs_only else load_flavors(repo)
        outputs = classify_paths(paths, flavors)
        if outputs["has_code"] == "true" and not flavors:
            raise ValueError("Android impact without a valid flavor catalog")
        destination = Path(__import__("os").environ["GITHUB_OUTPUT"])
        with destination.open("a", encoding="utf-8") as output:
            for key, value in outputs.items():
                output.write(f"{key}={value}\n")
        print(f"PR files: {len(paths)}; Android: {outputs['has_code']}; "
              f"side projects: {outputs['has_side_project_changes']}; "
              f"selected flavors: {outputs['flavors_json']}")
        return 0
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError) as exc:
        print(f"ERROR: Cannot safely classify PR impact: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
