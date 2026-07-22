#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/generate_ci_google_services.py"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def make_repo(root: Path) -> None:
    write_json(
        root / "config/firebase-apps.json",
        {
            "demo": {
                "projectId": "example-project",
                "appId": "1:123456789012:android:abcdef123456",
            }
        },
    )
    write_json(
        root / ".ci/apps.json",
        [
            {
                "flavor": "demo",
                "package": "com.example.demo",
                "name": "Demo",
                "admob_app_id": "ca-app-pub-0000000000000000~0000000000",
                "ad_units": {},
            }
        ],
    )


def run_script(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(SCRIPT), "--repo", str(repo), *args],
        check=False,
        text=True,
        capture_output=True,
    )


def target(repo: Path) -> Path:
    return repo / "app/src/demo/google-services.json"


def test_generates_valid_marked_placeholder() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo)
        result = run_script(repo, "--flavors", "demo")
        assert result.returncode == 0, result.stderr
        payload = json.loads(target(repo).read_text(encoding="utf-8"))
        assert payload["ci_placeholder"]["generated_by"] == "generate_ci_google_services.py"
        assert payload["project_info"]["project_number"] == "123456789012"
        assert payload["project_info"]["project_id"] == "example-project"
        client = payload["client"][0]
        assert client["client_info"]["mobilesdk_app_id"] == "1:123456789012:android:abcdef123456"
        assert client["client_info"]["android_client_info"]["package_name"] == "com.example.demo"
        assert client["oauth_client"][0] == {
            "client_id": "123456789012-ci-placeholder.apps.googleusercontent.com",
            "client_type": 3,
        }
        assert client["api_key"][0]["current_key"] == "AIzaSy000000000000000000000000000000000"
        assert target(repo).stat().st_mode & 0o777 == 0o600


def test_refuses_to_overwrite_real_config() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo)
        target(repo).parent.mkdir(parents=True, exist_ok=True)
        target(repo).write_text('{"real": true}\n', encoding="utf-8")
        result = run_script(repo, "--flavors", "demo")
        assert result.returncode == 1
        assert "refusing to overwrite existing non-placeholder" in result.stderr
        assert target(repo).read_text(encoding="utf-8") == '{"real": true}\n'


def test_clean_removes_only_generated_placeholder() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo)
        assert run_script(repo, "--flavors", "demo").returncode == 0
        result = run_script(repo, "--clean", "--flavors", "demo")
        assert result.returncode == 0, result.stderr
        assert not target(repo).exists()

        target(repo).parent.mkdir(parents=True, exist_ok=True)
        target(repo).write_text('{"real": true}\n', encoding="utf-8")
        result = run_script(repo, "--clean", "--flavors", "demo")
        assert result.returncode == 1
        assert "refusing to remove existing non-placeholder" in result.stderr
        assert target(repo).exists()


def test_unknown_flavor_fails() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo)
        result = run_script(repo, "--flavors", "missing")
        assert result.returncode == 1
        assert "unknown flavor" in result.stderr.lower()


def test_ci_workflow_materializes_and_cleans_once() -> None:
    workflow = (ROOT / ".github/workflows/ci-pr.yml").read_text(encoding="utf-8")
    generate = 'python3 scripts/ci/generate_ci_google_services.py --flavors "$FLAVOR"'
    clean = 'python3 scripts/ci/generate_ci_google_services.py --clean --flavors "$FLAVOR"'
    assert workflow.count(generate) == 1, workflow
    assert workflow.count(clean) == 1, workflow
    assert workflow.count("if: always()") >= 1, workflow


def test_ci_workflow_splits_lint_and_assemble() -> None:
    workflow = (ROOT / ".github/workflows/ci-pr.yml").read_text(encoding="utf-8")
    combined = './gradlew "lint${cap}Debug" "assemble${cap}Debug"'
    lint_only = './gradlew "lint${cap}Debug" --no-daemon'
    assemble_only = './gradlew "assemble${cap}Debug" --no-daemon'
    assert combined not in workflow, workflow
    assert workflow.count(lint_only) == 1, workflow
    assert workflow.count(assemble_only) == 1, workflow


def main() -> int:
    tests = [
        test_generates_valid_marked_placeholder,
        test_refuses_to_overwrite_real_config,
        test_clean_removes_only_generated_placeholder,
        test_unknown_flavor_fails,
        test_ci_workflow_materializes_and_cleans_once,
        test_ci_workflow_splits_lint_and_assemble,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
