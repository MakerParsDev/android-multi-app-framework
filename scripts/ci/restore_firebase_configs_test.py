#!/usr/bin/env python3
from __future__ import annotations

import base64
import io
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FLAVOR = "kuran_kerim"
TARGET = ROOT / "app" / "src" / FLAVOR / "google-services.json"
RESTORE = ROOT / "scripts" / "ci" / "restore_firebase_configs.sh"
MATERIALIZER = ROOT / "scripts" / "ci" / "materialize_firebase_configs.py"


def fixture_zip() -> bytes:
    firebase = json.loads((ROOT / "config/firebase-apps.json").read_text(encoding="utf-8"))[FLAVOR]
    apps = json.loads((ROOT / ".ci/apps.json").read_text(encoding="utf-8"))
    package = next(item["package"] for item in apps if item["flavor"] == FLAVOR)
    project_number = firebase["appId"].split(":", 2)[1]
    payload = {
        "project_info": {
            "project_number": project_number,
            "project_id": firebase["projectId"],
            "storage_bucket": f"{firebase['projectId']}.appspot.com",
        },
        "client": [{
            "client_info": {
                "mobilesdk_app_id": firebase["appId"],
                "android_client_info": {"package_name": package},
            },
            "oauth_client": [{
                "client_id": f"{project_number}-fixture.apps.googleusercontent.com",
                "client_type": 3,
            }],
            "api_key": [{"current_key": "AIzaSy000000000000000000000000000000000"}],
            "services": {"appinvite_service": {"other_platform_oauth_client": []}},
        }],
        "configuration_version": "1",
    }
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            f"app/src/{FLAVOR}/google-services.json",
            json.dumps(payload),
        )
    return buffer.getvalue()


def run(command: list[str], *, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    merged = os.environ.copy()
    if env:
        merged.update(env)
    return subprocess.run(
        command,
        cwd=ROOT,
        env=merged,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def clean_target() -> None:
    TARGET.unlink(missing_ok=True)


def test_materializer_accepts_private_zip_file() -> None:
    clean_target()
    try:
        with tempfile.TemporaryDirectory() as td:
            archive = Path(td) / "firebase.zip"
            archive.write_bytes(fixture_zip())
            archive.chmod(0o600)
            result = run([
                "python3", str(MATERIALIZER),
                "--flavors", FLAVOR,
                "--zip-file", str(archive),
                "--mode", "strict",
            ])
            assert result.returncode == 0, result.stderr + result.stdout
            assert TARGET.exists()
    finally:
        clean_target()


def test_base64_source_precedes_r2() -> None:
    clean_target()
    try:
        with tempfile.TemporaryDirectory() as td:
            forbidden = Path(td) / "forbidden"
            forbidden.write_text("#!/usr/bin/env bash\nexit 99\n", encoding="utf-8")
            forbidden.chmod(0o700)
            result = run(
                ["bash", str(RESTORE), FLAVOR],
                env={
                    "FIREBASE_CONFIGS_ZIP_BASE64": base64.b64encode(fixture_zip()).decode(),
                    "CF_R2_ACCOUNT_ID": "account",
                    "CF_API_TOKEN": "token",
                    "CF_R2_BUCKET": "bucket",
                    "CF_R2_FIREBASE_OBJECT": "firebase.zip",
                    "NPM_BIN": str(forbidden),
                    "WRANGLER_BIN": str(forbidden),
                },
            )
            assert result.returncode == 0, result.stderr + result.stdout
            assert TARGET.exists()
    finally:
        clean_target()


def test_incomplete_r2_contract_fails_closed() -> None:
    clean_target()
    result = run(
        ["bash", str(RESTORE), FLAVOR],
        env={
            "FIREBASE_CONFIGS_ZIP_BASE64": "",
            "CF_R2_ACCOUNT_ID": "account",
            "CF_API_TOKEN": "token",
            "CF_R2_BUCKET": "bucket",
            "CF_R2_FIREBASE_OBJECT": "",
        },
    )
    assert result.returncode != 0
    assert "Firebase config source is incomplete" in result.stderr
    assert not TARGET.exists()


def test_mocked_r2_download_is_materialized_and_archive_is_cleaned() -> None:
    clean_target()
    try:
        with tempfile.TemporaryDirectory() as td:
            temp = Path(td)
            source = temp / "source.zip"
            source.write_bytes(fixture_zip())
            trace = temp / "trace.txt"
            npm = temp / "npm"
            npm.write_text("#!/usr/bin/env bash\nset -euo pipefail\nprintf 'npm:%s\\n' \"$*\" >> \"$TRACE_FILE\"\n", encoding="utf-8")
            npm.chmod(0o700)
            wrangler = temp / "wrangler"
            wrangler.write_text(
                "#!/usr/bin/env bash\nset -euo pipefail\nprintf 'wrangler:%s\\n' \"$*\" >> \"$TRACE_FILE\"\nout=''\nwhile (($#)); do\n  if [[ $1 == --file ]]; then out=$2; shift 2; else shift; fi\ndone\ncp \"$R2_FIXTURE_ZIP\" \"$out\"\nprintf '%s' \"$out\" > \"$ARCHIVE_PATH_FILE\"\n",
                encoding="utf-8",
            )
            wrangler.chmod(0o700)
            archive_path_file = temp / "archive-path.txt"
            result = run(
                ["bash", str(RESTORE), FLAVOR],
                env={
                    "FIREBASE_CONFIGS_ZIP_BASE64": "",
                    "CF_R2_ACCOUNT_ID": "account",
                    "CF_API_TOKEN": "token",
                    "CF_R2_BUCKET": "bucket",
                    "CF_R2_FIREBASE_OBJECT": "firebase.zip",
                    "NPM_BIN": str(npm),
                    "WRANGLER_BIN": str(wrangler),
                    "TRACE_FILE": str(trace),
                    "R2_FIXTURE_ZIP": str(source),
                    "ARCHIVE_PATH_FILE": str(archive_path_file),
                },
            )
            assert result.returncode == 0, result.stderr + result.stdout
            assert TARGET.exists()
            assert "npm:ci --ignore-scripts --no-audit --no-fund" in trace.read_text()
            downloaded = Path(archive_path_file.read_text())
            assert not downloaded.exists(), f"temporary archive leaked: {downloaded}"
    finally:
        clean_target()


def main() -> int:
    tests = [
        test_materializer_accepts_private_zip_file,
        test_base64_source_precedes_r2,
        test_incomplete_r2_contract_fails_closed,
        test_mocked_r2_download_is_materialized_and_archive_is_cleaned,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
