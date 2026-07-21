#!/usr/bin/env python3
"""Validate the runtime observability policy, flavor catalog, and code wiring."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Dict, List

from runtime_health_policy import load_json, parse_flavor_packages, validate_catalogs, validate_policy

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "config/runtime-observability-policy.json"
FLAVORS = ROOT / "buildSrc/src/main/kotlin/FlavorConfig.kt"
FIREBASE_APPS = ROOT / "config/firebase-apps.json"
REPORT = ROOT / "build/reports/observability/policy-validation.json"

CODE_REQUIREMENTS = {
    "app/build.gradle.kts": [
        "RELEASE_REVISION",
        "RELEASE_TRACK",
        "BUILD_SOURCEVERSION",
    ],
    "app/src/main/java/com/parsfilo/contentapp/App.kt": [
        "runtimeObservability.configure",
        "BuildConfig.VERSION_CODE",
        "BuildConfig.RELEASE_REVISION",
        "BuildConfig.RELEASE_TRACK",
    ],
    "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/di/RuntimeObservabilityModule.kt": [
        "provideRuntimeObservability",
    ],
    "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/RuntimeObservability.kt": [
        "BILLING_PURCHASE_VERIFICATION",
        "REMOTE_CONFIG_FETCH",
        "UMP_CONSENT",
        "MOBILE_ADS_INITIALIZATION",
    ],
    "feature/billing/src/main/java/com/parsfilo/contentapp/feature/billing/BillingPurchaseVerifier.kt": [
        "RuntimeSignal.BILLING_PURCHASE_VERIFICATION",
    ],
    "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/config/RemoteConfigManager.kt": [
        "RuntimeSignal.REMOTE_CONFIG_FETCH",
    ],
    "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdManager.kt": [
        "RuntimeSignal.UMP_CONSENT",
        "RuntimeSignal.MOBILE_ADS_INITIALIZATION",
    ],
    "feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/AdRevenueLogger.kt": [
        "AnalyticsEventName.AD_FAILED_TO_LOAD",
    ],
    "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/AnalyticsConsent.kt": [
        "AnalyticsEventName.CONSENT_ERROR",
    ],
    "azure-pipelines/release-health.yml": [
        "security-gate.yml",
        "evaluate_release_health.py",
        "PublishPipelineArtifact@1",
    ],
    "docs/RELEASE_HEALTH_RUNBOOK.md": [
        "+24",
        "+48",
        "+72",
        "ROLLBACK",
        "Play grouped error triage",
    ],
    "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/push/PushRegistrationManager.kt": [
        "recordException",
    ],
}


def validate_code_requirements() -> List[str]:
    errors: List[str] = []
    for relative_path, needles in CODE_REQUIREMENTS.items():
        path = ROOT / relative_path
        if not path.is_file():
            errors.append("required observability file missing: %s" % relative_path)
            continue
        text = path.read_text(encoding="utf-8")
        for needle in needles:
            if needle not in text:
                errors.append("%s must contain %s" % (relative_path, needle))
    return errors


def main() -> int:
    policy = load_json(POLICY)
    flavors = parse_flavor_packages(FLAVORS)
    firebase_apps = load_json(FIREBASE_APPS)
    errors = [
        *validate_policy(policy),
        *validate_catalogs(flavors, firebase_apps),
        *validate_code_requirements(),
    ]
    payload: Dict[str, object] = {
        "valid": not errors,
        "flavor_count": len(flavors),
        "metric_count": len(policy.get("metrics", {})),
        "required_signal_count": len(policy.get("required_signals", {})),
        "errors": errors,
    }
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        print("Runtime observability validation failed:", file=sys.stderr)
        for error in errors:
            print("  - %s" % error, file=sys.stderr)
        return 1
    print(
        "Runtime observability policy passed: %d flavors, %d metrics, %d required signals"
        % (payload["flavor_count"], payload["metric_count"], payload["required_signal_count"])
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
