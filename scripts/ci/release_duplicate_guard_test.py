#!/usr/bin/env python3
"""Offline contract checks for release idempotency across manual dispatches."""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

import yaml

from release_duplicate_guard import (
    normalize_flavors, prior_successful_publication, verify_no_successful_duplicate,
)

SHA = "a" * 40
ROOT = Path(__file__).resolve().parents[2]


def run(run_id: int, flavors="amenerrasulu", track="production",
        sha=SHA, conclusion="success", status="completed") -> dict:
    return {
        "id": run_id, "status": status, "conclusion": conclusion,
        "display_title": f"Release: {flavors} | {track} | {sha}",
    }


def test_idempotency_for_same_commit_track_and_flavors() -> None:
    assert prior_successful_publication(
        [run(11)], current_id=12, sha=SHA, track="production",
        flavors=("amenerrasulu",),
    ) == 11


def test_distinct_tracks_and_commits_are_not_duplicates() -> None:
    for prior in (run(11, track="internal"), run(11, sha="b" * 40),
                  run(11, flavors="yasinsuresi")):
        assert prior_successful_publication(
            [prior], current_id=12, sha=SHA, track="production",
            flavors=("amenerrasulu",),
        ) is None


def test_overlapping_flavors_and_all_are_duplicate_publications() -> None:
    for earlier_flavors, requested in (
        ("all", ("amenerrasulu",)),
        ("amenerrasulu", ("all",)),
        ("amenerrasulu", ("amenerrasulu", "yasinsuresi")),
        ("amenerrasulu,yasinsuresi", ("yasinsuresi",)),
    ):
        assert prior_successful_publication(
            [run(11, flavors=earlier_flavors)], current_id=12,
            sha=SHA, track="production", flavors=requested,
        ) == 11


def test_failed_or_active_run_does_not_imply_publication() -> None:
    for prior in (run(11, conclusion="failure"), run(11, status="in_progress",
                  conclusion=None), run(12)):
        assert prior_successful_publication(
            [prior], current_id=12, sha=SHA, track="production",
            flavors=("amenerrasulu",),
        ) is None


def test_flavor_normalization_and_invalid_entries() -> None:
    assert normalize_flavors("yasinsuresi,amenerrasulu") == (
        "amenerrasulu", "yasinsuresi",
    )
    assert normalize_flavors("all") == ("all",)
    assert prior_successful_publication(
        [run(11, "yasinsuresi,amenerrasulu")], current_id=12,
        sha=SHA, track="production", flavors=("amenerrasulu", "yasinsuresi"),
    ) == 11
    for invalid in ("", "ALL", "amenerrasulu,amenerrasulu", "one;two",
                    " amenerrasulu", "amenerrasulu,"):
        try:
            normalize_flavors(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError(f"accepted invalid publication flavors: {invalid}")


def test_workflow_contract_and_no_permissions_expansion() -> None:
    path = ROOT / ".github/workflows/release.yml"
    workflow = yaml.safe_load(path.read_text())
    assert "Release: ${{ inputs.target_flavors }}" in workflow["run-name"]
    gate = next(s for s in workflow["jobs"]["security-gate"]["steps"]
                if s.get("name") == "Validate release publication prerequisites")
    assert "RELEASE_TARGET_FLAVORS" in gate["env"]
    assert "GITHUB_RUN_ID" in gate["env"]
    assert workflow["jobs"]["security-gate"]["permissions"] == {
        "contents": "read", "actions": "read",
    }
    publication_env = workflow["jobs"]["publish-play"]["environment"]
    assert isinstance(publication_env, dict)
    assert "'play-production-auto' || 'production'" in publication_env["name"]
    assert workflow["concurrency"]["cancel-in-progress"] is False


def test_fail_closed_on_incomplete_github_history() -> None:
    env = {
        "GITHUB_SHA": SHA, "GITHUB_REPOSITORY": "MakerParsDev/android-multi-app-framework",
        "GITHUB_TOKEN": "offline-test", "GITHUB_RUN_ID": "12",
        "RELEASE_TARGET_FLAVORS": "amenerrasulu", "RELEASE_DO_PUBLISH": "true",
    }

    class FakeResponse:
        def __init__(self, value):
            self.value = value

        def __enter__(self):
            return self

        def __exit__(self, *args):
            return False

        def read(self):
            return json.dumps(self.value).encode()

    for value in ({"workflow_runs": [run(11)], "total_count": 1},
                  {"workflow_runs": [run(11)], "total_count": 101},
                  {"workflow_runs": None}):
        with patch("release_duplicate_guard.urlopen", return_value=FakeResponse(value)):
            try:
                verify_no_successful_duplicate(env)
            except ValueError:
                pass
            else:
                raise AssertionError("expected duplicate/incomplete history rejection")
    with patch("release_duplicate_guard.urlopen", return_value=FakeResponse({
        "workflow_runs": [run(11, conclusion="failure")], "total_count": 1,
    })):
        verify_no_successful_duplicate(env)


def main() -> int:
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS", name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
