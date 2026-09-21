#!/usr/bin/env python3
"""
Unit and regression tests for scripts/ci/resolve_jules_api_key.sh.
Verifies safe resolution, error handling, and mask generation without secret leakage.
"""

from __future__ import annotations

import os
import shutil
import stat
import subprocess
import tempfile
import unittest

SCRIPT_PATH = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "resolve_jules_api_key.sh")
)


def find_bash() -> str:
    if os.name == "nt":
        git_bash = r"C:\Program Files\Git\bin\bash.exe"
        if os.path.exists(git_bash):
            return git_bash
        git_usr_bash = r"C:\Program Files\Git\usr\bin\bash.exe"
        if os.path.exists(git_usr_bash):
            return git_usr_bash
    return "bash"


def to_bash_path(path: str) -> str:
    # Convert Windows path like C:\foo\bar to /c/foo/bar for Git Bash
    if os.name == "nt" and len(path) > 2 and path[1] == ":":
        drive = path[0].lower()
        rest = path[2:].replace("\\", "/")
        return f"/{drive}{rest}"
    return path


class TestResolveJulesApiKey(unittest.TestCase):

    def setUp(self):
        self.bash = find_bash()
        self.temp_dir = tempfile.mkdtemp(prefix="resolve-test-")
        self.github_env = os.path.join(self.temp_dir, "github_env.txt")
        open(self.github_env, "w").close()

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_existing_key_skips_doppler(self):
        env = {
            "PATH": os.environ.get("PATH", ""),
            "JULES_API_KEY": "fake-test-preexisting-key-12345",
            "GITHUB_ENV": to_bash_path(self.github_env),
        }
        res = subprocess.run(
            [self.bash, to_bash_path(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertEqual(res.returncode, 0, f"STDOUT: {res.stdout}\nSTDERR: {res.stderr}")
        self.assertIn("JULES_API_KEY is already set in environment", res.stdout)
        self.assertIn("::add-mask::fake-test-preexisting-key-12345", res.stdout)
        with open(self.github_env, "r", encoding="utf-8") as f:
            content = f.read()
            self.assertIn("JULES_API_KEY=fake-test-preexisting-key-12345", content)

    def test_missing_doppler_token_fails(self):
        env = {
            "PATH": os.environ.get("PATH", ""),
            "GITHUB_ENV": to_bash_path(self.github_env),
        }
        res = subprocess.run(
            [self.bash, to_bash_path(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("DOPPLER_TOKEN is not set", res.stderr)

    def test_mock_doppler_returns_empty_fails(self):
        mock_bin_dir = os.path.join(self.temp_dir, "bin")
        os.makedirs(mock_bin_dir, exist_ok=True)
        mock_doppler = os.path.join(mock_bin_dir, "doppler")
        with open(mock_doppler, "w", newline="\n") as f:
            f.write("#!/usr/bin/env bash\nexit 0\n")
        os.chmod(mock_doppler, stat.S_IRWXU)

        env = {
            "PATH": f"{to_bash_path(mock_bin_dir)}:{os.environ.get('PATH', '')}",
            "DOPPLER_TOKEN": "mock-doppler-token",
            "GITHUB_ENV": to_bash_path(self.github_env),
            "RUNNER_TEMP": to_bash_path(self.temp_dir),
        }
        res = subprocess.run(
            [self.bash, to_bash_path(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("Retrieved JULES_API_KEY is empty", res.stderr)

    def test_mock_doppler_resolves_key_safely(self):
        fake_secret = "jules_mock_secret_998877"
        mock_bin_dir = os.path.join(self.temp_dir, "bin")
        os.makedirs(mock_bin_dir, exist_ok=True)
        mock_doppler = os.path.join(mock_bin_dir, "doppler")
        with open(mock_doppler, "w", newline="\n") as f:
            f.write(f"#!/usr/bin/env bash\necho '{fake_secret}'\n")
        os.chmod(mock_doppler, stat.S_IRWXU)

        env = {
            "PATH": f"{to_bash_path(mock_bin_dir)}:{os.environ.get('PATH', '')}",
            "DOPPLER_TOKEN": "mock-doppler-token",
            "GITHUB_ENV": to_bash_path(self.github_env),
            "RUNNER_TEMP": to_bash_path(self.temp_dir),
        }
        res = subprocess.run(
            [self.bash, to_bash_path(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
        )
        self.assertEqual(res.returncode, 0, f"STDOUT: {res.stdout}\nSTDERR: {res.stderr}")
        self.assertIn(f"::add-mask::{fake_secret}", res.stdout)
        self.assertIn("Successfully resolved JULES_API_KEY and masked it", res.stdout)
        with open(self.github_env, "r", encoding="utf-8") as f:
            self.assertIn(f"JULES_API_KEY={fake_secret}", f.read())


if __name__ == "__main__":
    unittest.main()
