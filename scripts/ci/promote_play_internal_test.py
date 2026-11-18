from __future__ import annotations

import unittest
from typing import Any

from promote_play_internal import make_plan, promote


def starting_tracks(
    *,
    internal_code: int = 76,
    production_code: int = 61,
    production_status: str = "completed",
    internal_status: str = "completed",
) -> list[dict[str, Any]]:
    return [
        {"track": "internal", "releases": [
            {"name": "internal-source-sha", "versionCodes": [str(internal_code)], "status": internal_status},
        ]},
        {"track": "production", "releases": [
            {"name": f"1.0.{production_code}", "versionCodes": [str(production_code)],
             "status": production_status},
        ]},
    ]


class Request:
    def __init__(self, function):
        self.function = function

    def execute(self, num_retries: int = 0):
        return self.function()


class FakeEdits:
    def __init__(self, tracks):
        self.current = tracks
        self.calls: list[str] = []
        self.pending: dict | None = None
        self.counter = 0
        self.fault: str | None = None

    def insert(self, **kwargs):
        self.calls.append("insert")
        self.counter += 1
        return Request(lambda: {"id": f"edit-{self.counter}"})

    def tracks(self):
        return self

    def list(self, **kwargs):
        self.calls.append("tracks.list")
        return Request(lambda: {"tracks": self.current})

    def update(self, **kwargs):
        self.calls.append("tracks.update")
        self.pending = kwargs["body"]
        return Request(dict)

    def validate(self, **kwargs):
        self.calls.append("validate")
        if self.fault == "validate":
            return Request(lambda: (_ for _ in ()).throw(RuntimeError("validation failed")))
        return Request(dict)

    def commit(self, **kwargs):
        self.calls.append("commit")

        def commit():
            assert self.pending is not None
            self.current = [
                self.current[0],
                {"track": "production", "releases": self.pending["releases"]},
            ]
            self.pending = None
            return {}

        return Request(commit)

    def delete(self, **kwargs):
        self.calls.append("delete")
        return Request(dict)


class FakeService:
    def __init__(self, tracks=None):
        self.edits_api = FakeEdits(tracks or starting_tracks())

    def edits(self):
        return self.edits_api


class PromotionTests(unittest.TestCase):
    def test_plan_uses_exact_internal_version_without_new_bundle(self):
        plan = make_plan(
            starting_tracks(), flavor="amenerrasulu",
            expected_internal_code=76, expected_production_code=61,
        )
        self.assertEqual(plan["source_release_name"], "internal-source-sha")
        self.assertEqual(plan["target_release"], {
            "name": "1.0.76", "versionCodes": ["76"], "status": "completed",
        })

    def test_ad_inspector_diagnostic_internal_release_cannot_be_promoted(self):
        tracks = starting_tracks()
        tracks[0]["releases"][0]["name"] = (
            "internal-ad-inspector-debug-source-sha"
        )
        with self.assertRaisesRegex(ValueError, "diagnostic-only"):
            make_plan(
                tracks,
                flavor="namazsurelerivedualarsesli",
                expected_internal_code=76,
                expected_production_code=61,
            )
    def test_preview_never_updates_or_commits(self):
        service = FakeService()
        result = promote(service, package_name="com.parsfilo.amenerrasulu",
                         flavor="amenerrasulu", expected_internal_code=76,
                         expected_production_code=61, execute=False)
        self.assertEqual(result["state"], "PREVIEW")
        self.assertEqual(service.edits_api.calls, ["insert", "tracks.list", "delete"])

    def test_execute_exact_promotion_verifies_fresh_production_track(self):
        service = FakeService()
        result = promote(service, package_name="com.parsfilo.amenerrasulu",
                         flavor="amenerrasulu", expected_internal_code=76,
                         expected_production_code=61, execute=True)
        self.assertEqual(result["state"], "VERIFIED")
        self.assertEqual(service.edits_api.calls, [
            "insert", "tracks.list", "tracks.update", "validate", "commit",
            "insert", "tracks.list", "delete",
        ])
        self.assertEqual(service.edits_api.current[1]["releases"][0]["versionCodes"], ["76"])

    def test_wrong_internal_code_never_commits(self):
        service = FakeService()
        with self.assertRaisesRegex(ValueError, "internal"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=77,
                    expected_production_code=61, execute=True)
        self.assertNotIn("tracks.update", service.edits_api.calls)
        self.assertIn("delete", service.edits_api.calls)

    def test_wrong_current_production_code_never_commits(self):
        service = FakeService()
        with self.assertRaisesRegex(ValueError, "production"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=76,
                    expected_production_code=60, execute=True)
        self.assertNotIn("commit", service.edits_api.calls)

    def test_staged_or_halted_production_blocks_replacement(self):
        for status in ("inProgress", "halted"):
            with self.subTest(status=status):
                service = FakeService(starting_tracks(production_status=status))
                with self.assertRaisesRegex(ValueError, "production"):
                    promote(service, package_name="com.parsfilo.amenerrasulu",
                            flavor="amenerrasulu", expected_internal_code=76,
                            expected_production_code=61, execute=True)
                self.assertNotIn("tracks.update", service.edits_api.calls)

    def test_noncompleted_internal_blocks_promotion(self):
        service = FakeService(starting_tracks(internal_status="draft"))
        with self.assertRaisesRegex(ValueError, "internal"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=76,
                    expected_production_code=61, execute=True)
        self.assertNotIn("commit", service.edits_api.calls)

    def test_validation_failure_discards_edit_without_commit(self):
        service = FakeService()
        service.edits_api.fault = "validate"
        with self.assertRaisesRegex(RuntimeError, "validation failed"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=76,
                    expected_production_code=61, execute=True)
        self.assertIn("delete", service.edits_api.calls)
        self.assertNotIn("commit", service.edits_api.calls)

    def test_reject_nonadvancing_version_and_ambiguous_production(self):
        with self.assertRaisesRegex(ValueError, "newer"):
            make_plan(starting_tracks(internal_code=61), flavor="amenerrasulu",
                      expected_internal_code=61, expected_production_code=61)
        tracks = starting_tracks()
        tracks[1]["releases"].append({"status": "completed", "versionCodes": ["60"]})
        with self.assertRaisesRegex(ValueError, "production"):
            make_plan(tracks, flavor="amenerrasulu",
                      expected_internal_code=76, expected_production_code=61)


    def test_postcommit_remote_mismatch_never_claims_verification_or_retries(self):
        service = FakeService()
        original_list = service.edits_api.list

        def stale_verification(**kwargs):
            if service.edits_api.counter >= 2:
                service.edits_api.calls.append("tracks.list")
                return Request(lambda: {"tracks": starting_tracks()})
            return original_list(**kwargs)

        service.edits_api.list = stale_verification
        with self.assertRaisesRegex(RuntimeError, "may have committed"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=76,
                    expected_production_code=61, execute=True)
        self.assertEqual(service.edits_api.calls.count("commit"), 1)

    def test_repeated_promotion_rejects_stale_production_precondition(self):
        service = FakeService()
        promote(service, package_name="com.parsfilo.amenerrasulu",
                flavor="amenerrasulu", expected_internal_code=76,
                expected_production_code=61, execute=True)
        with self.assertRaisesRegex(ValueError, "production"):
            promote(service, package_name="com.parsfilo.amenerrasulu",
                    flavor="amenerrasulu", expected_internal_code=76,
                    expected_production_code=61, execute=True)
        self.assertEqual(service.edits_api.calls.count("commit"), 1)


if __name__ == "__main__":
    unittest.main()
