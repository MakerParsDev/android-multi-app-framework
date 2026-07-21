#!/usr/bin/env python3
"""Validate merged manifests for system-only alarm reschedule receivers."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID = f"{{{ANDROID_NS}}}"
EXPECTED_ACTIONS = {
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.TIME_SET",
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.MY_PACKAGE_REPLACED",
}
RECEIVERS = {
    "zikirmatik": "com.parsfilo.contentapp.feature.counter.alarm.ZikirSystemBroadcastReceiver",
    "namazvakitleri": "com.parsfilo.contentapp.feature.prayertimes.alarm.PrayerRescheduleReceiver",
}


class ManifestValidationError(ValueError):
    """Raised when a merged manifest violates the receiver policy."""


def validate_manifest(flavor: str, manifest_path: Path) -> dict[str, object]:
    receiver_name = RECEIVERS[flavor]
    if not manifest_path.is_file():
        raise ManifestValidationError(f"{flavor}: merged manifest missing: {manifest_path}")

    root = ET.parse(manifest_path).getroot()
    matches = [
        receiver
        for receiver in root.findall("./application/receiver")
        if receiver.get(f"{ANDROID}name") == receiver_name
    ]
    if len(matches) != 1:
        raise ManifestValidationError(
            f"{flavor}: expected exactly one {receiver_name}, found {len(matches)}",
        )

    receiver = matches[0]
    exported = receiver.get(f"{ANDROID}exported")
    if exported != "false":
        raise ManifestValidationError(
            f"{flavor}: {receiver_name} must be android:exported=\"false\", found {exported!r}",
        )
    if receiver.get(f"{ANDROID}enabled", "true") != "true":
        raise ManifestValidationError(f"{flavor}: {receiver_name} must remain enabled")
    if receiver.get(f"{ANDROID}permission") is not None:
        raise ManifestValidationError(
            f"{flavor}: normal-permission sender checks must not replace exported=false",
        )

    actions = {
        action.get(f"{ANDROID}name")
        for action in receiver.findall("./intent-filter/action")
    }
    actions.discard(None)
    if actions != EXPECTED_ACTIONS:
        missing = sorted(EXPECTED_ACTIONS - actions)
        unexpected = sorted(actions - EXPECTED_ACTIONS)
        raise ManifestValidationError(
            f"{flavor}: receiver action set mismatch; missing={missing}, unexpected={unexpected}",
        )

    return {
        "flavor": flavor,
        "manifest": str(manifest_path),
        "receiver": receiver_name,
        "exported": False,
        "actions": sorted(actions),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--zikirmatik-manifest", required=True, type=Path)
    parser.add_argument("--namazvakitleri-manifest", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    results = [
        validate_manifest("zikirmatik", args.zikirmatik_manifest),
        validate_manifest("namazvakitleri", args.namazvakitleri_manifest),
    ]
    report = {
        "status": "passed",
        "runtime_api_range": "24-37",
        "receivers": results,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("System receiver manifest validation passed: 2 flavors, exported=false, 4 actions each")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
