#!/usr/bin/env python3
"""
Deterministic Risk Classifier for Jules Fleet PRs.

Classifies changed file paths into LOW_RISK or PROTECTED.
- LOW_RISK paths: normal app/core/feature code, unit tests, non-sensitive documentation.
- PROTECTED paths: .github/**, .fleet/**, .agents/**, .claude/**, .codex/**, config/**, scripts/**,
  root build/gradle infrastructure, dependency policy, security gates, secret ownership metadata.

Fails closed to PROTECTED if any file is ambiguous or unclassified.
"""

import sys
import os
import argparse
import re

PROTECTED_PREFIXES = (
    ".github/",
    ".fleet/",
    ".agents/",
    ".claude/",
    ".codex/",
    "config/",
    "scripts/",
    "gradle/",
    "buildSrc/",
)

PROTECTED_EXACT_FILES = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "app-versions.properties",
    ".gitleaks.toml",
    ".gitleaksignore",
    ".pre-commit-config.yaml",
}

LOW_RISK_PREFIXES = (
    "app/src/",
    "core/",
    "feature/",
    "performance/",
    "side-projects/",
)

LOW_RISK_DOC_PATTERNS = (
    r"^docs/.*\.md$",
    r"^README\.md$",
)


def classify_file(path: str) -> str:
    path = path.strip().lstrip("./")

    if not path:
        return "LOW_RISK"

    # Check protected exact files
    if path in PROTECTED_EXACT_FILES:
        return "PROTECTED"

    # Check protected prefixes
    for prefix in PROTECTED_PREFIXES:
        if path.startswith(prefix):
            return "PROTECTED"

    # Check doc files under docs/ (excluding protected specs or workflow docs if any)
    for pattern in LOW_RISK_DOC_PATTERNS:
        if re.match(pattern, path):
            return "LOW_RISK"

    # Check low-risk source code prefixes
    for prefix in LOW_RISK_PREFIXES:
        if path.startswith(prefix):
            return "LOW_RISK"

    # Fail-closed default
    return "PROTECTED"


def classify_changed_files(files: list) -> str:
    if not files:
        return "LOW_RISK"

    for f in files:
        status = classify_file(f)
        if status == "PROTECTED":
            return "PROTECTED"

    return "LOW_RISK"


def main():
    parser = argparse.ArgumentParser(description="Classify PR risk based on changed files.")
    parser.add_argument("--files", nargs="*", help="List of changed file paths")
    parser.add_argument("--files-file", help="Path to file containing list of changed files (one per line)")

    args = parser.parse_args()

    files = []
    if args.files:
        files.extend(args.files)

    if args.files_file and os.path.exists(args.files_file):
        with open(args.files_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    files.append(line)

    classification = classify_changed_files(files)
    print(classification)


if __name__ == "__main__":
    main()
