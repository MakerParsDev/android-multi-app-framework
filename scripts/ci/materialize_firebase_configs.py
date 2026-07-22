#!/usr/bin/env python3
"""Safely materialize Firebase google-services.json files for selected flavors.

Inputs:
- FIREBASE_CONFIGS_ZIP_BASE64: base64-encoded zip containing either
  app/src/<flavor>/google-services.json or <flavor>/google-services.json entries.

The script never extracts the zip wholesale. It only writes whitelisted
app/src/<flavor>/google-services.json files after JSON/package validation.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import io
import json
import os
import pathlib
import re
import stat
import sys
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from verify_google_signin_config import extract_clients, parse_json, read_flavors  # noqa: E402

AZURE_UNRESOLVED_PATTERN = re.compile(r"^\$\([^)]*\)$")
DEFAULT_ENV_VAR = "FIREBASE_CONFIGS_ZIP_BASE64"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Materialize Firebase google-services.json files")
    parser.add_argument("--flavors", default="all", help="Comma-separated flavor list or 'all'")
    parser.add_argument("--env-var", default=DEFAULT_ENV_VAR, help="Base64 zip env var name")
    parser.add_argument(
        "--zip-file",
        type=pathlib.Path,
        help="Private Firebase config zip file (mutually exclusive with the base64 env source)",
    )
    parser.add_argument("--mode", choices=("warn", "strict"), default="strict")
    parser.add_argument("--allow-existing", action="store_true")
    return parser.parse_args()


def is_config_source_set(value: str) -> bool:
    value = value.strip()
    return bool(value) and not AZURE_UNRESOLVED_PATTERN.match(value)


def selected_flavors(repo_root: pathlib.Path, flavors_arg: str):
    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    all_flavors = read_flavors(flavor_file)
    requested = [item.strip() for item in flavors_arg.split(",") if item.strip()]
    if not requested or requested == ["all"]:
        return list(all_flavors.values())
    unknown = [name for name in requested if name not in all_flavors]
    if unknown:
        allowed = ", ".join(sorted(all_flavors))
        raise RuntimeError(f"Unknown flavor(s): {', '.join(unknown)}. Allowed: {allowed}")
    return [all_flavors[name] for name in requested]


def decode_zip(source_value: str, source_name: str) -> zipfile.ZipFile:
    try:
        raw = base64.b64decode(source_value, validate=True)
    except binascii.Error as exc:
        raise RuntimeError(f"{source_name} is not valid base64") from exc
    try:
        return zipfile.ZipFile(io.BytesIO(raw))
    except zipfile.BadZipFile as exc:
        raise RuntimeError(f"{source_name} is not a valid zip archive") from exc


def candidate_members(flavor: str) -> list[str]:
    return [
        f"app/src/{flavor}/google-services.json",
        f"./app/src/{flavor}/google-services.json",
        f"{flavor}/google-services.json",
        f"./{flavor}/google-services.json",
        f"google-services/{flavor}.json",
        f"./google-services/{flavor}.json",
    ]


def read_flavor_json_from_zip(archive: zipfile.ZipFile, flavor: str) -> bytes | None:
    names = set(archive.namelist())
    for member in candidate_members(flavor):
        if member in names:
            info = archive.getinfo(member)
            if info.is_dir():
                continue
            return archive.read(info)
    return None


def validate_google_services(flavor_name: str, package_name: str, raw_json: bytes, firebase_map: dict) -> str:
    try:
        payload = json.loads(raw_json.decode("utf-8"))
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(f"[{flavor_name}] invalid google-services.json JSON: {exc}") from exc

    matching_clients, web_ids = extract_clients(payload, package_name)
    if not matching_clients:
        raise RuntimeError(f"[{flavor_name}] package not found in client list: {package_name}")
    if not web_ids:
        raise RuntimeError(f"[{flavor_name}] missing OAuth web client (client_type=3)")

    expected = firebase_map.get(flavor_name)
    if not isinstance(expected, dict):
        raise RuntimeError(f"[{flavor_name}] missing config/firebase-apps.json entry")

    expected_project_id = str(expected.get("projectId") or "").strip()
    expected_app_id = str(expected.get("appId") or "").strip()
    actual_project_id = str((payload.get("project_info") or {}).get("project_id") or "").strip()
    if expected_project_id and actual_project_id != expected_project_id:
        raise RuntimeError(f"[{flavor_name}] projectId mismatch")

    actual_app_ids = {
        str(((client.get("client_info") or {}).get("mobilesdk_app_id") or "")).strip()
        for client in matching_clients
    }
    actual_app_ids.discard("")
    if expected_app_id and expected_app_id not in actual_app_ids:
        raise RuntimeError(f"[{flavor_name}] Firebase appId mismatch")

    return json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def write_private_file(path: pathlib.Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fp:
        fp.write(content)
    try:
        path.chmod(stat.S_IRUSR | stat.S_IWUSR)
    except PermissionError:
        pass


def fail_or_warn(message: str, mode: str) -> int:
    prefix = "ERROR" if mode == "strict" else "WARN"
    print(f"{prefix}: {message}")
    return 1 if mode == "strict" else 0


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    os.chdir(repo_root)

    try:
        flavors = selected_flavors(repo_root, args.flavors)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1

    source_value = os.environ.get(args.env_var, "")
    env_source_set = is_config_source_set(source_value)
    raw_zip_source = args.zip_file
    zip_source = raw_zip_source.resolve() if raw_zip_source is not None else None
    if env_source_set and zip_source is not None:
        print(f"ERROR: use either {args.env_var} or --zip-file, not both")
        return 1
    if zip_source is not None and (
        raw_zip_source is None or raw_zip_source.is_symlink() or not zip_source.is_file()
    ):
        print(f"ERROR: --zip-file must be an existing non-symlink regular file: {zip_source}")
        return 1

    missing_existing = [
        flavor.name
        for flavor in flavors
        if not (repo_root / "app" / "src" / flavor.name / "google-services.json").exists()
    ]

    if not env_source_set and zip_source is None:
        if args.allow_existing and not missing_existing:
            print(f"OK: Firebase configs already exist for {len(flavors)} flavor(s).")
            return 0
        missing_display = ", ".join(missing_existing or [flavor.name for flavor in flavors])
        return fail_or_warn(
            f"neither {args.env_var} nor --zip-file is set and google-services.json is missing for: {missing_display}",
            args.mode,
        )

    try:
        firebase_map = parse_json(repo_root / "config" / "firebase-apps.json")
        archive = (
            zipfile.ZipFile(zip_source)
            if zip_source is not None
            else decode_zip(source_value, args.env_var)
        )
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1

    written = []
    missing_from_zip = []
    try:
        for flavor in flavors:
            raw = read_flavor_json_from_zip(archive, flavor.name)
            if raw is None:
                missing_from_zip.append(flavor.name)
                continue
            rendered = validate_google_services(flavor.name, flavor.package_name, raw, firebase_map)
            target = repo_root / "app" / "src" / flavor.name / "google-services.json"
            write_private_file(target, rendered)
            written.append(target.relative_to(repo_root).as_posix())
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1
    finally:
        archive.close()

    if missing_from_zip:
        return fail_or_warn(
            f"{args.env_var} does not contain google-services.json for: {', '.join(missing_from_zip)}",
            args.mode,
        )

    print(f"OK: Materialized Firebase configs for {len(written)} flavor(s).")
    for path in written:
        print(f"- {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
