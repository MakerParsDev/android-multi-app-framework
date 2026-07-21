#!/usr/bin/env python3
"""Regression tests for runtime observability policy and release decisions."""

from __future__ import annotations

import copy
import unittest
from pathlib import Path

from runtime_health_policy import (
    evaluate_snapshot,
    load_json,
    parse_flavor_packages,
    validate_catalogs,
    validate_policy,
)

ROOT = Path(__file__).resolve().parents[2]


class RuntimeHealthPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = load_json(ROOT / "config/runtime-observability-policy.json")
        self.flavors = parse_flavor_packages(ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt")
        self.snapshot = load_json(ROOT / "config/runtime-health-snapshot.example.json")

    def test_repository_policy_is_valid(self) -> None:
        self.assertEqual([], validate_policy(self.policy))

    def test_flavor_and_firebase_catalogs_match(self) -> None:
        firebase_apps = load_json(ROOT / "config/firebase-apps.json")
        self.assertEqual([], validate_catalogs(self.flavors, firebase_apps))
        self.assertEqual(17, len(self.flavors))

    def test_healthy_snapshot_is_healthy(self) -> None:
        result = evaluate_snapshot(self.policy, self.snapshot, self.flavors)
        self.assertEqual("healthy", result["decision"])
        self.assertEqual([], result["errors"])

    def test_watch_threshold_produces_watch(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["metrics"]["crash_rate"] = 0.005
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("watch", result["decision"])

    def test_hotfix_threshold_produces_hotfix(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["metrics"]["billing_verification_failure_rate"] = 0.05
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("hotfix", result["decision"])

    def test_rollback_has_priority_over_other_breaches(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["metrics"]["crash_rate"] = 0.006
        snapshot["metrics"]["anr_rate"] = 0.02
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("rollback", result["decision"])
        self.assertEqual("anr_rate", result["breaches"][1]["metric"])

    def test_missing_metric_is_incomplete(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        del snapshot["metrics"]["remote_config_failure_rate"]
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("incomplete", result["decision"])
        self.assertTrue(result["errors"])

    def test_package_mismatch_is_incomplete(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["package_name"] = "com.parsfilo.wrong"
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("incomplete", result["decision"])

    def test_invalid_threshold_order_is_rejected(self) -> None:
        policy = copy.deepcopy(self.policy)
        policy["metrics"]["crash_rate"]["watch"] = 0.5
        errors = validate_policy(policy)
        self.assertTrue(any("thresholds must increase" in error for error in errors))

    def test_unknown_checkpoint_is_incomplete(self) -> None:
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["checkpoint_hours"] = 12
        result = evaluate_snapshot(self.policy, snapshot, self.flavors)
        self.assertEqual("incomplete", result["decision"])


if __name__ == "__main__":
    unittest.main()
