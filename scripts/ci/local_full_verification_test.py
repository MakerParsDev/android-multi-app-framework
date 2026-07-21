#!/usr/bin/env python3
"""Regression tests for the local full-verification shell contract."""

from __future__ import annotations

import json
import os
import shutil
import subprocess  # nosec B404 -- fixed local test harness
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASH_PATH = shutil.which("bash")
SCRIPT = ROOT / "scripts/ci/local-full-verification.sh"
FLAVORS = (
    "amenerrasulu",
    "ayetelkursi",
    "bereketduasi",
    "esmaulhusna",
    "fetihsuresi",
    "insirahsuresi",
    "ismiazamduasi",
    "kenzularsduasi",
    "kible",
    "kuran_kerim",
    "mucizedualar",
    "namazsurelerivedualarsesli",
    "namazvakitleri",
    "nazarayeti",
    "vakiasuresi",
    "yasinsuresi",
    "zikirmatik",
)


@unittest.skipUnless(BASH_PATH, "requires Bash")
class LocalFullVerificationTest(unittest.TestCase):
    def test_skip_firebase_excludes_every_google_services_variant_task(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo = Path(temp_dir) / "repo"
            script_dir = repo / "scripts" / "ci"
            script_dir.mkdir(parents=True)
            shutil.copy2(SCRIPT, script_dir / SCRIPT.name)
            (script_dir / "android-toolchain.sh").write_text("", encoding="utf-8")

            for name in (
                "validate_android_toolchain_config.py",
                "validate_ci_apps_catalog.py",
                "validate_admob_inventory.py",
                "validate_app_ads_txt.py",
                "collect_quality_reports.py",
                "validate_gradle_warning_report.py",
            ):
                (script_dir / name).write_text("", encoding="utf-8")
            for name in (
                "verify_env_contract.sh",
                "verify-android-toolchain.sh",
                "release_task_graph_dry_run.sh",
            ):
                path = script_dir / name
                path.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
                path.chmod(0o755)

            expected = [
                f":app:process{flavor[0].upper() + flavor[1:]}{build_type}GoogleServices"
                for flavor in FLAVORS
                for build_type in ("Debug", "Release")
            ]
            gradle_log = repo / "gradle.log"
            gradlew = repo / "gradlew"
            expected_json = json.dumps(expected)
            gradlew.write_text(
                textwrap.dedent(
                    f"""\
                    #!/usr/bin/env bash
                    set -euo pipefail
                    printf '%s\\n' "$*" >> "{gradle_log}"
                    if [[ " $* " == *" --version "* ]]; then
                      exit 0
                    fi
                    if [[ " $* " == *" -q printFlavors "* ]]; then
                      printf '%s\\n' '{json.dumps(list(FLAVORS))}'
                      exit 0
                    fi
                    python3 - "$*" '{expected_json}' <<'CHECK'
                    import json
                    import sys

                    invocation = f" {{sys.argv[1]}} "
                    expected = json.loads(sys.argv[2])
                    missing = [task for task in expected if f" -x {{task}} " not in invocation]
                    if missing:
                        print("missing exclusions: " + ", ".join(missing), file=sys.stderr)
                        raise SystemExit(1)
                    CHECK
                    """
                ),
                encoding="utf-8",
            )
            gradlew.chmod(0o755)

            fake_bin = repo / "fake-bin"
            fake_bin.mkdir()
            fake_git = fake_bin / "git"
            fake_git.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            fake_git.chmod(0o755)

            env = os.environ.copy()
            env["PATH"] = f"{fake_bin}:{env['PATH']}"
            result = subprocess.run(  # nosec B603 -- fixed Bash binary and test-owned script
                [BASH_PATH, str(script_dir / SCRIPT.name), "--skip-firebase"],
                cwd=repo,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )

            invocations = gradle_log.read_text(encoding="utf-8") if gradle_log.exists() else ""

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("qualityCheck", invocations)
        self.assertIn(":app:assembleNamazsurelerivedualarsesliDebug", invocations)


if __name__ == "__main__":
    unittest.main()
