#!/usr/bin/env python3
"""Collect Gradle quality and test reports into one CI artifact tree."""

from __future__ import annotations

import json
import os
import shutil
import uuid
from datetime import datetime, timezone
from pathlib import Path


def _report_build_dirs(repo_root: Path, destination: Path) -> list[Path]:
    return sorted(
        path
        for path in repo_root.rglob("build")
        if path.is_dir()
        and path != destination
        and destination not in path.parents
        and ".git" not in path.parts
        and ".gradle" not in path.parts
        and "node_modules" not in path.parts
    )


def _copy_report_files(source: Path, target: Path) -> int:
    """Copy report contents without non-portable ownership/mode metadata."""
    copied_files = 0
    for item in sorted(source.rglob("*")):
        if not item.is_file():
            continue
        destination_file = target / item.relative_to(source)
        destination_file.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(item, destination_file)
        copied_files += 1
    return copied_files


def collect_reports(repo_root: Path, destination: Path) -> dict[str, object]:
    repo_root = repo_root.resolve()
    destination = destination.resolve()
    if destination.is_symlink():
        raise ValueError(f"Artifact destination must not be a symlink: {destination}")

    build_dirs = _report_build_dirs(repo_root, destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_name(f".{destination.name}.tmp-{os.getpid()}-{uuid.uuid4().hex}")
    if temporary.exists():
        shutil.rmtree(temporary)
    temporary.mkdir(parents=True)

    copied_roots: list[str] = []
    copied_files = 0
    try:
        for build_dir in build_dirs:
            module_dir = build_dir.parent.relative_to(repo_root)
            module_target = temporary / (module_dir if module_dir.parts else Path("root"))
            for report_dir_name in ("reports", "test-results"):
                source = build_dir / report_dir_name
                if not source.is_dir():
                    continue
                target = module_target / report_dir_name
                copied_files += _copy_report_files(source, target)
                copied_roots.append(str(source.relative_to(repo_root)))

        manifest: dict[str, object] = {
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "copiedRoots": copied_roots,
            "copiedFileCount": copied_files,
        }
        (temporary / "manifest.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        if destination.exists():
            shutil.rmtree(destination)
        temporary.replace(destination)
        return manifest
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    destination = repo_root / "build" / "quality-reports"
    manifest = collect_reports(repo_root, destination)
    copied_roots = manifest["copiedRoots"]
    copied_files = manifest["copiedFileCount"]
    print(
        f"Collected {copied_files} quality/test report files from "
        f"{len(copied_roots)} report roots into {destination.relative_to(repo_root)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
