#!/usr/bin/env python3
"""Upload one exact AAB to Google Play internal track and commit the edit."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
import sys
from pathlib import Path
from typing import Any, Callable, Sequence

ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
ALLOWED_TRACK = "internal"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_service_account_path(raw: str) -> Path:
    if not raw.strip():
        raise RuntimeError("PLAY_SERVICE_ACCOUNT_JSON is required")
    path = Path(raw).expanduser().resolve()
    if not path.is_file():
        raise RuntimeError(f"Play service-account file does not exist: {path}")
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode & 0o077:
        raise RuntimeError(f"Play service-account file must not be group/world accessible: mode={mode:o}")
    return path


def publish_aab(
    *,
    service: Any,
    package_name: str,
    aab_path: Path,
    track: str,
    release_name: str,
    media_factory: Callable[[str], Any],
) -> dict[str, Any]:
    if track != ALLOWED_TRACK:
        raise RuntimeError(f"Only the {ALLOWED_TRACK!r} track is allowed")
    if not package_name.strip():
        raise RuntimeError("package name is required")
    if not aab_path.is_file():
        raise RuntimeError(f"AAB does not exist: {aab_path}")

    local_sha256 = sha256_file(aab_path)
    edit_id: str | None = None
    edits = service.edits()
    try:
        edit = edits.insert(packageName=package_name, body={}).execute(num_retries=3)
        edit_id = str(edit["id"])
        uploaded = edits.bundles().upload(
            packageName=package_name,
            editId=edit_id,
            media_body=media_factory(str(aab_path)),
        ).execute(num_retries=3)

        remote_sha256 = str(uploaded.get("sha256", "")).lower()
        if remote_sha256 != local_sha256:
            raise RuntimeError(
                "Google Play upload SHA-256 mismatch: "
                f"local={local_sha256} remote={remote_sha256 or '(missing)'}"
            )

        version_code = str(uploaded["versionCode"])
        track_body = {
            "track": ALLOWED_TRACK,
            "releases": [
                {
                    "name": release_name,
                    "versionCodes": [version_code],
                    "status": "completed",
                }
            ],
        }
        edits.tracks().update(
            packageName=package_name,
            editId=edit_id,
            track=ALLOWED_TRACK,
            body=track_body,
        ).execute(num_retries=3)
        edits.validate(packageName=package_name, editId=edit_id).execute(num_retries=3)
        edits.commit(packageName=package_name, editId=edit_id).execute(num_retries=3)
    except Exception:
        if edit_id is not None:
            try:
                edits.delete(packageName=package_name, editId=edit_id).execute(num_retries=1)
            except Exception:
                pass
        raise

    return {
        "packageName": package_name,
        "track": ALLOWED_TRACK,
        "releaseName": release_name,
        "versionCode": int(version_code),
        "sha256": local_sha256,
    }


def build_service(service_account_path: Path) -> tuple[Any, Callable[[str], Any]]:
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    credentials = service_account.Credentials.from_service_account_file(
        str(service_account_path),
        scopes=[ANDROID_PUBLISHER_SCOPE],
    )
    service = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)

    def media_factory(path: str) -> Any:
        return MediaFileUpload(
            path,
            mimetype="application/octet-stream",
            resumable=True,
            chunksize=8 * 1024 * 1024,
        )

    return service, media_factory


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--track", default=ALLOWED_TRACK, choices=[ALLOWED_TRACK])
    parser.add_argument("--release-name", required=True)
    parser.add_argument("--report", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        service_account_path = validate_service_account_path(
            os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "")
        )
        service, media_factory = build_service(service_account_path)
        report = publish_aab(
            service=service,
            package_name=args.package_name,
            aab_path=args.aab.resolve(),
            track=args.track,
            release_name=args.release_name,
            media_factory=media_factory,
        )
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(
        "Published exact AAB to Google Play internal track: "
        f"package={report['packageName']} versionCode={report['versionCode']} sha256={report['sha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
