#!/usr/bin/env python3
"""
Fetch current versionCodes from Google Play Console (Android Publisher API) per flavor/package.

Why:
- Each applicationId (each flavor) must have monotonically increasing versionCode on Play.
- This script helps you discover the current max versionCode per app so you can set per-flavor
  values in app-versions.properties safely.

Notes:
- The Android Publisher API exposes versionCodes; it does not reliably expose the Android
  "versionName" displayed in Play Console. This script focuses on versionCode.

Usage:
  python scripts/ci/fetch_play_version_codes.py --flavors all
  python scripts/ci/fetch_play_version_codes.py --flavors amenerrasulu,ayetelkursi --tracks production,internal

Auth:
  Reads PLAY_SERVICE_ACCOUNT_JSON from env (path or inline JSON). If missing, tries repo .env.
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


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


@dataclass(frozen=True)
class VersionGateResult:
    flavor: str
    package_name: str
    repo_version_code: int | None
    play_max_version_code: int | None
    max_tracks: tuple[str, ...]
    status: str
    message: str = ""

    @property
    def passed(self) -> bool:
        return self.status in {"OK", "NEW_APP"}


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Fetch Play Console versionCodes per flavor/package")
    p.add_argument(
        "--flavors",
        default="all",
        help="Comma-separated flavor list or 'all' (default: all)",
    )
    p.add_argument(
        "--tracks",
        default="all",
        help="Comma-separated tracks to consider or 'all' (default: all)",
    )
    p.add_argument(
        "--out-json",
        default="TEMP_OUT/play_version_codes.json",
        help="Output JSON file path (default: TEMP_OUT/play_version_codes.json)",
    )
    p.add_argument(
        "--out-props",
        default="TEMP_OUT/app-versions.properties.suggested",
        help="Output suggested properties file path (default: TEMP_OUT/app-versions.properties.suggested)",
    )
    p.add_argument(
        "--suggest-next",
        action="store_true",
        help="Write suggested next versionCode (= max + 1) to the properties output",
    )
    p.add_argument(
        "--apply-to-app-versions",
        action="store_true",
        help="Apply resolved versionCode values to app-versions.properties",
    )
    p.add_argument(
        "--sync-version-names",
        action="store_true",
        help="When applying, also sync <flavor>.versionName to match target versionCode (e.g. 1.0.<code>)",
    )
    p.add_argument(
        "--app-versions-file",
        default="app-versions.properties",
        help="Path to app-versions.properties (default: app-versions.properties)",
    )
    p.add_argument(
        "--verify-greater-than-play",
        action="store_true",
        help=(
            "Fail unless every selected repo versionCode is strictly greater than the maximum "
            "versionCode found on the selected Play tracks"
        ),
    )
    return p.parse_args()


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


def try_read_from_dotenv(repo_root: pathlib.Path) -> str:
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


def load_service_account_value(raw: str) -> dict:
    raw = (raw or "").strip()
    if not raw:
        raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON is missing/empty")

    p = pathlib.Path(raw)
    if p.exists() and p.is_file():
        return json.loads(p.read_text(encoding="utf-8"))

    if raw.startswith("{") and raw.endswith("}"):
        return json.loads(raw)

    raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON must be a file path or inline JSON content")


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
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def to_int_list(values) -> list[int]:
    out: list[int] = []
    for v in values or []:
        try:
            out.append(int(v))
        except Exception:
            continue
    return out


def fetch_tracks_version_codes(service, package_name: str) -> dict[str, list[int]]:
    """
    Returns mapping: track -> sorted versionCodes (unique) found in track releases.
    Uses an edit to access edits.tracks.list.
    """
    resp = service.edits().insert(packageName=package_name, body={}).execute()
    edit_id = str(resp.get("id") or "").strip()
    if not edit_id:
        raise RuntimeError("Missing edit id in response")

    try:
        tracks_resp = service.edits().tracks().list(packageName=package_name, editId=edit_id).execute()
        tracks = tracks_resp.get("tracks") or []
        result: dict[str, list[int]] = {}
        for t in tracks:
            track_name = str(t.get("track") or "").strip()
            releases = t.get("releases") or []
            codes: list[int] = []
            for r in releases:
                codes.extend(to_int_list(r.get("versionCodes")))
            codes = sorted(set(codes))
            if track_name:
                result[track_name] = codes
        return result
    finally:
        # Cleanup edit to avoid leaving uncommitted edits around.
        try:
            service.edits().delete(packageName=package_name, editId=edit_id).execute()
        except Exception:
            pass


def ensure_parent(path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def read_version_codes(path: pathlib.Path) -> dict[str, int]:
    if not path.exists():
        raise RuntimeError(f"app-versions.properties not found: {path}")

    result: dict[str, int] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, raw_value = line.split("=", 1)
        key = key.strip()
        if not key.endswith(".versionCode"):
            continue
        flavor = key.removesuffix(".versionCode").strip()
        if not flavor:
            raise RuntimeError(f"Invalid versionCode key at {path}:{line_number}")
        try:
            value = int(raw_value.strip())
        except ValueError as exc:
            raise RuntimeError(
                f"Invalid integer for {key} at {path}:{line_number}: {raw_value.strip()!r}"
            ) from exc
        if value <= 0:
            raise RuntimeError(f"{key} must be > 0 at {path}:{line_number} (resolved={value})")
        if flavor in result:
            raise RuntimeError(f"Duplicate {key} at {path}:{line_number}")
        result[flavor] = value
    return result


def evaluate_version_gate(
    selected: list[FlavorInfo],
    out_apps: dict,
    repo_version_codes: dict[str, int],
) -> list[VersionGateResult]:
    results: list[VersionGateResult] = []
    for flavor in selected:
        app = out_apps.get(flavor.name) or {}
        repo_code = repo_version_codes.get(flavor.name)
        if repo_code is None:
            results.append(
                VersionGateResult(
                    flavor=flavor.name,
                    package_name=flavor.package_name,
                    repo_version_code=None,
                    play_max_version_code=None,
                    max_tracks=(),
                    status="MISSING_REPO_CODE",
                    message=f"Missing {flavor.name}.versionCode",
                )
            )
            continue

        if "error" in app:
            results.append(
                VersionGateResult(
                    flavor=flavor.name,
                    package_name=flavor.package_name,
                    repo_version_code=repo_code,
                    play_max_version_code=None,
                    max_tracks=(),
                    status="LOOKUP_ERROR",
                    message=str(app["error"]),
                )
            )
            continue

        play_max = app.get("maxVersionCode")
        if play_max is None:
            results.append(
                VersionGateResult(
                    flavor=flavor.name,
                    package_name=flavor.package_name,
                    repo_version_code=repo_code,
                    play_max_version_code=None,
                    max_tracks=(),
                    status="NEW_APP",
                    message="No versionCode exists on selected Play tracks",
                )
            )
            continue

        play_max = int(play_max)
        tracks = app.get("tracks") or {}
        max_tracks = tuple(
            sorted(
                track_name
                for track_name, codes in tracks.items()
                if play_max in to_int_list(codes)
            )
        )
        passed = repo_code > play_max
        results.append(
            VersionGateResult(
                flavor=flavor.name,
                package_name=flavor.package_name,
                repo_version_code=repo_code,
                play_max_version_code=play_max,
                max_tracks=max_tracks,
                status="OK" if passed else "BLOCKED",
                message=(
                    ""
                    if passed
                    else f"repo versionCode must be at least {play_max + 1}"
                ),
            )
        )
    return results


def print_version_gate_summary(results: list[VersionGateResult]) -> None:
    headers = ("flavor", "repo", "play_max", "max_track", "status")
    rows: list[tuple[str, str, str, str, str]] = []
    for result in results:
        rows.append(
            (
                result.flavor,
                "-" if result.repo_version_code is None else str(result.repo_version_code),
                "-" if result.play_max_version_code is None else str(result.play_max_version_code),
                ",".join(result.max_tracks) or "-",
                result.status,
            )
        )

    widths = [
        max(len(headers[index]), *(len(row[index]) for row in rows)) if rows else len(headers[index])
        for index in range(len(headers))
    ]

    def format_row(columns: tuple[str, str, str, str, str]) -> str:
        return " | ".join(columns[index].ljust(widths[index]) for index in range(len(columns)))

    print("")
    print("Play versionCode release gate")
    print(format_row(headers))
    print("-+-".join("-" * width for width in widths))
    for row in rows:
        print(format_row(row))
    print("")

    for result in results:
        if not result.passed and result.message:
            print(f"ERROR: {result.flavor}: {result.message}")


def print_version_code_summary(
    selected: list[FlavorInfo],
    out_apps: dict,
    use_next: bool,
) -> None:
    rows: list[tuple[str, str, str, str, str]] = []
    for fi in selected:
        app = out_apps.get(fi.name) or {}
        if "error" in app:
            rows.append((fi.name, fi.package_name, "-", "-", f"ERROR: {app['error']}"))
            continue
        max_code = app.get("maxVersionCode")
        if max_code is None:
            rows.append((fi.name, fi.package_name, "-", "-", "NO_CODES"))
            continue
        target_code = int(max_code) + 1 if use_next else int(max_code)
        rows.append((fi.name, fi.package_name, str(max_code), str(target_code), "OK"))

    headers = ("flavor", "package", "max_on_play", "target_code", "status")
    widths = [
        max(len(headers[i]), *(len(r[i]) for r in rows)) if rows else len(headers[i])
        for i in range(len(headers))
    ]

    def fmt(cols: tuple[str, str, str, str, str]) -> str:
        return " | ".join(cols[i].ljust(widths[i]) for i in range(len(cols)))

    print("")
    print("Play versionCode summary")
    print(fmt(headers))
    print("-+-".join("-" * w for w in widths))
    for row in rows:
        print(fmt(row))
    print("")


def apply_codes_to_app_versions(
    app_versions_file: pathlib.Path,
    selected: list[FlavorInfo],
    out_apps: dict,
    use_next: bool,
    sync_version_names: bool,
) -> tuple[list[str], list[str]]:
    """
    Updates <flavor>.versionCode in app-versions.properties.
    Returns (updated_flavors, skipped_flavors).
    """
    if not app_versions_file.exists():
        raise RuntimeError(f"app-versions.properties not found: {app_versions_file}")

    lines = app_versions_file.read_text(encoding="utf-8").splitlines()
    updated: list[str] = []
    skipped: list[str] = []

    for fi in selected:
        app = out_apps.get(fi.name) or {}
        if "error" in app:
            skipped.append(f"{fi.name} (error)")
            continue

        max_code = app.get("maxVersionCode")
        if max_code is None:
            skipped.append(f"{fi.name} (no-version-on-track)")
            continue

        target_code = int(max_code) + 1 if use_next else int(max_code)
        key = f"{fi.name}.versionCode"
        prefix = f"{key}="
        replaced = False
        for idx, raw in enumerate(lines):
            stripped = raw.strip()
            if stripped.startswith("#") or "=" not in stripped:
                continue
            if stripped.startswith(prefix):
                lines[idx] = f"{key}={target_code}"
                replaced = True
                break
        if replaced:
            if sync_version_names:
                sync_version_name_for_flavor(lines=lines, flavor_name=fi.name, target_code=target_code)
            updated.append(fi.name)
        else:
            skipped.append(f"{fi.name} (missing-key)")

    app_versions_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return updated, skipped


def sync_version_name_for_flavor(lines: list[str], flavor_name: str, target_code: int) -> None:
    key = f"{flavor_name}.versionName"
    prefix = f"{key}="
    target_suffix = str(target_code)

    for idx, raw in enumerate(lines):
        stripped = raw.strip()
        if stripped.startswith("#") or "=" not in stripped:
            continue
        if not stripped.startswith(prefix):
            continue

        existing_value = stripped.split("=", 1)[1].strip()
        # Preserve common style 1.0.<versionCode>; otherwise keep existing major.minor and replace last numeric segment.
        match = re.match(r"^(?P<base>\d+\.\d+)\.\d+$", existing_value)
        if match:
            lines[idx] = f"{key}={match.group('base')}.{target_suffix}"
            return

        generic = re.match(r"^(?P<head>\d+(?:\.\d+)*)$", existing_value)
        if generic and "." in existing_value:
            parts = existing_value.split(".")
            parts[-1] = target_suffix
            lines[idx] = f"{key}={'.'.join(parts)}"
            return

        # Fallback for non-standard formats: set deterministic simple version name.
        lines[idx] = f"{key}=1.0.{target_suffix}"
        return


def gh_warning(message: str) -> None:
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        print(f"::warning::{message}")
    else:
        print(f"WARNING: {message}")


def main() -> int:
    args = parse_args()

    repo_root = pathlib.Path(__file__).resolve().parents[2]
    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    flavors = read_flavors(flavor_file)

    raw_tracks = [t.strip() for t in (args.tracks or "").split(",") if t.strip()]
    if not raw_tracks:
        raise RuntimeError("--tracks resolved to empty list")
    use_all_tracks = len(raw_tracks) == 1 and raw_tracks[0].lower() == "all"
    want_tracks = [] if use_all_tracks else raw_tracks

    wanted = args.flavors.strip()
    if wanted.lower() == "all":
        selected = list(flavors.values())
    else:
        names = [x.strip() for x in wanted.split(",") if x.strip()]
        missing = [n for n in names if n not in flavors]
        if missing:
            raise RuntimeError(f"Unknown flavors: {', '.join(missing)}")
        selected = [flavors[n] for n in names]

    is_ci = os.environ.get("CI", "").strip().lower() == "true"
    raw_sa = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw_sa and not is_ci:
        raw_sa = try_read_from_dotenv(repo_root)

    service_account_info = load_service_account_value(raw_sa)
    service = build_android_publisher(service_account_info)

    out: dict[str, dict] = {"tracks": "all" if use_all_tracks else want_tracks, "apps": {}}

    for fi in selected:
        pkg = fi.package_name
        try:
            track_map = fetch_tracks_version_codes(service, pkg)
            filtered = track_map if use_all_tracks else {t: track_map.get(t, []) for t in want_tracks}
            max_code = max((c for codes in filtered.values() for c in codes), default=None)
            out["apps"][fi.name] = {
                "package": pkg,
                "tracks": filtered,
                "maxVersionCode": max_code,
            }
        except Exception as exc:  # noqa: BLE001
            out["apps"][fi.name] = {
                "package": pkg,
                "error": str(exc).splitlines()[0][:240],
            }

    out_json = pathlib.Path(args.out_json)
    ensure_parent(out_json)
    out_json.write_text(json.dumps(out, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")

    # Suggested properties file (versionCode only).
    out_props = pathlib.Path(args.out_props)
    ensure_parent(out_props)
    lines: list[str] = []
    lines.append("# Generated by scripts/ci/fetch_play_version_codes.py")
    lines.append("# Populate app-versions.properties with per-flavor versionCode values.")
    lines.append("#")
    for fi in selected:
        app = out["apps"].get(fi.name) or {}
        if "error" in app:
            lines.append(f"# {fi.name}: ERROR: {app['error']}")
            continue
        max_code = app.get("maxVersionCode")
        if max_code is None:
            track_note = "all" if use_all_tracks else ",".join(want_tracks)
            lines.append(f"# {fi.name}: No versionCodes found on selected tracks ({track_note})")
            continue
        code = int(max_code) + 1 if args.suggest_next else int(max_code)
        suffix = " # next" if args.suggest_next else " # current-max"
        lines.append(f"{fi.name}.versionCode={code}{suffix}")

    out_props.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"Wrote: {out_json}")
    print(f"Wrote: {out_props}")
    print_version_code_summary(selected=selected, out_apps=out["apps"], use_next=args.suggest_next)

    app_versions_file = (repo_root / args.app_versions_file).resolve()
    if args.apply_to_app_versions:
        updated, skipped = apply_codes_to_app_versions(
            app_versions_file=app_versions_file,
            selected=selected,
            out_apps=out["apps"],
            use_next=args.suggest_next,
            sync_version_names=args.sync_version_names,
        )
        print(f"Updated app-versions file: {app_versions_file}")
        print(f"Updated flavors: {', '.join(updated) if updated else '(none)'}")
        if skipped:
            print(f"Skipped flavors: {', '.join(skipped)}")
            for item in skipped:
                if "no-version-on-track" in item:
                    gh_warning(
                        f"No Play versionCode found for {item}. Keeping existing app-versions.properties value "
                        "(expected for brand-new flavor/apps not yet uploaded)."
                    )
                elif "(error)" in item:
                    gh_warning(f"Play version lookup failed for {item}. Existing app-versions.properties value kept.")
                elif "(missing-key)" in item:
                    gh_warning(f"Missing versionCode key in app-versions.properties for {item}.")

    if args.verify_greater_than_play:
        repo_version_codes = read_version_codes(app_versions_file)
        gate_results = evaluate_version_gate(
            selected=selected,
            out_apps=out["apps"],
            repo_version_codes=repo_version_codes,
        )
        print_version_gate_summary(gate_results)
        failures = [result for result in gate_results if not result.passed]
        if failures:
            print(
                "FAILED: Repo versionCode values must be strictly greater than the maximum "
                "versionCode on every selected Play track."
            )
            return 1
        print("OK: All selected repo versionCodes are greater than Play track maxima.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
