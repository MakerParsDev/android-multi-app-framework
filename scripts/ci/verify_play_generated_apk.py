#!/usr/bin/env python3
"""Read-only verification of Play-signed APKs and nonlocalized Firebase resources.

A successful publisher upload is not proof of Play approval or device delivery.
This audit runs *after* Google Play generates APKs. It never creates an edit,
changes a track, installs an APK, or logs credentials and API-key values.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import quote

from verify_play_console_access import (
    load_service_account_value,
    read_flavors,
    try_read_from_dotenv,
)

ROOT = Path(__file__).resolve().parents[2]
FIREBASE_FIELDS = (
    "google_app_id", "project_id", "gcm_defaultSenderId", "google_api_key",
    "google_storage_bucket", "firebase_database_url", "admob_app_id", "web_client_id",
)
MANDATORY = frozenset(("google_app_id", "project_id", "gcm_defaultSenderId", "google_api_key"))
RESOURCE_LINE = re.compile(r"^\s*resource 0x[0-9a-fA-F]+ string/([A-Za-z0-9_]+)(?:\s|$)")
CONFIG_VALUE_LINE = re.compile(r"^\s*\(([^)]*)\)\s+(.+)$")
QUOTED_STRING = re.compile(r'^"([^"]*)"$')
API_KEY_PATTERN = re.compile(r"AIza[A-Za-z0-9_-]{35}\Z")
MAX_APK_BYTES = 150 * 1024 * 1024


def parse_resources(output: str) -> dict[str, list[tuple[str, str | None]]]:
    """Track each resource configuration, even non-string references (fail closed)."""
    records: dict[str, list[tuple[str, str | None]]] = {}
    current: str | None = None
    for line in output.splitlines():
        if line.lstrip().startswith("resource "):
            match = RESOURCE_LINE.match(line)
            current = match.group(1) if match and match.group(1) in FIREBASE_FIELDS else None
            if current is not None:
                records.setdefault(current, [])
            continue
        if current:
            configuration = CONFIG_VALUE_LINE.match(line)
            if configuration:
                value = QUOTED_STRING.fullmatch(configuration.group(2))
                records[current].append((
                    configuration.group(1),
                    value.group(1) if value else None,
                ))
    return records


def validate_firebase_resources(records: dict[str, list[tuple[str, str | None]]],
                                expected: dict) -> dict[str, str]:
    """Reject any localized override, ambiguous default, or wrong Firebase app."""
    app_id = expected["appId"]
    project_id = expected["projectId"]
    sender = app_id.split(":")[1]
    expected_values = {"google_app_id": app_id, "project_id": project_id,
                       "gcm_defaultSenderId": sender}
    result: dict[str, str] = {}
    for field in FIREBASE_FIELDS:
        values = records.get(field, [])
        if not values:
            if field in MANDATORY:
                raise ValueError(f"missing required Firebase resource: {field}")
            result[field] = "absent (optional)"
            continue
        default = [value for locale, value in values if not locale]
        if len(default) != 1:
            raise ValueError(f"Firebase resource has no unique default: {field}")
        if len(values) != 1:
            raise ValueError(f"localized or duplicated Firebase resource: {field}")
        actual = default[0]
        if actual is None:
            raise ValueError(f"unsupported Firebase resource value: {field}")
        if field in expected_values and actual != expected_values[field]:
            raise ValueError(f"Firebase resource does not match project configuration: {field}")
        if field == "google_api_key" and not API_KEY_PATTERN.fullmatch(actual):
            raise ValueError("Firebase Android API-key resource is missing/invalid")
        result[field] = "verified"
    return result


def find_aapt2(explicit: str | None) -> str:
    if explicit:
        if not Path(explicit).is_file() and not shutil.which(explicit):
            raise ValueError("aapt2 executable not found")
        return explicit
    on_path = shutil.which("aapt2")
    if on_path:
        return on_path
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        found = sorted((Path(sdk) / "build-tools").glob("*/aapt2"))
        if found:
            return str(found[-1])
    raise ValueError("aapt2 is unavailable; set --aapt2 or ANDROID_HOME")


def audit_apk(apk_file: Path, aapt2: str, expected: dict) -> dict[str, str]:
    proc = subprocess.run(
        [aapt2, "dump", "resources", str(apk_file)],
        capture_output=True, text=True, timeout=120, check=False,
    )
    if proc.returncode:
        raise ValueError("Android resource table could not be decoded by aapt2")
    return validate_firebase_resources(parse_resources(proc.stdout), expected)


def choose_universal(metadata: dict) -> str:
    groups = metadata.get("generatedApks")
    if not isinstance(groups, list):
        raise ValueError("invalid generated APK metadata")
    ids = [str(group["generatedUniversalApk"]["downloadId"])
           for group in groups
           if isinstance(group, dict)
           and isinstance(group.get("generatedUniversalApk"), dict)
           and group["generatedUniversalApk"].get("downloadId")]
    if len(ids) != 1:
        raise ValueError("expected exactly one Play-generated universal signed APK")
    return ids[0]


def signed_apk_media_url(base: str, download_id: str) -> str:
    # Google returns an empty 204-style response without alt=media.
    return f"{base}/downloads/{quote(download_id, safe='')}:download?alt=media"


def download_play_signed_apk(package: str, version_code: int, destination: Path,
                             service_account: dict) -> None:
    """Use GET-only Google Play API operations, with no stateful edits."""
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account as google_service_account

    credentials = google_service_account.Credentials.from_service_account_info(
        service_account, scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)
    base = ("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
            f"{quote(package, safe='')}/generatedApks/{version_code}")
    try:
        response = session.get(base, timeout=60)
        response.raise_for_status()
        download_id = choose_universal(response.json())
        response = session.get(
            signed_apk_media_url(base, download_id),
            timeout=180, stream=True,
        )
        response.raise_for_status()
        length = 0
        with destination.open("wb") as f:
            for block in response.iter_content(chunk_size=1024 * 1024):
                length += len(block)
                if length > MAX_APK_BYTES:
                    raise ValueError("downloaded APK exceeded the size cap")
                f.write(block)
        if length == 0:
            raise ValueError("Google Play returned an empty APK")
    finally:
        session.close()


def apk_sha256(apk_file: Path) -> str:
    digest = hashlib.sha256()
    with apk_file.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main(argv: list[str] | None = None) -> int:
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--flavor", required=True, help="Known Android app flavor")
    cli.add_argument("--version-code", type=int, help="Play versionCode (required for API mode)")
    cli.add_argument("--apk", type=Path, help="Read-only offline APK audit; skips Play API")
    cli.add_argument("--aapt2", help="Path to Android build-tools aapt2")
    args = cli.parse_args(argv)
    try:
        catalog = read_flavors(ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt")
        if args.flavor not in catalog:
            raise ValueError("unknown flavor")
        expected = json.loads((ROOT / "config/firebase-apps.json").read_text())
        if args.flavor not in expected:
            raise ValueError("flavor has no expected Firebase configuration")
        aapt2 = find_aapt2(args.aapt2)
        if args.apk:
            if not args.apk.is_file():
                raise ValueError("APK file does not exist")
            result = audit_apk(args.apk, aapt2, expected[args.flavor])
            digest = apk_sha256(args.apk)
        else:
            if not args.version_code or args.version_code < 1:
                raise ValueError("positive --version-code is required for Play API mode")
            credential = (os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
                          or try_read_from_dotenv(ROOT))
            account = load_service_account_value(credential)
            with tempfile.TemporaryDirectory(prefix="play-apk-audit-") as temp:
                apk_file = Path(temp) / "play-signed.apk"
                download_play_signed_apk(catalog[args.flavor].package_name,
                                         args.version_code, apk_file, account)
                result = audit_apk(apk_file, aapt2, expected[args.flavor])
                digest = apk_sha256(apk_file)
        # Do not print raw API-key / auth-token / Firebase secret values.
        print(json.dumps({"status": "PASS", "flavor": args.flavor,
                          "versionCode": args.version_code,
                          "apkSha256": digest, "firebaseResources": result},
                         sort_keys=True))
        return 0
    except ValueError as error:
        # All ValueErrors above are authored with safe, non-secret messages.
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    except Exception:  # noqa: BLE001
        # Authentication / HTTP exception bodies may include credentials.
        print("FAIL: Play APK verification could not complete; inspect access "
              "and connectivity without logging credentials", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
