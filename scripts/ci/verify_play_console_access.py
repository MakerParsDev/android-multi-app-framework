#!/usr/bin/env python3
"""
Verify that the Play Console service account can access required Android Publisher API endpoints.

This does NOT "read permissions" from Play Console (Google doesn't expose that directly).
Instead, it attempts a few representative API calls per package and reports what succeeded.

Why this matters:
- Uploading bundles/APKs requires release permissions (tracks).
- Uploading listing/metadata/screenshots/release-notes requires store presence permissions (listings/images).

Usage:
  python scripts/ci/verify_play_console_access.py --flavors amenerrasulu,ayetelkursi
  python scripts/ci/verify_play_console_access.py --flavors all

Auth:
  The script reads PLAY_SERVICE_ACCOUNT_JSON from env.
  - If it is a path, it loads the service account JSON file.
  - If it is inline JSON, it loads it from memory.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import sys
from dataclasses import dataclass


FLAVOR_PATTERN = re.compile(
    r'FlavorConfig\(\s*"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"'
)


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify Play Console API access per flavor")
    parser.add_argument(
        "--flavors",
        default="all",
        help="Comma-separated flavor list or 'all' (default: all)",
    )
    return parser.parse_args()


def read_flavors(flavor_file: pathlib.Path) -> dict[str, FlavorInfo]:
    text = flavor_file.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}
    for match in FLAVOR_PATTERN.finditer(text):
        name = match.group("name").strip()
        pkg = match.group("pkg").strip()
        result[name] = FlavorInfo(name=name, package_name=pkg)
    if not result:
        raise RuntimeError(f"No flavors parsed from {flavor_file}")
    return result


def load_service_account_value(raw: str) -> dict:
    raw = (raw or "").strip()
    if not raw:
        raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON is missing/empty")

    # Path?
    p = pathlib.Path(raw)
    if p.exists() and p.is_file():
        return json.loads(p.read_text(encoding="utf-8"))

    # Inline JSON?
    if raw.startswith("{") and raw.endswith("}"):
        return json.loads(raw)

    raise RuntimeError(
        "PLAY_SERVICE_ACCOUNT_JSON must be a file path or inline JSON content"
    )

def try_read_from_dotenv(repo_root: pathlib.Path) -> str:
    """
    Convenience for local runs: if PLAY_SERVICE_ACCOUNT_JSON isn't set in env,
    try to read it from the repo's .env file.

    Supported:
      KEY=value
      KEY="value"
    """
    env_path = repo_root / ".env"
    if not env_path.exists():
        return ""

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != "PLAY_SERVICE_ACCOUNT_JSON":
            continue
        v = value.strip()
        if len(v) >= 2 and v[0] == '"' and v[-1] == '"':
            v = v[1:-1]
        return v.strip()
    return ""


def build_android_publisher(service_account_info: dict):
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except Exception as exc:  # noqa: BLE001
        raise RuntimeError(
            "Missing deps. Install: python -m pip install google-api-python-client google-auth"
        ) from exc

    creds = service_account.Credentials.from_service_account_info(
        service_account_info,
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    # cache_discovery=False avoids writing cache files on CI agents.
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def try_execute(label: str, fn) -> tuple[bool, str]:
    try:
        fn()
        return True, ""
    except Exception as exc:  # noqa: BLE001
        msg = str(exc)
        # Keep output short and avoid leaking details.
        return False, f"{label}: {msg.splitlines()[0][:240]}"


def check_package(service, package_name: str) -> dict:
    result: dict[str, object] = {
        "package": package_name,
        "can_create_edit": False,
        "can_list_tracks": False,
        "can_list_listings": False,
        "can_list_images": None,  # None = not checked (no listing language to use)
        "errors": [],
    }

    edit_id: str | None = None

    def create_edit():
        nonlocal edit_id
        resp = service.edits().insert(packageName=package_name, body={}).execute()
        edit_id = str(resp.get("id") or "")
        if not edit_id:
            raise RuntimeError("Missing edit id in response")

    ok, err = try_execute("edits.insert", create_edit)
    result["can_create_edit"] = ok
    if not ok:
        result["errors"].append(err)
        return result

    assert edit_id is not None

    ok, err = try_execute(
        "edits.tracks.list",
        lambda: service.edits().tracks().list(packageName=package_name, editId=edit_id).execute(),
    )
    result["can_list_tracks"] = ok
    if not ok:
        result["errors"].append(err)

    listings_resp: dict | None = None

    def list_listings():
        nonlocal listings_resp
        listings_resp = service.edits().listings().list(
            packageName=package_name,
            editId=edit_id,
        ).execute()

    ok, err = try_execute("edits.listings.list", list_listings)
    result["can_list_listings"] = ok
    if not ok:
        result["errors"].append(err)
    else:
        # Screenshots/graphics are under edits.images.* and require a listing language.
        # Pick the first available language from listings.
        language = None
        try:
            listings = (listings_resp or {}).get("listings") or []
            if isinstance(listings, list) and listings:
                language = str((listings[0] or {}).get("language") or "").strip() or None
        except Exception:
            language = None

        if language:
            ok, err = try_execute(
                "edits.images.list(phoneScreenshots)",
                lambda: service.edits().images().list(
                    packageName=package_name,
                    editId=edit_id,
                    language=language,
                    imageType="phoneScreenshots",
                ).execute(),
            )
            result["can_list_images"] = ok
            if not ok:
                result["errors"].append(err)

    # Cleanup edit to avoid leaving uncommitted edits around.
    try:
        service.edits().delete(packageName=package_name, editId=edit_id).execute()
    except Exception:
        pass

    return result


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"

    all_flavors = read_flavors(flavor_file)

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

    is_ci = os.environ.get("CI", "").strip().lower() == "true"
    raw_sa = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw_sa and not is_ci:
        raw_sa = try_read_from_dotenv(repo_root)
    try:
        sa_info = load_service_account_value(raw_sa)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1

    try:
        service = build_android_publisher(sa_info)
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: {exc}")
        return 1

    print(f"Checking Play API access for {len(selected)} flavor(s)...")
    failures = 0
    for f in selected:
        r = check_package(service, f.package_name)
        ok_tracks = bool(r["can_list_tracks"])
        ok_listings = bool(r["can_list_listings"])
        ok_images = r.get("can_list_images")
        images_str = "n/a" if ok_images is None else str(bool(ok_images))
        status = "OK" if (ok_tracks and ok_listings) else "FAIL"
        print(
            f"- {status} {f.name} ({f.package_name}) tracks={ok_tracks} listings={ok_listings} images={images_str}"
        )
        if r["errors"]:
            failures += 1
            for e in r["errors"]:
                print(f"  - {e}")

    if failures:
        print(
            "\nFAILED: Some packages are missing access.\n"
            "Hint: In Play Console -> Users and permissions, ensure the service account has:\n"
            "- Release permissions (for bundle upload / tracks)\n"
            "- Store presence permissions (for listing/graphics/release notes)\n"
        )
        return 1

    print("OK: Service account can access tracks + listings for selected flavor(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
