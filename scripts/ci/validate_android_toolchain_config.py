#!/usr/bin/env python3
"""Validate that Android/JVM toolchain versions have one authoritative source."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROPERTIES = ROOT / "gradle.properties"
JAVA_MAJOR_KEY = "toolchain.java.major"
COMPILE_SDK_KEY = "toolchain.android.compileSdk"
PLATFORM_PACKAGE_KEY = "toolchain.android.platformPackage"
TARGET_SDK_KEY = "toolchain.android.targetSdk"
MIN_SDK_KEY = "toolchain.android.minSdk"
BUILD_TOOLS_KEY = "toolchain.android.buildTools"
CMDLINE_TOOLS_KEY = "toolchain.android.cmdlineTools"
BOOTSTRAP_CMDLINE_TOOLS_REVISION_KEY = (
    "toolchain.android.bootstrapCmdlineToolsRevision"
)
YAML_GLOB = "*.yml"
REQUIRED_KEYS = {
    JAVA_MAJOR_KEY,
    COMPILE_SDK_KEY,
    PLATFORM_PACKAGE_KEY,
    TARGET_SDK_KEY,
    MIN_SDK_KEY,
    BUILD_TOOLS_KEY,
    CMDLINE_TOOLS_KEY,
    BOOTSTRAP_CMDLINE_TOOLS_REVISION_KEY,
}
FORBIDDEN_PIPELINE_TOKENS = (
    "ANDROID_API_LEVEL",
    "ANDROID_BUILD_TOOLS_VERSION",
    "androidApiLevel",
)
HARDCODE_PATTERNS = {
    "compileSdk": re.compile(r"\bcompileSdk\s*=\s*\d"),
    "minSdk": re.compile(r"\bminSdk\s*=\s*\d"),
    "targetSdk": re.compile(r"\btargetSdk\s*=\s*\d"),
    "jvmToolchain": re.compile(r"\bjvmToolchain\(\s*\d"),
    "JavaLanguageVersion": re.compile(r"JavaLanguageVersion\.of\(\s*\d"),
    "jvmTarget": re.compile(r"\bjvmTarget\s*=\s*\"\d"),
    "JavaVersion": re.compile(r"JavaVersion\.VERSION_\d"),
}


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def find_hardcoded_toolchain_values(text: str) -> list[tuple[int, str]]:
    """Return line/label pairs for executable hardcoded toolchain declarations."""
    findings: list[tuple[int, str]] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        code = line.split("//", 1)[0].strip()
        if not code:
            continue
        for label, pattern in HARDCODE_PATTERNS.items():
            if pattern.search(code):
                findings.append((line_number, label))
    return findings


def validate_manifest(values: dict[str, str]) -> list[str]:
    errors: list[str] = []
    missing = sorted(REQUIRED_KEYS - values.keys())
    if missing:
        return ["Missing toolchain properties: " + ", ".join(missing)]

    integer_keys = {
        JAVA_MAJOR_KEY: "Java major",
        COMPILE_SDK_KEY: "compileSdk",
        TARGET_SDK_KEY: "targetSdk",
        MIN_SDK_KEY: "minSdk",
    }
    integers: dict[str, int] = {}
    for key, label in integer_keys.items():
        try:
            integers[key] = int(values[key])
        except ValueError:
            errors.append(f"{label} must be an integer: {values[key]}")

    try:
        platform_major = int(values[PLATFORM_PACKAGE_KEY].split(".", 1)[0])
    except ValueError:
        errors.append(
            "platformPackage must start with an integer: "
            + values[PLATFORM_PACKAGE_KEY]
        )
        platform_major = None

    try:
        build_tools_major = int(values[BUILD_TOOLS_KEY].split(".", 1)[0])
    except ValueError:
        errors.append(
            "buildTools must start with an integer: "
            + values[BUILD_TOOLS_KEY]
        )
        build_tools_major = None

    if errors:
        return errors

    compile_sdk = integers[COMPILE_SDK_KEY]
    target_sdk = integers[TARGET_SDK_KEY]
    min_sdk = integers[MIN_SDK_KEY]
    java_major = integers[JAVA_MAJOR_KEY]

    if platform_major != compile_sdk:
        errors.append("platformPackage major must match compileSdk")
    if build_tools_major != compile_sdk:
        errors.append("buildTools major must match compileSdk")
    if not min_sdk <= target_sdk <= compile_sdk:
        errors.append("Expected minSdk <= targetSdk <= compileSdk")
    if java_major < 21:
        errors.append("Java toolchain must be 21 or newer")
    return errors


def collect_gradle_files(root: Path) -> list[Path]:
    files = [root / "build.gradle.kts", root / "app/build.gradle.kts"]
    files.extend(sorted((root / "core").glob("*/build.gradle.kts")))
    files.extend(sorted((root / "feature").glob("*/build.gradle.kts")))
    return files


def validate_gradle_files(paths: list[Path], root: Path) -> list[str]:
    errors: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for line_number, label in find_hardcoded_toolchain_values(text):
            errors.append(
                f"Hardcoded {label} in {path.relative_to(root)} on line {line_number}"
            )
    return errors


def collect_pipeline_files(root: Path) -> list[Path]:
    return [
        *sorted((root / "azure-pipelines").glob(YAML_GLOB)),
        *sorted((root / "pipelines").glob(YAML_GLOB)),
        *sorted((root / "pipelines/templates").rglob(YAML_GLOB)),
    ]


def validate_pipeline_files(paths: list[Path], root: Path) -> list[str]:
    errors: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for token in FORBIDDEN_PIPELINE_TOKENS:
            if token in text:
                errors.append(
                    f"Pipeline duplicates toolchain value via {token}: "
                    f"{path.relative_to(root)}"
                )
    return errors


def validate_script_references(root: Path) -> list[str]:
    required_references = {
        root / "scripts/ci/setup-android-sdk.sh": "android-toolchain.sh",
        root / "scripts/ci/verify-android-toolchain.sh": "android-toolchain.sh",
        root / "scripts/ci/local-full-verification.sh": "verify-android-toolchain.sh",
    }
    errors: list[str] = []
    for path, needle in required_references.items():
        if needle not in path.read_text(encoding="utf-8"):
            errors.append(f"{path.relative_to(root)} must reference {needle}")
    return errors


def collect_validation_errors(
    root: Path,
    values: dict[str, str],
    gradle_files: list[Path],
    pipeline_files: list[Path],
) -> list[str]:
    return [
        *validate_manifest(values),
        *validate_gradle_files(gradle_files, root),
        *validate_pipeline_files(pipeline_files, root),
        *validate_script_references(root),
    ]


def main() -> int:
    values = parse_properties(PROPERTIES)
    gradle_files = collect_gradle_files(ROOT)
    pipeline_files = collect_pipeline_files(ROOT)
    errors = collect_validation_errors(ROOT, values, gradle_files, pipeline_files)

    if errors:
        print("Android toolchain configuration validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print("Android toolchain configuration is centralized and consistent:")
    for key in sorted(REQUIRED_KEYS):
        print(f"  {key}={values[key]}")
    print(f"  checked Gradle files: {len(gradle_files)}")
    print(f"  checked pipeline files: {len(pipeline_files)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
