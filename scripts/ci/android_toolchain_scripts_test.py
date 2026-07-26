#!/usr/bin/env python3
"""Regression tests for the Android toolchain shell helpers."""

from __future__ import annotations

import os
import shutil
import stat
import subprocess  # nosec B404 -- trusted local test process harness
import tempfile
import textwrap
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASH_PATH = shutil.which("bash")
TOOLCHAIN_SCRIPT = ROOT / "scripts/ci/android-toolchain.sh"
SETUP_SCRIPT = ROOT / "scripts/ci/setup-android-sdk.sh"
VERIFY_SCRIPT = ROOT / "scripts/ci/verify-android-toolchain.sh"

MANIFEST = """
    toolchain.java.major = 21
    toolchain.android.compileSdk = 37
    toolchain.android.platformPackage = 37.0
    toolchain.android.targetSdk = 36
    toolchain.android.minSdk = 24
    toolchain.android.buildTools = 37.0.0
    toolchain.android.cmdlineTools = 21.0
    toolchain.android.bootstrapCmdlineToolsRevision = 14742923
"""


@unittest.skipUnless(BASH_PATH, "requires Bash")
class AndroidToolchainScriptsTest(unittest.TestCase):
    def run_bash(
        self,
        command: str,
        *,
        env: dict[str, str] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        merged_env = os.environ.copy()
        merged_env.pop("ANDROID_API_LEVEL", None)
        merged_env.pop("ANDROID_BUILD_TOOLS_VERSION", None)
        merged_env.pop("ANDROID_CMDLINE_TOOLS_VERSION", None)
        if env:
            merged_env.update(env)
        if BASH_PATH is None:
            self.fail("Bash path disappeared after test class initialization")
        return subprocess.run(  # nosec B603 -- fixed Bash binary; test-owned input only
            [BASH_PATH, "-c", command],
            cwd=ROOT,
            env=merged_env,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_manifest_reader_accepts_whitespace_around_equals(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            manifest = Path(temp_dir) / "gradle.properties"
            manifest.write_text(textwrap.dedent(MANIFEST), encoding="utf-8")
            result = self.run_bash(
                f'source "{TOOLCHAIN_SCRIPT}"; '
                'printf "%s|%s|%s\\n" "$ANDROID_JAVA_MAJOR" '
                '"$ANDROID_PLATFORM_PACKAGE" "$ANDROID_BUILD_TOOLS_VERSION"',
                env={"ANDROID_TOOLCHAIN_PROPERTIES_FILE": str(manifest)},
            )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("21|37.0|37.0.0", result.stdout.strip())

    def test_java_detection_ignores_launcher_messages(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_bin = Path(temp_dir) / "bin"
            fake_bin.mkdir()
            java_script = fake_bin / "java"
            java_script.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' 'Picked up JAVA_TOOL_OPTIONS: -Dexample=true' >&2\n"
                "printf '%s\\n' 'openjdk version \"21.0.11\" 2026-04-21' >&2\n",
                encoding="utf-8",
            )
            java_script.chmod(0o755)

            result = self.run_bash(
                f'source "{SETUP_SCRIPT}"; detect_java_major',
                env={"PATH": f"{fake_bin}:{os.environ['PATH']}"},
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("21", result.stdout.strip())

    def test_toolchain_verification_ignores_launcher_messages(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            manifest = temp / "gradle.properties"
            manifest.write_text(textwrap.dedent(MANIFEST), encoding="utf-8")
            fake_bin = temp / "bin"
            fake_bin.mkdir()
            java_script = fake_bin / "java"
            java_script.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' 'Picked up JAVA_TOOL_OPTIONS: -Dexample=true' >&2\n"
                "printf '%s\\n' 'openjdk version \"21.0.11\" 2026-04-21' >&2\n",
                encoding="utf-8",
            )
            java_script.chmod(0o755)

            sdk_root = temp / "sdk"
            (sdk_root / "platforms/android-37.0").mkdir(parents=True)
            (sdk_root / "platforms/android-37.0/android.jar").touch()
            (sdk_root / "build-tools/37.0.0").mkdir(parents=True)
            (sdk_root / "platform-tools").mkdir(parents=True)
            adb = sdk_root / "platform-tools/adb"
            adb.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            adb.chmod(0o755)
            sdkmanager = sdk_root / "cmdline-tools/21.0/bin/sdkmanager"
            sdkmanager.parent.mkdir(parents=True)
            sdkmanager.write_text(
                "#!/usr/bin/env bash\nprintf '%s\\n' '21.0'\n",
                encoding="utf-8",
            )
            sdkmanager.chmod(0o755)

            result = self.run_bash(
                f'bash "{VERIFY_SCRIPT}"',
                env={
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "ANDROID_HOME": str(sdk_root),
                    "ANDROID_SDK_ROOT": str(sdk_root),
                    "ANDROID_TOOLCHAIN_PROPERTIES_FILE": str(manifest),
                },
            )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Java: openjdk version "21.0.11"', result.stdout)

    def test_bootstrap_temp_directory_is_removed_when_download_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            fake_bin = temp / "bin"
            fake_bin.mkdir()
            bootstrap_temp = temp / "bootstrap-temp"
            sdk_root = temp / "sdk"

            mktemp_script = fake_bin / "mktemp"
            mktemp_script.write_text(
                "#!/usr/bin/env bash\n"
                'if [[ "${1:-}" == "-d" ]]; then\n'
                '  mkdir -p "$TEST_BOOTSTRAP_TEMP"\n'
                '  printf "%s\\n" "$TEST_BOOTSTRAP_TEMP"\n'
                "else\n"
                '  exec /usr/bin/mktemp "$@"\n'
                "fi\n",
                encoding="utf-8",
            )
            curl_script = fake_bin / "curl"
            curl_script.write_text("#!/usr/bin/env bash\nexit 22\n", encoding="utf-8")
            mktemp_script.chmod(0o755)
            curl_script.chmod(0o755)

            result = self.run_bash(
                f'source "{SETUP_SCRIPT}"; '
                'SDK_ROOT="$TEST_SDK_ROOT"; bootstrap_cmdline_tools_seed',
                env={
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "TEST_BOOTSTRAP_TEMP": str(bootstrap_temp),
                    "TEST_SDK_ROOT": str(sdk_root),
                },
            )
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(bootstrap_temp.exists(), result.stderr)

    def test_local_properties_update_preserves_existing_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            test_repo = Path(temp_dir) / "repo"
            test_repo.mkdir()
            (test_repo / "settings.gradle.kts").write_text("", encoding="utf-8")
            local_properties = test_repo / "local.properties"
            local_properties.write_text("custom=value\nsdk.dir=/old/sdk\n", encoding="utf-8")
            local_properties.chmod(0o640)
            original_stat = local_properties.stat()

            result = self.run_bash(
                f'source "{SETUP_SCRIPT}"; '
                'REPO_ROOT="$TEST_REPO"; SDK_ROOT="/new/sdk"; write_local_properties',
                env={"TEST_REPO": str(test_repo)},
            )
            updated_stat = local_properties.stat()
            contents = local_properties.read_text(encoding="utf-8")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(stat.S_IMODE(original_stat.st_mode), stat.S_IMODE(updated_stat.st_mode))
        self.assertEqual(original_stat.st_uid, updated_stat.st_uid)
        self.assertEqual(original_stat.st_gid, updated_stat.st_gid)
        self.assertEqual("custom=value\nsdk.dir=/new/sdk\n", contents)


if __name__ == "__main__":
    unittest.main()
