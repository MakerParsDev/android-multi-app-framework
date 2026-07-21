#!/usr/bin/env python3
"""
Validate Google Sign-In/Firebase config consistency per flavor.

Checks:
1) app/src/<flavor>/google-services.json exists and is valid JSON
2) Expected package name exists in google-services client list
3) Web OAuth client (client_type=3) exists and format looks valid
4) WEB_CLIENT_ID (CLI arg/env/local.properties) matches google-services web client IDs
5) config/firebase-apps.json contains flavor and matching appId/projectId

Usage:
  python scripts/ci/verify_google_signin_config.py
  python scripts/ci/verify_google_signin_config.py --flavors amenerrasulu,ayetelkursi
  python scripts/ci/verify_google_signin_config.py --flavors all --web-client-id xxx.apps.googleusercontent.com
  python scripts/ci/verify_google_signin_config.py --flavors all --require-web-client-id
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
from dataclasses import dataclass


NAMED_ARG_PATTERN = re.compile(r'(?P<key>\w+)\s*=\s*"(?P<value>[^"]+)"')
POSITIONAL_PATTERN = re.compile(r'"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"')
WEB_CLIENT_PATTERN = re.compile(r"^[0-9]+-[a-zA-Z0-9\-_]+\.apps\.googleusercontent\.com$")


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Google Sign-In config per flavor")
    parser.add_argument(
        "--flavors",
        default="all",
        help="Comma-separated flavor list or 'all' (default: all)",
    )
    parser.add_argument(
        "--web-client-id",
        default="",
        help="WEB_CLIENT_ID override (takes precedence over env WEB_CLIENT_ID/FIREBASE_WEB_CLIENT_ID/local.properties)",
    )
    parser.add_argument(
        "--require-web-client-id",
        action="store_true",
        help="Fail when WEB_CLIENT_ID cannot be resolved",
    )
    return parser.parse_args()


def read_flavors(flavor_file: pathlib.Path) -> dict[str, FlavorInfo]:
    text = flavor_file.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}

    for block in extract_flavor_blocks(text):
        flavor = parse_flavor_block(block)
        if flavor is None:
            continue
        result[flavor.name] = flavor

    if not result:
        raise RuntimeError(f"No flavors parsed from {flavor_file}")
    return result


def extract_flavor_blocks(text: str) -> list[str]:
    blocks: list[str] = []
    marker = "FlavorConfig("
    search_from = 0

    while True:
        start = text.find(marker, search_from)
        if start == -1:
            break

        index = start + len("FlavorConfig")
        depth = 0
        in_string = False
        escaped = False

        while index < len(text):
            char = text[index]

            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
            else:
                if char == '"':
                    in_string = True
                elif char == "(":
                    depth += 1
                elif char == ")":
                    depth -= 1
                    if depth == 0:
                        blocks.append(text[start : index + 1])
                        search_from = index + 1
                        break
            index += 1
        else:
            break

    return blocks


def parse_flavor_block(block: str) -> FlavorInfo | None:
    named_values = {
        match.group("key").strip(): match.group("value").strip()
        for match in NAMED_ARG_PATTERN.finditer(block)
    }

    if named_values.get("name") and named_values.get("packageName"):
        return FlavorInfo(
            name=named_values["name"],
            package_name=named_values["packageName"],
        )

    positional_match = POSITIONAL_PATTERN.search(block)
    if positional_match:
        return FlavorInfo(
            name=positional_match.group("name").strip(),
            package_name=positional_match.group("pkg").strip(),
        )

    return None


def read_local_web_client_id(local_properties: pathlib.Path) -> str:
    if not local_properties.exists():
        return ""
    for raw_line in local_properties.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == "WEB_CLIENT_ID":
            return value.strip()
    return ""


def parse_json(path: pathlib.Path) -> dict:
    with path.open("r", encoding="utf-8") as fp:
        return json.load(fp)


def extract_clients(google_services: dict, package_name: str) -> tuple[list[dict], list[str]]:
    clients = google_services.get("client") or []
    if not isinstance(clients, list):
        return [], []

    matching_clients: list[dict] = []
    all_web_ids: list[str] = []

    for client in clients:
        oauth = client.get("oauth_client") or []
        for oauth_client in oauth:
            if oauth_client.get("client_type") == 3 and oauth_client.get("client_id"):
                all_web_ids.append(str(oauth_client["client_id"]).strip())

        android_info = (
            ((client.get("client_info") or {}).get("android_client_info") or {})
        )
        if android_info.get("package_name") == package_name:
            matching_clients.append(client)

    unique_web_ids = sorted({item for item in all_web_ids if item})
    return matching_clients, unique_web_ids


def verify_flavor(
    repo_root: pathlib.Path,
    flavor: FlavorInfo,
    expected_web_client_id: str,
    firebase_map: dict,
) -> list[str]:
    errors: list[str] = []
    flavor_prefix = f"[{flavor.name}]"

    google_services_path = repo_root / "app" / "src" / flavor.name / "google-services.json"
    if not google_services_path.exists():
        errors.append(f"{flavor_prefix} Missing file: {google_services_path.as_posix()}")
        return errors

    try:
        google_services = parse_json(google_services_path)
    except Exception as exc:  # noqa: BLE001
        errors.append(f"{flavor_prefix} Invalid JSON ({google_services_path.name}): {exc}")
        return errors

    matching_clients, all_web_ids = extract_clients(google_services, flavor.package_name)
    if not matching_clients:
        errors.append(
            f"{flavor_prefix} Package not found in google-services clients: {flavor.package_name}"
        )
        return errors

    if not all_web_ids:
        errors.append(f"{flavor_prefix} Missing OAuth web client (client_type=3)")
    else:
        malformed = [cid for cid in all_web_ids if not WEB_CLIENT_PATTERN.match(cid)]
        if malformed:
            errors.append(f"{flavor_prefix} Invalid web client format: {malformed[0]}")

    if expected_web_client_id:
        if expected_web_client_id not in all_web_ids:
            errors.append(
                f"{flavor_prefix} WEB_CLIENT_ID does not exist in {google_services_path.name}"
            )

    flavor_firebase = firebase_map.get(flavor.name)
    if not isinstance(flavor_firebase, dict):
        errors.append(f"{flavor_prefix} Missing entry in config/firebase-apps.json")
        return errors

    expected_project_id = str(flavor_firebase.get("projectId") or "").strip()
    expected_app_id = str(flavor_firebase.get("appId") or "").strip()
    if not expected_project_id:
        errors.append(f"{flavor_prefix} Missing projectId in config/firebase-apps.json")
    if not expected_app_id:
        errors.append(f"{flavor_prefix} Missing appId in config/firebase-apps.json")

    actual_project_id = str((google_services.get("project_info") or {}).get("project_id") or "").strip()
    if expected_project_id and actual_project_id and expected_project_id != actual_project_id:
        errors.append(
            f"{flavor_prefix} projectId mismatch. expected={expected_project_id} actual={actual_project_id}"
        )

    app_ids_for_package: set[str] = set()
    for client in matching_clients:
        app_id = str(((client.get("client_info") or {}).get("mobilesdk_app_id") or "")).strip()
        if app_id:
            app_ids_for_package.add(app_id)
    if expected_app_id and expected_app_id not in app_ids_for_package:
        errors.append(
            f"{flavor_prefix} appId mismatch. expected={expected_app_id} actualCandidates={sorted(app_ids_for_package)}"
        )

    return errors


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]

    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    local_properties = repo_root / "local.properties"
    firebase_apps = repo_root / "config" / "firebase-apps.json"

    try:
        all_flavors = read_flavors(flavor_file)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: Failed to parse flavors: {exc}")
        return 1

    requested = [f.strip() for f in args.flavors.split(",") if f.strip()]
    if not requested or requested == ["all"]:
        selected = list(all_flavors.values())
    else:
        unknown = [name for name in requested if name not in all_flavors]
        if unknown:
            print(f"ERROR: Unknown flavors: {', '.join(unknown)}")
            print("Allowed:", ", ".join(sorted(all_flavors)))
            return 1
        selected = [all_flavors[name] for name in requested]

    if not firebase_apps.exists():
        print(f"ERROR: Missing file: {firebase_apps.as_posix()}")
        return 1
    try:
        firebase_map = parse_json(firebase_apps)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: Invalid config/firebase-apps.json: {exc}")
        return 1

    web_client_id = args.web_client_id.strip()
    if not web_client_id:
        web_client_id = os.environ.get("WEB_CLIENT_ID", "").strip()
    if not web_client_id:
        web_client_id = os.environ.get("FIREBASE_WEB_CLIENT_ID", "").strip()
    if not web_client_id:
        web_client_id = read_local_web_client_id(local_properties).strip()

    if not web_client_id:
        if args.require_web_client_id:
            print(
                "ERROR: WEB_CLIENT_ID is required but missing "
                "(checked: --web-client-id, env WEB_CLIENT_ID, env FIREBASE_WEB_CLIENT_ID, local.properties)"
            )
            return 1
        print("WARN: WEB_CLIENT_ID unresolved (cross-check skipped)")
    elif not WEB_CLIENT_PATTERN.match(web_client_id):
        print("ERROR: WEB_CLIENT_ID has invalid format")
        return 1

    print(f"Checking {len(selected)} flavor(s): {', '.join(f.name for f in selected)}")
    all_errors: list[str] = []
    for flavor in selected:
        all_errors.extend(
            verify_flavor(
                repo_root=repo_root,
                flavor=flavor,
                expected_web_client_id=web_client_id,
                firebase_map=firebase_map,
            )
        )

    if all_errors:
        print("\nFAILED: Google Sign-In configuration issues found:")
        for err in all_errors:
            print(f"- {err}")
        return 1

    print("OK: Google Sign-In configuration is valid for selected flavor(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
