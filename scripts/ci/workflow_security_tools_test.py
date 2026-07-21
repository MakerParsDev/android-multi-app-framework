#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "config/workflow-security-tools.json"
INSTALLER = ROOT / "scripts/ci/install_workflow_security_tools.sh"

EXPECTED = {
    "actionlint": {
        "version": "1.7.12",
        "asset": "actionlint_1.7.12_linux_amd64.tar.gz",
        "url": "https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz",
        "sha256": "8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8",
        "binary": "actionlint",
    },
    "zizmor": {
        "version": "1.27.0",
        "asset": "zizmor-x86_64-unknown-linux-gnu.tar.gz",
        "url": "https://github.com/zizmorcore/zizmor/releases/download/v1.27.0/zizmor-x86_64-unknown-linux-gnu.tar.gz",
        "sha256": "277f2bd8fd37cf60c42ab7afca6faa884e65440fa31e02b44bdaae60f62a358f",
        "binary": "zizmor",
    },
}


def run_installer(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["bash", str(INSTALLER), *args],
        cwd=ROOT,
        check=False,
        text=True,
        capture_output=True,
    )


def test_config_matches_pinned_records() -> None:
    document = json.loads(CONFIG.read_text(encoding="utf-8"))
    assert document == EXPECTED, document


def test_print_config_is_stable() -> None:
    result = run_installer("--print-config")
    assert result.returncode == 0, result.stderr
    assert result.stdout == "actionlint=1.7.12\nzizmor=1.27.0\n", result.stdout


def test_installer_contains_required_integrity_controls() -> None:
    script = INSTALLER.read_text(encoding="utf-8")
    required_fragments = (
        "set -euo pipefail",
        "curl --fail --location --silent --show-error --proto '=https' --tlsv1.2",
        "--connect-timeout 10 --max-time 300",
        "sha256sum --check --status",
        "mktemp -d",
        "trap",
        "install -m 0755",
    )
    for fragment in required_fragments:
        assert fragment in script, fragment


def test_unknown_argument_fails() -> None:
    result = run_installer("--not-a-real-option")
    assert result.returncode == 2, (result.returncode, result.stderr)
    assert "Unknown argument" in result.stderr, result.stderr



def write_fake_tool(path: Path, version_output: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"#!/usr/bin/env bash\nprintf '%s\n' {version_output!r}\n", encoding="utf-8")
    path.chmod(0o755)


def test_shell_assignments_support_space_in_bin_dir() -> None:
    with tempfile.TemporaryDirectory(prefix="workflow tools ") as tmp:
        bin_root = Path(tmp)
        write_fake_tool(bin_root / "actionlint/1.7.12/actionlint", "1.7.12")
        write_fake_tool(bin_root / "zizmor/1.27.0/zizmor", "zizmor 1.27.0")
        result = run_installer("--bin-dir", str(bin_root))
        assert result.returncode == 0, result.stderr
        check = subprocess.run(
            [
                "bash",
                "-c",
                'set -euo pipefail; eval "$ASSIGNMENTS"; test -x "$ACTIONLINT_BIN"; test -x "$ZIZMOR_BIN"',
            ],
            env={**os.environ, "ASSIGNMENTS": result.stdout},
            check=False,
            text=True,
            capture_output=True,
        )
        assert check.returncode == 0, (result.stdout, check.stderr)

def main() -> int:
    tests = [
        test_config_matches_pinned_records,
        test_print_config_is_stable,
        test_installer_contains_required_integrity_controls,
        test_unknown_argument_fails,
        test_shell_assignments_support_space_in_bin_dir,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
