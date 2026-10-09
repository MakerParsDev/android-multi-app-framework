#!/usr/bin/env python3
"""Regression tests for exact-source GitHub dependency graph freshness."""

from __future__ import annotations

from pathlib import Path
import os
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import wait_for_dependency_submission as gate


SHA = "a" * 40


def record(
    state: str, conclusion: str | None = None,
    *, run_id: int = 42, head_sha: str = SHA,
    event: str = "push", branch: str = "main",
) -> dict:
    return {
        "id": run_id,
        "head_sha": head_sha,
        "head_branch": branch,
        "event": event,
        "status": state,
        "conclusion": conclusion,
        "created_at": f"2026-10-10T00:00:{run_id % 60:02d}Z",
    }


class SubmissionGateTests(unittest.TestCase):
    def test_classifies_exact_workflow_path_patterns(self):
        for path in (
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradle.properties", "settings.gradle.kts",
            "build.gradle.kts", "app/build.gradle.kts",
            "buildSrc/build.gradle.kts", "buildSrc/src/main/kotlin/Task.kt",
            "feature/example/legacy.gradle",
        ):
            with self.subTest(path=path):
                self.assertTrue(gate.gradle_affected([path]))
        for path in (
            "README.md", "docs/CI_CD.md", ".github/workflows/maintenance-health.yml",
            "feature/a/src/main/File.kt", "scripts/ci/install_osv_scanner.sh",
        ):
            with self.subTest(path=path):
                self.assertFalse(gate.gradle_affected([path]))

    def test_exact_main_push_is_required(self):
        self.assertEqual(
            gate.classify_runs([record("completed", "success")], SHA)[0], "ready"
        )
        self.assertEqual(
            gate.classify_runs([record("completed", "success", event="workflow_dispatch")], SHA)[0],
            "ready",
        )
        for options in (
            {"head_sha": "b" * 40}, {"branch": "feature"},
            {"event": "pull_request"},
        ):
            self.assertEqual(
                gate.classify_runs([record("completed", "success", **options)], SHA)[0],
                "wait",
            )

    def test_pending_and_completed_failures_are_not_accepted(self):
        for state in ("queued", "in_progress", "waiting", "pending", "requested"):
            self.assertEqual(gate.classify_runs([record(state)], SHA)[0], "wait")
        for conclusion in ("failure", "cancelled", "skipped", "timed_out", None):
            self.assertEqual(
                gate.classify_runs([record("completed", conclusion)], SHA)[0],
                "failed",
            )

    def test_latest_run_supersedes_old_success(self):
        self.assertEqual(
            gate.classify_runs([
                record("completed", "success", run_id=40),
                record("in_progress", run_id=45),
            ], SHA)[0],
            "wait",
        )
        self.assertEqual(
            gate.classify_runs([
                record("completed", "success", run_id=40),
                record("completed", "failure", run_id=45),
            ], SHA)[0],
            "failed",
        )

    @patch.object(gate.time, "sleep")
    @patch.object(gate, "workflow_runs")
    def test_waits_for_matching_success_before_scanning(self, fetch, sleep):
        fetch.side_effect = [
            [], [record("queued")], [record("in_progress")],
            [record("completed", "success")],
        ]
        self.assertIn("succeeded", gate.await_submission(
            "MakerParsDev/android-multi-app-framework", SHA, "opaque",
            timeout_seconds=120, poll_seconds=1,
        ))
        self.assertEqual(fetch.call_count, 4)
        self.assertEqual(sleep.call_count, 3)

    @patch.object(gate, "workflow_runs")
    def test_fails_closed_when_matching_submission_failed(self, fetch):
        fetch.return_value = [record("completed", "failure")]
        with self.assertRaisesRegex(gate.SubmissionError, "failure"):
            gate.await_submission("owner/repo", SHA, "opaque")

    @patch.object(gate.time, "sleep")
    @patch.object(gate, "workflow_runs", return_value=[])
    def test_times_out_if_run_never_appears(self, fetch, sleep):
        with patch.object(gate.time, "monotonic", side_effect=[1, 2, 3, 4, 10]):
            with self.assertRaisesRegex(gate.SubmissionError, "Timed out"):
                gate.await_submission("owner/repo", SHA, "opaque", timeout_seconds=2)
        fetch.assert_called()

    def test_rejects_invalid_repo_sha_and_credentials(self):
        for repo, sha, token in (
            ("../repo", SHA, "opaque"), ("owner/repo", "HEAD", "opaque"),
            ("owner/repo", SHA, ""),
        ):
            with self.assertRaises(gate.SubmissionError):
                gate.await_submission(repo, sha, token)

    @patch.object(gate, "changed_paths", return_value=["docs/README.md"])
    @patch.object(gate, "await_submission")
    def test_docs_only_has_no_submission_wait(self, wait, changed):
        with patch.dict(os.environ, {"GITHUB_REF": "refs/heads/main"}):
            with patch.object(sys, "argv", ["wait_for_dependency_submission.py"]):
                self.assertEqual(gate.main(), 0)
        wait.assert_not_called()

    def test_workflow_uses_read_only_actions_and_fail_closed_dashboard(self):
        workflow = Path(__file__).resolve().parents[2] / ".github/workflows/maintenance-health.yml"
        source = workflow.read_text(encoding="utf-8")
        self.assertIn("actions: read", source)
        self.assertIn("fetch-depth: 2", source)
        self.assertIn("wait_for_dependency_submission.py", source)
        self.assertIn("needs.health-check.result == 'success'", source)

    def test_invalid_run_list_rejected(self):
        self.assertEqual(gate.classify_runs([], SHA)[0], "wait")
        self.assertEqual(gate.classify_runs([record("bad_state")], SHA)[0], "failed")


if __name__ == "__main__":
    unittest.main()
