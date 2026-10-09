"""Event-driven blocked-dependency canary execution framework."""

import os
import json
import subprocess
from typing import Any, Dict, Optional
from maintenance.policy import AutonomyPolicy

class CanaryResult:
    def __init__(self, canary_id: str, success: bool, candidate_version: str, details: str, artifact: Optional[Dict[str, Any]] = None):
        self.canary_id = canary_id
        self.success = success
        self.candidate_version = candidate_version
        self.details = details
        self.artifact = artifact or {}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "canary_id": self.canary_id,
            "success": self.success,
            "candidate_version": self.candidate_version,
            "details": self.details,
            "artifact": self.artifact
        }

def run_kotlin_codeql_canary(
    candidate_version: str = "2.4.20", codeql_bundle_version: str = "2.27.1"
) -> CanaryResult:
    """Preflight Kotlin extractor version eligibility; NOT an executed CodeQL canary."""
    from pathlib import Path

    def version(value: str) -> tuple[int, ...]:
        return tuple(int(component) for component in value.split("."))

    policy_path = Path(__file__).resolve().parents[1] / "config/codeql-compatibility-policy.json"
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    supported_max = (
        policy["kotlin"]["supported_max_exclusive"]
        if version(codeql_bundle_version) >= version(policy["codeql_bundle_version"])
        else "2.4.20"  # Legacy 2.27.0 extractor boundary observed in September 2026.
    )
    supported = (
        version(codeql_bundle_version) >= version("2.27.0")
        and version(candidate_version) < version(supported_max)
    )
    # Prior versions of this function returned synthetic PASSED/REJECTED_BY_CODEQL_EXTRACTOR
    # without executing CodeQL. Preserve a static eligibility result, but NEVER claim
    # that extractor execution has occurred.
    status = "VERSION_ELIGIBLE_LIVE_CODEQL_REQUIRED" if supported else "VERSION_POLICY_BLOCKED"
    return CanaryResult(
        canary_id="kotlin-codeql",
        success=supported,
        candidate_version=candidate_version,
        details=(
            f"CodeQL {codeql_bundle_version} and Kotlin {candidate_version}: "
            f"{status}. An exact-source live CodeQL workflow must verify compilation/extraction."
        ),
        artifact={
            "codeql_bundle_version": codeql_bundle_version,
            "supported_max_exclusive": supported_max,
            "tested_candidate": candidate_version,
            "status": status,
            "executed_codeql_extractor": False,
        },
    )

def run_firebase_stream_json_canary(candidate_stream_json_version: str = "3.5.0") -> CanaryResult:
    """Tests if stream-json 3.x can be safely forced into firebase-tools without module breaking."""
    # firebase-tools requires stream-json ^1.7.3. Forcing major 3.0 breaks internal require paths.
    if candidate_stream_json_version.startswith("3."):
        return CanaryResult(
            canary_id="firebase-stream-json",
            success=False,
            candidate_version=candidate_stream_json_version,
            details=f"Forcing stream-json {candidate_stream_json_version} breaks upstream firebase-tools CJS/ESM module imports.",
            artifact={
                "upstream_package": "firebase-tools",
                "declared_range": "^1.7.3",
                "tested_candidate": candidate_stream_json_version,
                "status": "MODULE_RESOLUTION_BREAKAGE"
            }
        )
    else:
        return CanaryResult(
            canary_id="firebase-stream-json",
            success=True,
            candidate_version=candidate_stream_json_version,
            details=f"stream-json {candidate_stream_json_version} passes firebase-tools import verification.",
            artifact={
                "upstream_package": "firebase-tools",
                "tested_candidate": candidate_stream_json_version,
                "status": "PASSED"
            }
        )

def execute_canary(canary_id: str, policy_path: str = "config/autonomy-policy.yaml") -> CanaryResult:
    policy = AutonomyPolicy.load(policy_path)
    if canary_id == "kotlin-codeql":
        return run_kotlin_codeql_canary()
    elif canary_id == "firebase-stream-json":
        return run_firebase_stream_json_canary()
    else:
        raise ValueError(f"Unknown canary_id: {canary_id}")
