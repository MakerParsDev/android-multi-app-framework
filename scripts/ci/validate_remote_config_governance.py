#!/usr/bin/env python3
"""Validate and optionally regenerate the source-controlled Remote Config contract."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Mapping

from remote_config_governance import (
    build_report,
    build_template,
    load_app_records,
    load_json,
    render_markdown,
    validate_governance,
    write_json,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument(
        "--governance",
        type=Path,
        default=Path("config/remote-config/governance.json"),
    )
    parser.add_argument(
        "--template",
        type=Path,
        default=Path("config/remote-config/template.json"),
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=Path("config/remote-config/history/template-v3.json"),
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("docs/REMOTE_CONFIG_GOVERNANCE.md"),
    )
    parser.add_argument(
        "--impact-report",
        type=Path,
        default=Path("docs/REMOTE_CONFIG_DEPLOYMENT_IMPACT.md"),
    )
    parser.add_argument("--write", action="store_true")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/reports/remote-config/governance.json"),
    )
    return parser.parse_args()


def resolve(root: Path, path: Path) -> Path:
    return path if path.is_absolute() else root / path


def effective_value(
    template: Mapping[str, Any],
    parameter_name: str,
    condition_name: str | None = None,
) -> str | None:
    parameter = template.get("parameters", {}).get(parameter_name)
    if not isinstance(parameter, Mapping):
        return None
    if condition_name is not None:
        conditional_values = parameter.get("conditionalValues", {})
        if isinstance(conditional_values, Mapping):
            conditional = conditional_values.get(condition_name)
            if isinstance(conditional, Mapping) and "value" in conditional:
                return str(conditional["value"])
    default_value = parameter.get("defaultValue", {})
    if isinstance(default_value, Mapping) and "value" in default_value:
        return str(default_value["value"])
    return None


def validate_baseline_compatibility(
    baseline: Mapping[str, Any],
    candidate: Mapping[str, Any],
    apps: list[Any],
) -> tuple[list[str], dict[str, Any]]:
    errors: list[str] = []
    baseline_parameters = baseline.get("parameters", {})
    candidate_parameters = candidate.get("parameters", {})
    baseline_keys = set(baseline_parameters)
    candidate_keys = set(candidate_parameters)
    removed = sorted(baseline_keys - candidate_keys)
    added = sorted(candidate_keys - baseline_keys)
    if removed:
        errors.append("Candidate removes live parameters: " + ", ".join(removed))

    global_default_changes: list[dict[str, str | None]] = []
    per_app_changes: list[dict[str, str | None]] = []
    for key in sorted(baseline_keys & candidate_keys):
        before = effective_value(baseline, key)
        after_default = effective_value(candidate, key)
        if before != after_default:
            global_default_changes.append(
                {"parameter": key, "before": before, "after": after_default}
            )
        for app in apps:
            before_for_app = effective_value(baseline, key, app.condition_name)
            after_for_app = effective_value(candidate, key, app.condition_name)
            if before_for_app != after_for_app:
                change = {
                    "flavor": app.flavor,
                    "parameter": key,
                    "before": before_for_app,
                    "after": after_for_app,
                }
                per_app_changes.append(change)
                if key != "latest_version_code":
                    errors.append(
                        f"Unexpected existing-key behavior change for {app.flavor}:{key}: "
                        f"{before_for_app!r} -> {after_for_app!r}"
                    )

    impact = {
        "baseline_version": str(baseline.get("version", {}).get("versionNumber", "unknown")),
        "baseline_parameter_count": len(baseline_keys),
        "candidate_parameter_count": len(candidate_keys),
        "candidate_condition_count": len(candidate.get("conditions", [])),
        "added_parameters": added,
        "removed_parameters": removed,
        "global_default_changes": global_default_changes,
        "per_app_existing_parameter_changes": per_app_changes,
    }
    return errors, impact


def render_deployment_impact(impact: Mapping[str, Any]) -> str:
    added = impact["added_parameters"]
    removed = impact["removed_parameters"]
    global_changes = impact["global_default_changes"]
    per_app_changes = impact["per_app_existing_parameter_changes"]
    has_changes = bool(added or removed or global_changes or per_app_changes)

    lines = [
        "# Remote Config Deployment Impact",
        "",
        "This file is generated by `scripts/ci/validate_remote_config_governance.py`.",
        "",
        "## Summary",
        "",
        f"- Baseline Firebase template version: **{impact['baseline_version']}**",
        f"- Baseline parameters: **{impact['baseline_parameter_count']}**",
        f"- Candidate parameters: **{impact['candidate_parameter_count']}**",
        f"- Candidate app conditions: **{impact['candidate_condition_count']}**",
        f"- Added parameters: **{len(added)}**",
        f"- Removed parameters: **{len(removed)}**",
        f"- Global fallback changes: **{len(global_changes)}**",
        f"- Existing-key changes for matched apps: **{len(per_app_changes)}**",
        "",
    ]
    if has_changes:
        lines.append(
            "Any existing-key change other than `latest_version_code` is blocked. "
            "Update mode and minimum-supported-version changes therefore require an explicit governance change."
        )
    else:
        lines.append(
            "The generated candidate is behaviorally identical to the audited live template for all 17 matched apps and the unmatched-app fallback."
        )

    lines.extend(["", "## Added parameters", ""])
    if added:
        lines.extend(f"- `{parameter}`" for parameter in added)
    else:
        lines.append("None.")

    lines.extend(["", "## Removed parameters", ""])
    if removed:
        lines.extend(f"- `{parameter}`" for parameter in removed)
    else:
        lines.append("None.")

    lines.extend(["", "## Global fallback changes", ""])
    if global_changes:
        lines.extend(
            [
                "These values apply only when a Firebase app does not match one of the 17 audited `app.id` conditions.",
                "",
                "| Parameter | Before | After |",
                "|---|---|---|",
            ]
        )
        for change in global_changes:
            lines.append("| `{parameter}` | `{before}` | `{after}` |".format(**change))
    else:
        lines.append("None.")

    lines.extend(["", "## Existing-key changes for matched apps", ""])
    if per_app_changes:
        lines.extend(
            [
                "| Flavor | Parameter | Before | After |",
                "|---|---|---:|---:|",
            ]
        )
        for change in per_app_changes:
            lines.append("| {flavor} | `{parameter}` | {before} | {after} |".format(**change))
    else:
        lines.append("None.")

    lines.extend(
        [
            "",
            "## Deployment controls",
            "",
            "- Firebase REST `validateOnly=true` must succeed before publication.",
            "- Publish with the current ETag; never use a forced overwrite for the normal path.",
            "- Record the new Firebase template version after publication.",
            "- Roll back to the audited baseline/current previous version if condition matching or runtime behavior is incorrect.",
            "- Global and per-app update/ad emergency switches remain available independently.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    governance_path = resolve(root, args.governance)
    template_path = resolve(root, args.template)
    baseline_path = resolve(root, args.baseline)
    report_path = resolve(root, args.report)
    impact_report_path = resolve(root, args.impact_report)
    output_path = resolve(root, args.output)

    governance = load_json(governance_path)
    apps = load_app_records(root)
    generated_template = build_template(governance, apps)
    generated_report = build_report(governance, apps)
    generated_markdown = render_markdown(generated_report)

    errors: list[str] = []
    if baseline_path.exists():
        baseline_template: Mapping[str, Any] = load_json(baseline_path)
    else:
        baseline_template = {}
        errors.append(f"Missing audited live-template baseline: {baseline_path.relative_to(root)}")
    baseline_errors, deployment_impact = validate_baseline_compatibility(
        baseline_template,
        generated_template,
        apps,
    )
    errors.extend(baseline_errors)
    generated_impact_markdown = render_deployment_impact(deployment_impact)
    generated_report["deployment_impact"] = deployment_impact

    if args.write:
        write_json(template_path, generated_template)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(generated_markdown, encoding="utf-8")
        impact_report_path.parent.mkdir(parents=True, exist_ok=True)
        impact_report_path.write_text(generated_impact_markdown, encoding="utf-8")

    if not template_path.exists():
        errors.append(f"Missing generated template: {template_path.relative_to(root)}")
        committed_template: Mapping[str, Any] = {}
    else:
        committed_template = load_json(template_path)
    if not report_path.exists():
        errors.append(f"Missing generated report: {report_path.relative_to(root)}")
    elif report_path.read_text(encoding="utf-8") != generated_markdown:
        errors.append(
            "Remote Config governance report is stale; run "
            "validate_remote_config_governance.py --write"
        )
    if not impact_report_path.exists():
        errors.append(f"Missing deployment impact report: {impact_report_path.relative_to(root)}")
    elif impact_report_path.read_text(encoding="utf-8") != generated_impact_markdown:
        errors.append(
            "Remote Config deployment impact report is stale; run "
            "validate_remote_config_governance.py --write"
        )

    errors.extend(validate_governance(root, governance, apps, committed_template))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(generated_report, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    if errors:
        print("Remote Config governance validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    print(
        "Remote Config governance validation passed: "
        f"{generated_report['condition_count']} app conditions, "
        f"{generated_report['parameter_count']} parameters, "
        f"{generated_report['conditional_value_count']} conditional values, "
        f"{len(deployment_impact['added_parameters'])} added and "
        f"{len(deployment_impact['removed_parameters'])} removed live parameters"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
