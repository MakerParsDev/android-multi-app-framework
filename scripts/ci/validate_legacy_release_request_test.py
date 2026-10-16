#!/usr/bin/env python3
"""Contract tests for legacy release publication gates."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path

import yaml

from validate_legacy_release_request import (
    REQUIRED_WORKFLOWS, has_successful_run, validate_release_inputs,
)

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/ci/validate_legacy_release_request.py"
BASE = {
    "RELEASE_DO_BUILD": "true",
    "RELEASE_DO_QUALITY": "true",
    "RELEASE_DO_INTERNAL_TEST": "false",
    "RELEASE_DO_PUBLISH": "false",
    "RELEASE_PRODUCTION_ROLLOUT_PERCENT": "0",
    "RELEASE_TARGET_FLAVORS": "amenerrasulu",
    "RELEASE_UPDATE_PLAY_LISTING": "false",
    "RELEASE_BUILD_TYPE": "Release",
    "GITHUB_REF": "refs/heads/main",
}


def rejected(**changes: str) -> bool:
    try:
        validate_release_inputs({**BASE, **changes})
        return False
    except ValueError:
        return True


def test_build_only_does_not_call_github() -> None:
    result = subprocess.run(
        ["python3", str(SCRIPT)], env={**os.environ, **BASE},
        capture_output=True, text=True,
    )
    assert result.returncode == 0, result.stderr


def test_production_and_internal_publish_need_quality() -> None:
    for flag in ("RELEASE_DO_INTERNAL_TEST", "RELEASE_DO_PUBLISH"):
        for change in (
            {"RELEASE_DO_QUALITY": "false"},
            {"RELEASE_DO_BUILD": "false"},
            {"RELEASE_BUILD_TYPE": "Debug"},
            {"GITHUB_REF": "refs/heads/feature/unsafe"},
        ):
            selected = {"RELEASE_PRODUCTION_ROLLOUT_PERCENT": "5"} if flag == "RELEASE_DO_PUBLISH" else {}
            assert rejected(**{flag: "true", **selected, **change}), (flag, change)


def test_reject_ambiguous_track_and_listing_without_publish() -> None:
    assert rejected(RELEASE_DO_INTERNAL_TEST="true", RELEASE_DO_PUBLISH="true")
    assert rejected(RELEASE_UPDATE_PLAY_LISTING="true")
    assert rejected(RELEASE_DO_PUBLISH="true", RELEASE_DO_QUALITY="invalid")


def test_production_requires_explicit_five_percent_canary() -> None:
    assert rejected(RELEASE_DO_PUBLISH="true")
    for fraction in ("", "0", "1", "10", "25", "100", "05", "5.0", "-5"):
        assert rejected(
            RELEASE_DO_PUBLISH="true",
            RELEASE_PRODUCTION_ROLLOUT_PERCENT=fraction,
        ), fraction
    for target in ("all", "amenerrasulu,another", "", "amenerrasulu,"):
        assert rejected(
            RELEASE_DO_PUBLISH="true",
            RELEASE_PRODUCTION_ROLLOUT_PERCENT="5",
            RELEASE_TARGET_FLAVORS=target,
        ), target
    assert rejected(RELEASE_DO_PUBLISH="true",
                    RELEASE_PRODUCTION_ROLLOUT_PERCENT="5",
                    RELEASE_UPDATE_PLAY_LISTING="true")
    assert rejected(RELEASE_PRODUCTION_ROLLOUT_PERCENT="5")
    assert rejected(RELEASE_DO_INTERNAL_TEST="true",
                    RELEASE_PRODUCTION_ROLLOUT_PERCENT="5")
    assert validate_release_inputs({
        **BASE,
        "RELEASE_DO_PUBLISH": "true",
        "RELEASE_PRODUCTION_ROLLOUT_PERCENT": "5",
    })


def test_success_requires_exact_main_push_commit() -> None:
    sha = "a" * 40
    good = {"head_sha": sha, "head_branch": "main",
            "event": "push", "status": "completed", "conclusion": "success"}
    assert has_successful_run([good], sha)
    for field, bad in (
        ("head_sha", "b" * 40),
        ("head_branch", "release"),
        ("event", "workflow_dispatch"),
        ("status", "in_progress"),
        ("conclusion", "skipped"),
    ):
        assert not has_successful_run([{**good, field: bad}], sha), field


def test_workflow_contract_executes_before_credentials() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
    gate = workflow["jobs"]["security-gate"]
    assert gate["permissions"] == {"contents": "read", "actions": "read"}
    steps = gate["steps"]
    names = [step.get("name", "") for step in steps]
    validator_index = names.index("Validate release publication prerequisites")
    assert validator_index < names.index("Run security gate")
    assert validator_index < names.index("Validate Doppler bootstrap")
    assert "validate_legacy_release_request.py" in steps[validator_index]["run"]
    assert "GITHUB_TOKEN" in steps[validator_index]["env"]
    assert set(REQUIRED_WORKFLOWS) == {"ci-main.yml", "codeql.yml"}


def test_release_credentials_are_visible_within_step_and_next_step() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
    steps = workflow["jobs"]["build-release"]["steps"]
    decode = next(x for x in steps if x.get("name") == "Decode keystore")
    signing = decode["run"]
    assert signing.index("export KEYSTORE_FILE=/tmp/release.jks") < signing.index(
        "bash scripts/ci/verify_release_signing_config.sh"
    )
    assert "KEYSTORE_FILE=$KEYSTORE_FILE" in signing
    assert "set -euo pipefail" in signing and "umask 077" in signing

    bump = next(x for x in steps if x.get("name") == "Auto-bump Play version codes")
    script = bump["run"]
    assert script.index("export PLAY_SERVICE_ACCOUNT_JSON=/tmp/service-account.json") < (
        script.index("python3 scripts/ci/verify_play_service_account_project.py")
    )
    assert "PLAY_SERVICE_ACCOUNT_JSON=$PLAY_SERVICE_ACCOUNT_JSON" in script
    assert "set -euo pipefail" in script and "umask 077" in script

    build = next(x for x in steps if x.get("name") == "Build AABs")
    assert build["env"]["KEYSTORE_FILE"] == "/tmp/release.jks"


def test_release_build_and_publish_use_doppler_and_exact_signed_artifacts() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
    build = workflow["jobs"]["build-release"]
    publish = workflow["jobs"]["publish-play"]
    build_aabs = next(x for x in build["steps"] if x.get("name") == "Build AABs")
    publish_step = next(x for x in publish["steps"] if x.get("name") == "Publish to Play")
    assert "DOPPLER_TOKEN" in build_aabs["env"]
    assert "scripts/doppler-run.sh -- ./gradlew" in build_aabs["run"]
    assert "DOPPLER_TOKEN" in publish_step["env"]
    assert "scripts/doppler-run.sh -- ./gradlew" in publish_step["run"]
    assert "--artifact-dir" in publish_step["run"]
    assert "find" in publish_step["run"] and "*.aab" in publish_step["run"]
    assert "exactly one signed AAB" in publish_step["run"]
    assert publish["environment"] == "production"
    assert "Install verified Doppler CLI" in [
        step.get("name") for step in publish["steps"]
    ]


def test_production_publish_requires_manual_protected_environment() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
    release = workflow["jobs"]["publish-play"]
    assert release["environment"] == "production"
    assert release["needs"] == ["build-release", "resolve-flavors"]
    assert release["if"] == "inputs.do_internal_test || inputs.do_publish"
    publish = next(x for x in release["steps"] if x.get("name") == "Publish to Play")
    assert "PRODUCTION_ROLLOUT_PERCENT" in publish["env"]
    assert "ROLLOUT_FLAGS=(-PPLAY_ROLLOUT_PERCENT=" in publish["run"]
    assert '"${ROLLOUT_FLAGS[@]}"' in publish["run"]
    security = next(x for x in workflow["jobs"]["security-gate"]["steps"]
                    if x.get("name") == "Validate release publication prerequisites")
    assert security["env"]["RELEASE_PRODUCTION_ROLLOUT_PERCENT"] == (
        "${{ inputs.production_rollout_percent }}"
    )
    for file in ("play-internal.yml", "release-attested.yml"):
        sibling = yaml.safe_load((ROOT / ".github/workflows" / file).read_text())
        jobs = sibling["jobs"].values()
        assert any(job.get("environment") == "production" for job in jobs), file



def test_play_postpublish_audit_uses_exact_build_metadata_and_does_not_retry_release() -> None:
    workflow = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
    build_steps = workflow["jobs"]["build-release"]["steps"]
    publish_steps = workflow["jobs"]["publish-play"]["steps"]
    upload = next(x for x in build_steps
                  if x.get("name") == "Upload release version metadata")
    download = next(x for x in publish_steps
                    if x.get("name") == "Download release version metadata")
    publish_index = next(i for i, s in enumerate(publish_steps)
                         if s.get("name") == "Publish to Play")
    audit_index = next(i for i, s in enumerate(publish_steps)
                       if s.get("name") == "Audit Play-generated APK after successful production upload")
    audit = publish_steps[audit_index]
    assert upload["with"]["name"] == download["with"]["name"] == "play-release-version-metadata"
    assert upload["with"]["path"] == "app-versions.properties"
    assert audit_index > publish_index
    assert audit["continue-on-error"] is True
    assert "inputs.do_publish" in audit["if"]
    assert "!contains(needs.resolve-flavors.outputs.flavors_csv, ',')" in audit["if"]
    assert "verify_play_generated_apk.py" in audit["run"]
    assert "--versions-file" in audit["run"]
    assert "--flavor" in audit["run"]
    assert "re-dispatch" in audit["run"]
    assert audit["env"]["PLAY_SERVICE_ACCOUNT_JSON"] == "/tmp/service-account.json"


def main() -> int:
    for name, test in list(globals().items()):
        if name.startswith("test_") and callable(test):
            test()
            print("PASS", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
