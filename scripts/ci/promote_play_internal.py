#!/usr/bin/env python3
"""Promote an exact already-published Play Internal version to 100% production.

No AAB is built or uploaded. Existing 100%-only owner policy is preserved.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from fetch_play_version_codes import read_flavors
from publish_play_internal import build_service, validate_service_account_path


def _single_release(tracks: list[dict[str, Any]], track: str) -> dict[str, Any]:
    candidates = [item for item in tracks if item.get("track") == track]
    if len(candidates) != 1 or len(candidates[0].get("releases") or []) != 1:
        raise ValueError(f"{track} must contain exactly one current release")
    return candidates[0]["releases"][0]


def make_plan(
    tracks: list[dict[str, Any]],
    *,
    flavor: str,
    expected_internal_code: int,
    expected_production_code: int,
) -> dict[str, Any]:
    """Require a stable old production release and an exact completed internal one."""
    if expected_internal_code <= expected_production_code or expected_production_code <= 0:
        raise ValueError("internal version must be newer than production")
    internal = _single_release(tracks, "internal")
    if internal.get("status") != "completed" or internal.get("versionCodes") != [
        str(expected_internal_code)
    ]:
        raise ValueError("internal release does not match expected completed version")
    source_release_name = str(internal.get("name") or "")
    if "ad-inspector-debug" in source_release_name.casefold():
        raise ValueError("diagnostic-only Internal release cannot be promoted to Production")
    production = _single_release(tracks, "production")
    if production.get("status") != "completed" or production.get("versionCodes") != [
        str(expected_production_code)
    ]:
        raise ValueError("production release does not match expected stable version")
    return {
        "flavor": flavor,
        "internal_code": expected_internal_code,
        "production_code": expected_production_code,
        "source_release_name": source_release_name,
        "target_release": {
            "name": f"1.0.{expected_internal_code}",
            "versionCodes": [str(expected_internal_code)],
            "status": "completed",
        },
    }


def promote(
    service: Any,
    *,
    package_name: str,
    flavor: str,
    expected_internal_code: int,
    expected_production_code: int,
    execute: bool,
) -> dict[str, Any]:
    """Prepare one edit, validate, commit only when requested, then reread Play."""
    if not package_name or not package_name.startswith("com.parsfilo."):
        raise ValueError("unrecognized application package")
    edits = service.edits()
    edit_id: str | None = None
    committed = False
    try:
        edit_id = str(edits.insert(packageName=package_name, body={}).execute()["id"])
        snapshot = edits.tracks().list(packageName=package_name, editId=edit_id).execute()
        plan = make_plan(
            snapshot.get("tracks") or [],
            flavor=flavor,
            expected_internal_code=expected_internal_code,
            expected_production_code=expected_production_code,
        )
        if not execute:
            return {**plan, "package_name": package_name, "state": "PREVIEW"}
        edits.tracks().update(
            packageName=package_name,
            editId=edit_id,
            track="production",
            body={"track": "production", "releases": [plan["target_release"]]},
        ).execute()
        edits.validate(packageName=package_name, editId=edit_id).execute()
        # A commit is the irreversible mutation. Never blindly retry on errors.
        edits.commit(packageName=package_name, editId=edit_id).execute()
        committed = True
    finally:
        if edit_id and not committed:
            try:
                edits.delete(packageName=package_name, editId=edit_id).execute()
            except Exception as cleanup_error:  # noqa: BLE001 - cleanup
                print("Play edit cleanup warning: " + type(cleanup_error).__name__, file=sys.stderr)

    # A successful commit is not itself proof of live serving state.
    verify_edit_id: str | None = None
    try:
        verify_edit_id = str(edits.insert(packageName=package_name, body={}).execute()["id"])
        current = edits.tracks().list(
            packageName=package_name, editId=verify_edit_id
        ).execute().get("tracks") or []
        observed = _single_release(current, "production")
        if observed.get("status") != "completed" or observed.get("versionCodes") != [
            str(expected_internal_code)
        ]:
            raise RuntimeError("Play production state differs from committed version")
    except Exception as error:
        raise RuntimeError(
            "Promotion may have committed but remote verification failed; "
            "inspect Play Console before any retry"
        ) from error
    finally:
        if verify_edit_id:
            try:
                edits.delete(packageName=package_name, editId=verify_edit_id).execute()
            except Exception as cleanup_error:  # noqa: BLE001 - cleanup
                print("Play edit cleanup warning: " + type(cleanup_error).__name__, file=sys.stderr)
    return {**plan, "package_name": package_name, "state": "VERIFIED"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--flavor", required=True)
    parser.add_argument("--expected-internal-code", type=int, required=True)
    parser.add_argument("--expected-production-code", type=int, required=True)
    parser.add_argument("--acceptance-issue", type=int, required=True)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    try:
        if args.acceptance_issue <= 0:
            raise ValueError("a tracked release acceptance issue is required")
        if args.execute and (
            os.getenv("GITHUB_REF") != "refs/heads/main"
            or os.getenv("GITHUB_EVENT_NAME") != "workflow_dispatch"
        ):
            raise ValueError("production promotion requires a manual main-branch workflow")
        root = Path(__file__).resolve().parents[2]
        flavors = read_flavors(root / "buildSrc/src/main/kotlin/FlavorConfig.kt")
        if args.flavor not in flavors:
            raise ValueError("unknown canonical flavor")
        keyfile = validate_service_account_path(
            os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "")
        )
        service, _ = build_service(keyfile)
        report = promote(
            service,
            package_name=flavors[args.flavor].package_name,
            flavor=args.flavor,
            expected_internal_code=args.expected_internal_code,
            expected_production_code=args.expected_production_code,
            execute=args.execute,
        )
        report["acceptance_issue"] = args.acceptance_issue
        print(json.dumps(report, sort_keys=True))
        return 0
    except Exception as error:  # noqa: BLE001 - sanitize API errors
        # Do not expose credential paths, Play API error bodies or user data in logs.
        print(f"Promotion not confirmed ({type(error).__name__}); check expected "
              "versions, acceptance and Google Play state", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
