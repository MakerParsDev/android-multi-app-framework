#!/usr/bin/env python3
"""Shared validation and evaluation logic for post-release health snapshots."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Dict, List, Mapping, Sequence, Tuple

DECISION_RANK = {
    "healthy": 0,
    "watch": 1,
    "hotfix": 2,
    "rollback": 3,
    "incomplete": 4,
}
REQUIRED_SOURCES = {
    "crash_anr_rate",
    "fatal_stack_and_nonfatal",
    "product_and_consent_funnel",
    "ad_delivery_and_revenue",
    "release_identity",
}
REQUIRED_SIGNALS = {
    "billing_purchase_verification_failure",
    "ad_failed_to_load",
    "consent_error",
    "push_registration_sync_failed",
    "remote_config_fetch_failure",
}
REQUIRED_CONTEXT_KEYS = {
    "package_name",
    "flavor",
    "version_code",
    "version_name",
    "build_type",
    "release_revision",
    "release_track",
}
FLAVOR_BLOCK_PATTERN = re.compile(
    r"FlavorConfig\(\s*name\s*=\s*\"(?P<name>[^\"]+)\".*?"
    r"packageName\s*=\s*\"(?P<package>[^\"]+)\"",
    re.DOTALL,
)


def load_json(path: Path) -> Dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("JSON root must be an object: %s" % path)
    return payload


def parse_flavor_packages(path: Path) -> Dict[str, str]:
    text = path.read_text(encoding="utf-8")
    result = {
        match.group("name"): match.group("package")
        for match in FLAVOR_BLOCK_PATTERN.finditer(text)
    }
    if not result:
        raise ValueError("No flavor/package pairs found in %s" % path)
    return result


def validate_policy(policy: Mapping[str, Any]) -> List[str]:
    errors: List[str] = []
    if policy.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    errors.extend(_validate_source_authority(policy.get("source_authority")))
    errors.extend(_validate_context_keys(policy.get("release_context_keys")))
    errors.extend(_validate_required_signals(policy.get("required_signals")))
    errors.extend(_validate_metrics(policy.get("metrics")))
    errors.extend(_validate_checkpoints(policy.get("checkpoints"), policy.get("metrics")))
    errors.extend(_validate_actions(policy.get("decision_actions")))
    return errors


def _validate_source_authority(raw: Any) -> List[str]:
    if not isinstance(raw, dict):
        return ["source_authority must be an object"]
    missing = sorted(REQUIRED_SOURCES - set(raw))
    return ["source_authority missing: %s" % ", ".join(missing)] if missing else []


def _validate_context_keys(raw: Any) -> List[str]:
    if not isinstance(raw, list) or not all(isinstance(item, str) for item in raw):
        return ["release_context_keys must be a string array"]
    missing = sorted(REQUIRED_CONTEXT_KEYS - set(raw))
    return ["release_context_keys missing: %s" % ", ".join(missing)] if missing else []


def _validate_required_signals(raw: Any) -> List[str]:
    if not isinstance(raw, dict):
        return ["required_signals must be an object"]
    errors: List[str] = []
    missing = sorted(REQUIRED_SIGNALS - set(raw))
    if missing:
        errors.append("required_signals missing: %s" % ", ".join(missing))
    for signal, entry in raw.items():
        if not isinstance(entry, dict):
            errors.append("signal %s must be an object" % signal)
            continue
        for key in ("source", "owner"):
            if not isinstance(entry.get(key), str) or not entry[key].strip():
                errors.append("signal %s must define %s" % (signal, key))
    return errors


def _validate_metrics(raw: Any) -> List[str]:
    if not isinstance(raw, dict) or not raw:
        return ["metrics must be a non-empty object"]
    errors: List[str] = []
    for metric, entry in raw.items():
        errors.extend(_validate_metric(metric, entry))
    return errors


def _validate_metric(metric: str, raw: Any) -> List[str]:
    if not isinstance(raw, dict):
        return ["metric %s must be an object" % metric]
    direction = raw.get("direction")
    thresholds, threshold_errors = _read_metric_thresholds(metric, raw)
    errors = [*threshold_errors, *_validate_metric_text_fields(metric, raw)]
    if direction not in ("higher_is_worse", "lower_is_worse"):
        errors.append("metric %s has invalid direction" % metric)
    if len(thresholds) == 3:
        errors.extend(_validate_threshold_order(metric, direction, thresholds))
    return errors


def _read_metric_thresholds(
    metric: str,
    raw: Mapping[str, Any],
) -> Tuple[List[float], List[str]]:
    thresholds: List[float] = []
    errors: List[str] = []
    for level in ("watch", "hotfix", "rollback"):
        value = raw.get(level)
        if _is_number(value):
            thresholds.append(float(value))
        else:
            errors.append("metric %s threshold %s must be numeric" % (metric, level))
    return thresholds, errors


def _validate_metric_text_fields(metric: str, raw: Mapping[str, Any]) -> List[str]:
    return [
        "metric %s must define %s" % (metric, key)
        for key in ("source", "unit")
        if not isinstance(raw.get(key), str) or not raw[key].strip()
    ]


def _validate_threshold_order(
    metric: str,
    direction: Any,
    thresholds: Sequence[float],
) -> List[str]:
    if direction == "higher_is_worse" and not thresholds[0] <= thresholds[1] <= thresholds[2]:
        return ["metric %s thresholds must increase watch <= hotfix <= rollback" % metric]
    if direction == "lower_is_worse" and not thresholds[0] >= thresholds[1] >= thresholds[2]:
        return ["metric %s thresholds must decrease watch >= hotfix >= rollback" % metric]
    return []


def _validate_checkpoints(raw: Any, metrics_raw: Any) -> List[str]:
    if not isinstance(raw, dict):
        return ["checkpoints must be an object"]
    metrics = set(metrics_raw) if isinstance(metrics_raw, dict) else set()
    errors: List[str] = []
    for checkpoint in ("24", "48", "72"):
        entry = raw.get(checkpoint)
        if not isinstance(entry, dict):
            errors.append("checkpoint %s must be defined" % checkpoint)
            continue
        required = entry.get("required_metrics")
        if not isinstance(required, list) or not all(isinstance(item, str) for item in required):
            errors.append("checkpoint %s required_metrics must be a string array" % checkpoint)
            continue
        unknown = sorted(set(required) - metrics)
        missing = sorted(metrics - set(required))
        if unknown:
            errors.append("checkpoint %s has unknown metrics: %s" % (checkpoint, ", ".join(unknown)))
        if missing:
            errors.append("checkpoint %s omits metrics: %s" % (checkpoint, ", ".join(missing)))
        ack = entry.get("ack_minutes")
        if isinstance(ack, bool) or not isinstance(ack, int) or ack <= 0:
            errors.append("checkpoint %s ack_minutes must be a positive integer" % checkpoint)
    return errors


def _validate_actions(raw: Any) -> List[str]:
    if not isinstance(raw, dict):
        return ["decision_actions must be an object"]
    missing = sorted(set(DECISION_RANK) - set(raw))
    return ["decision_actions missing: %s" % ", ".join(missing)] if missing else []


def validate_catalogs(
    flavors: Mapping[str, str],
    firebase_apps: Mapping[str, Any],
) -> List[str]:
    errors: List[str] = []
    flavor_names = set(flavors)
    firebase_names = set(firebase_apps)
    missing = sorted(flavor_names - firebase_names)
    unexpected = sorted(firebase_names - flavor_names)
    if missing:
        errors.append("Firebase app catalog missing flavors: %s" % ", ".join(missing))
    if unexpected:
        errors.append("Firebase app catalog has unexpected flavors: %s" % ", ".join(unexpected))
    packages = list(flavors.values())
    if len(packages) != len(set(packages)):
        errors.append("Flavor package names must be unique")
    for flavor in sorted(flavor_names & firebase_names):
        entry = firebase_apps[flavor]
        if not isinstance(entry, dict):
            errors.append("Firebase app entry %s must be an object" % flavor)
            continue
        for key in ("projectId", "appId"):
            value = entry.get(key)
            if not isinstance(value, str) or not value.strip():
                errors.append("Firebase app entry %s must define %s" % (flavor, key))
    return errors


def validate_snapshot_identity(
    snapshot: Mapping[str, Any],
    flavors: Mapping[str, str],
) -> List[str]:
    errors: List[str] = []
    flavor = snapshot.get("flavor")
    package_name = snapshot.get("package_name")
    if not isinstance(flavor, str) or flavor not in flavors:
        errors.append("snapshot flavor is unknown")
    elif package_name != flavors[flavor]:
        errors.append("snapshot package_name does not match flavor catalog")
    for key in ("version_code", "checkpoint_hours"):
        value = snapshot.get(key)
        if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
            errors.append("snapshot %s must be a positive integer" % key)
    for key in ("version_name", "release_revision", "release_track"):
        value = snapshot.get(key)
        if not isinstance(value, str) or not value.strip():
            errors.append("snapshot %s must be a non-empty string" % key)
    return errors


def evaluate_snapshot(
    policy: Mapping[str, Any],
    snapshot: Mapping[str, Any],
    flavors: Mapping[str, str],
) -> Dict[str, Any]:
    errors = validate_snapshot_identity(snapshot, flavors)
    checkpoint_policy = _resolve_checkpoint_policy(policy, snapshot, errors)
    metrics_payload = _resolve_metrics_payload(snapshot, errors)
    required_metrics = checkpoint_policy.get("required_metrics", [])
    missing_metrics = _missing_required_metrics(required_metrics, metrics_payload)
    if missing_metrics:
        errors.append("required metrics missing or non-numeric: %s" % ", ".join(missing_metrics))

    breaches, decision = _evaluate_metric_breaches(
        policy=policy,
        required_metrics=required_metrics,
        missing_metrics=missing_metrics,
        metrics_payload=metrics_payload,
    )
    if errors:
        decision = "incomplete"
    actions = policy.get("decision_actions", {})
    action = actions.get(decision, "") if isinstance(actions, dict) else ""
    return {
        "decision": decision,
        "action": action,
        "checkpoint_hours": snapshot.get("checkpoint_hours"),
        "ack_minutes": checkpoint_policy.get("ack_minutes", 0),
        "flavor": snapshot.get("flavor"),
        "package_name": snapshot.get("package_name"),
        "version_code": snapshot.get("version_code"),
        "release_revision": snapshot.get("release_revision"),
        "release_track": snapshot.get("release_track"),
        "breaches": breaches,
        "errors": errors,
        "metrics": dict(metrics_payload),
    }


def _resolve_checkpoint_policy(
    policy: Mapping[str, Any],
    snapshot: Mapping[str, Any],
    errors: List[str],
) -> Mapping[str, Any]:
    checkpoints = policy.get("checkpoints", {})
    checkpoint = str(snapshot.get("checkpoint_hours", ""))
    checkpoint_policy = checkpoints.get(checkpoint) if isinstance(checkpoints, dict) else None
    if isinstance(checkpoint_policy, dict):
        return checkpoint_policy
    errors.append("checkpoint_hours must be one of 24, 48, or 72")
    return {"required_metrics": [], "ack_minutes": 0}


def _resolve_metrics_payload(
    snapshot: Mapping[str, Any],
    errors: List[str],
) -> Mapping[str, Any]:
    metrics_payload = snapshot.get("metrics")
    if isinstance(metrics_payload, dict):
        return metrics_payload
    errors.append("snapshot metrics must be an object")
    return {}


def _missing_required_metrics(
    required_metrics: Sequence[str],
    metrics_payload: Mapping[str, Any],
) -> List[str]:
    return sorted(
        name for name in required_metrics if not _is_number(metrics_payload.get(name))
    )


def _evaluate_metric_breaches(
    policy: Mapping[str, Any],
    required_metrics: Sequence[str],
    missing_metrics: Sequence[str],
    metrics_payload: Mapping[str, Any],
) -> Tuple[List[Dict[str, Any]], str]:
    metric_policy = policy.get("metrics", {})
    if not isinstance(metric_policy, dict):
        return [], "healthy"
    breaches: List[Dict[str, Any]] = []
    decision = "healthy"
    for metric in required_metrics:
        if metric in missing_metrics:
            continue
        breach = _metric_breach(metric, metrics_payload, metric_policy)
        if breach is None:
            continue
        breaches.append(breach)
        if DECISION_RANK[breach["level"]] > DECISION_RANK[decision]:
            decision = breach["level"]
    return breaches, decision


def _metric_breach(
    metric: str,
    metrics_payload: Mapping[str, Any],
    metric_policy: Mapping[str, Any],
) -> Any:
    value = float(metrics_payload[metric])
    entry = metric_policy[metric]
    level = _metric_level(value, entry)
    if level == "healthy":
        return None
    return {
        "metric": metric,
        "value": value,
        "level": level,
        "threshold": float(entry[level]),
        "source": entry["source"],
    }


def _metric_level(value: float, entry: Mapping[str, Any]) -> str:
    direction = entry["direction"]
    levels: Sequence[str] = ("rollback", "hotfix", "watch")
    for level in levels:
        threshold = float(entry[level])
        if direction == "higher_is_worse" and value >= threshold:
            return level
        if direction == "lower_is_worse" and value <= threshold:
            return level
    return "healthy"


def _is_number(value: Any) -> bool:
    return not isinstance(value, bool) and isinstance(value, (int, float))


def report_markdown(result: Mapping[str, Any]) -> str:
    lines = [
        "# Release Health Decision",
        "",
        "- Decision: **%s**" % result["decision"].upper(),
        "- Flavor: `%s`" % result.get("flavor"),
        "- Package: `%s`" % result.get("package_name"),
        "- Version code: `%s`" % result.get("version_code"),
        "- Track: `%s`" % result.get("release_track"),
        "- Checkpoint: `+%sh`" % result.get("checkpoint_hours"),
        "- Acknowledgement window: `%s minutes`" % result.get("ack_minutes"),
        "",
        "## Action",
        "",
        str(result.get("action") or "No action configured."),
        "",
    ]
    errors = result.get("errors") or []
    if errors:
        lines.extend(["## Missing or invalid evidence", ""])
        lines.extend("- %s" % item for item in errors)
        lines.append("")
    breaches = result.get("breaches") or []
    if breaches:
        lines.extend(["## Threshold breaches", "", "| Metric | Value | Level | Threshold | Source |", "|---|---:|---|---:|---|"])
        for item in breaches:
            lines.append(
                "| `%s` | %s | %s | %s | %s |"
                % (item["metric"], item["value"], item["level"], item["threshold"], item["source"])
            )
        lines.append("")
    return "\n".join(lines) + "\n"
