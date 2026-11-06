#!/usr/bin/env python3
"""Require actual passing Android-device evidence for the retained audio navigation regression."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_CLASS = "com.parsfilo.contentapp.ui.AudioPlayerLocalizedSemanticsTest"
REQUIRED_METHOD = "retainedNavigationDestinationObservesLiveAudioProgress"


def verify_required_test(report_root: Path) -> str:
    reports = sorted(report_root.rglob("TEST-*.xml")) if report_root.is_dir() else []
    if not reports:
        raise ValueError(f"No managed-device JUnit XML reports under {report_root}")

    found: list[tuple[Path, ET.Element]] = []
    for report in reports:
        try:
            tree = ET.parse(report)
        except (ET.ParseError, OSError) as exc:
            raise ValueError(f"Unreadable managed-device JUnit XML: {report}: {exc}") from exc
        for case in tree.iter("testcase"):
            if case.get("classname") == REQUIRED_CLASS and case.get("name") == REQUIRED_METHOD:
                found.append((report, case))

    if len(found) != 1:
        raise ValueError(
            f"Expected exactly one {REQUIRED_CLASS}.{REQUIRED_METHOD} JUnit testcase, "
            f"found {len(found)} across {len(reports)} reports"
        )

    report, case = found[0]
    for marker in ("failure", "error", "skipped"):
        if case.find(marker) is not None:
            raise ValueError(f"Required audio navigation JUnit testcase is {marker}: {report}")

    return f"PASS {REQUIRED_CLASS}.{REQUIRED_METHOD} ({report})"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report_root", type=Path, help="Managed-device Android JUnit results directory")
    args = parser.parse_args(argv)
    try:
        print(verify_required_test(args.report_root))
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
