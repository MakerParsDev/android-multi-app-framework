#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/resolve_ci_flavor_matrix.py"
EXPECTED_FLAVORS = [
    "amenerrasulu",
    "ayetelkursi",
    "bereketduasi",
    "esmaulhusna",
    "fetihsuresi",
    "kenzularsduasi",
    "kuran_kerim",
    "kible",
    "mucizedualar",
    "namazvakitleri",
    "namazsurelerivedualarsesli",
    "nazarayeti",
    "vakiasuresi",
    "yasinsuresi",
    "zikirmatik",
    "insirahsuresi",
    "ismiazamduasi",
]


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def make_repo(root: Path, flavors: list[str]) -> None:
    write_json(
        root / ".ci/apps.json",
        [
            {
                "flavor": flavor,
                "package": f"com.example.{flavor}",
                "name": flavor,
                "admob_app_id": "ca-app-pub-0000000000000000~0000000000",
                "ad_units": {},
            }
            for flavor in flavors
        ],
    )
    write_json(
        root / "config/firebase-apps.json",
        {
            flavor: {
                "projectId": "example-project",
                "appId": f"1:123456789012:android:{index:012x}",
            }
            for index, flavor in enumerate(flavors, start=1)
        },
    )


def run_script(repo: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["python3", str(SCRIPT), "--repo", str(repo)],
        check=False,
        text=True,
        capture_output=True,
    )


def parse_outputs(stdout: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in stdout.splitlines():
        key, value = line.split("=", 1)
        result[key] = value
    return result


def test_resolves_catalog_order_and_count() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo, ["first_app", "second_app"])
        result = run_script(repo)
        assert result.returncode == 0, result.stderr
        outputs = parse_outputs(result.stdout)
        assert json.loads(outputs["flavors"]) == ["first_app", "second_app"]
        assert outputs["count"] == "2"


def test_duplicate_flavor_fails() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo, ["demo"])
        apps = json.loads((repo / ".ci/apps.json").read_text(encoding="utf-8"))
        apps.append(dict(apps[0]))
        write_json(repo / ".ci/apps.json", apps)
        result = run_script(repo)
        assert result.returncode == 1
        assert "duplicate flavor" in result.stderr.lower()


def test_catalog_mismatch_fails() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        make_repo(repo, ["demo", "other"])
        firebase = json.loads(
            (repo / "config/firebase-apps.json").read_text(encoding="utf-8")
        )
        firebase.pop("other")
        write_json(repo / "config/firebase-apps.json", firebase)
        result = run_script(repo)
        assert result.returncode == 1
        assert "catalog mismatch" in result.stderr.lower()


def test_real_repository_has_all_17_flavors() -> None:
    result = run_script(ROOT)
    assert result.returncode == 0, result.stderr
    outputs = parse_outputs(result.stdout)
    assert json.loads(outputs["flavors"]) == EXPECTED_FLAVORS
    assert outputs["count"] == "17"


def main() -> int:
    tests = [
        test_resolves_catalog_order_and_count,
        test_duplicate_flavor_fails,
        test_catalog_mismatch_fails,
        test_real_repository_has_all_17_flavors,
    ]
    for test in tests:
        test()
        print(f"PASS {test.__name__}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
