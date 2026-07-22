#!/usr/bin/env python3
"""Validate variant-scoped Baseline Profiles and compiled AAB metadata."""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from resolve_ci_flavor_matrix import load_flavors

ROOT = SCRIPT_DIR.parents[1]


def gradle_flavor_token(flavor: str) -> str:
    if not flavor or not flavor[0].islower():
        raise ValueError(f"Invalid flavor: {flavor}")
    return flavor[0].upper() + flavor[1:]


def expected_profile_dir(repo: Path, flavor: str) -> Path:
    return (
        repo
        / "app"
        / "src"
        / f"{flavor}Release"
        / "generated"
        / "baselineProfiles"
    )


def load_packages(repo: Path) -> dict[str, str]:
    data = json.loads((repo / ".ci/apps.json").read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError(".ci/apps.json must contain an array")
    packages: dict[str, str] = {}
    for entry in data:
        if not isinstance(entry, dict):
            raise ValueError(".ci/apps.json entries must be objects")
        flavor = entry.get("flavor")
        package_name = entry.get("package")
        if not isinstance(flavor, str) or not isinstance(package_name, str):
            raise ValueError(".ci/apps.json entries require string flavor and package")
        packages[flavor] = package_name
    return packages


def normalized_rules(path: Path) -> set[str]:
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }


def validate_profile_pair(
    repo: Path,
    flavor: str,
    packages: dict[str, str],
) -> list[str]:
    errors: list[str] = []
    directory = expected_profile_dir(repo, flavor)
    baseline = directory / "baseline-prof.txt"
    startup = directory / "startup-prof.txt"
    for path in (baseline, startup):
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f"[{flavor}] missing or empty profile: {path}")
    if errors:
        return errors

    baseline_rules = normalized_rules(baseline)
    startup_rules = normalized_rules(startup)
    if not startup_rules.issubset(baseline_rules):
        errors.append(f"[{flavor}] startup profile is not a subset of baseline profile")

    for other, package_name in packages.items():
        if other == flavor:
            continue
        descriptor = package_name.replace(".", "/")
        if any(descriptor in rule for rule in baseline_rules | startup_rules):
            errors.append(f"[{flavor}] profile references other flavor package: {other}")
    return errors


def validate_aab(aab: Path) -> list[str]:
    required = {
        "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof",
        "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm",
    }
    if not aab.is_file():
        return [f"AAB does not exist: {aab}"]
    try:
        with zipfile.ZipFile(aab) as archive:
            names = set(archive.namelist())
    except zipfile.BadZipFile:
        return [f"AAB is not a readable ZIP archive: {aab}"]
    return [
        f"AAB missing compiled profile metadata: {name}"
        for name in sorted(required - names)
    ]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("validate-source", "task"):
        command = sub.add_parser(name)
        command.add_argument("--flavor", required=True)
    sub.add_parser("validate-all")
    aab = sub.add_parser("validate-aab")
    aab.add_argument("--flavor", required=True)
    aab.add_argument("--aab", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        flavors = load_flavors(ROOT)
        packages = load_packages(ROOT)
    except (RuntimeError, ValueError, OSError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    selected = getattr(args, "flavor", None)
    if selected is not None and selected not in flavors:
        print(f"ERROR: unknown flavor: {selected}", file=sys.stderr)
        return 2

    if args.command == "task":
        print(f"generate{gradle_flavor_token(selected)}ReleaseBaselineProfile")
        return 0

    if args.command == "validate-aab":
        errors = validate_aab(args.aab)
    else:
        targets = flavors if args.command == "validate-all" else [selected]
        errors = [
            error
            for flavor in targets
            for error in validate_profile_pair(ROOT, flavor, packages)
        ]

    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    print("Performance profile validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
