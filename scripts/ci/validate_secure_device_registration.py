#!/usr/bin/env python3
"""Enforce backend-only, App Check-protected device registration."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from dataclasses import dataclass
from pathlib import Path

REQUIRED_PAYLOAD_FIELDS = (
    "installationId",
    "fcmToken",
    "tokenHash",
    "adRuntimeFunnelCounts",
    "adRuntimeSuppressReasonCounts",
)


@dataclass(frozen=True)
class ValidationResult:
    files_checked: int
    payload_fields: int


def _single(root: Path, pattern: str, label: str) -> Path:
    matches = sorted(root.glob(pattern))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {label}; found {len(matches)}")
    return matches[0]


def validate(root: Path) -> ValidationResult:
    module_path = _single(root, "core/firebase/src/main/java/**/di/PushRegistrationModule.kt", "push DI module")
    sender_path = _single(
        root,
        "core/firebase/src/main/java/**/push/HttpPushRegistrationSender.kt",
        "HTTP push registration sender",
    )
    worker_path = root / "side-projects/cloudflare/workers/admin-api/src/index.ts"
    handler_path = root / "side-projects/cloudflare/workers/admin-api/src/deviceRegistration.ts"
    wrangler_path = root / "side-projects/cloudflare/workers/admin-api/wrangler.toml"
    rules_path = root / "side-projects/firebase/firestore.rules"
    required_paths = (module_path, sender_path, worker_path, handler_path, wrangler_path, rules_path)
    for path in required_paths:
        if not path.is_file():
            raise ValueError(f"Required secure device registration file is missing: {path.relative_to(root)}")

    errors: list[str] = []
    module = module_path.read_text(encoding="utf-8")
    sender = sender_path.read_text(encoding="utf-8")
    worker = worker_path.read_text(encoding="utf-8")
    handler = handler_path.read_text(encoding="utf-8")
    rules = rules_path.read_text(encoding="utf-8")
    wrangler = tomllib.loads(wrangler_path.read_text(encoding="utf-8"))

    if "HttpPushRegistrationSender" not in module or "FirestorePushRegistrationSender" in module:
        errors.append("Hilt must bind HttpPushRegistrationSender and must not bind FirestorePushRegistrationSender")

    firestore_senders = sorted(
        root.glob("core/firebase/src/main/java/**/push/FirestorePushRegistrationSender.kt")
    )
    if firestore_senders:
        errors.append("FirestorePushRegistrationSender must not exist in production sources")

    if "X-Firebase-AppCheck" not in sender:
        errors.append("HttpPushRegistrationSender must send X-Firebase-AppCheck")
    missing_fields = [field for field in REQUIRED_PAYLOAD_FIELDS if f'"{field}"' not in sender]
    if missing_fields:
        errors.append(f"HTTP registration payload is missing fields: {', '.join(missing_fields)}")

    if '"/registerDevice"' not in worker or "handleRegisterDevice" not in worker:
        errors.append("Worker must expose the /registerDevice route through handleRegisterDevice")
    if "handleDeviceRegistration" not in handler:
        errors.append("Worker device registration handler must exist")

    vars_config = wrangler.get("vars")
    require_app_check = ""
    if isinstance(vars_config, dict):
        require_app_check = str(vars_config.get("REGISTER_DEVICE_REQUIRE_APP_CHECK", "")).strip().lower()
    if require_app_check != "true":
        errors.append("REGISTER_DEVICE_REQUIRE_APP_CHECK must be true")

    devices_match = re.search(
        r"match\s+/devices/\{deviceId\}\s*\{(?P<body>.*?)\n\s*\}",
        rules,
        flags=re.DOTALL,
    )
    if devices_match is None:
        errors.append("Firestore rules must define /devices/{deviceId}")
    else:
        devices_body = devices_match.group("body")
        if "allow write: if false;" not in devices_body or re.search(
            r"allow\s+(?:create|update|delete)(?:\s*,\s*(?:create|update|delete))*\s*:",
            devices_body,
        ):
            errors.append("Firestore /devices rules must deny every client write")

    if errors:
        raise ValueError("\n".join(errors))

    return ValidationResult(files_checked=len(required_paths), payload_fields=len(REQUIRED_PAYLOAD_FIELDS))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("build/reports/security/secure-device-registration.json"),
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        result = validate(args.root.resolve())
    except (OSError, tomllib.TOMLDecodeError, ValueError) as error:
        print(f"Secure device registration validation failed:\n{error}", file=sys.stderr)
        return 1

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(
            {
                "files_checked": result.files_checked,
                "payload_fields": result.payload_fields,
                "status": "passed",
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        "Secure device registration validation passed: "
        f"files={result.files_checked} payload_fields={result.payload_fields}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
