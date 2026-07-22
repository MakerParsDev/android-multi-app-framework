#!/usr/bin/env python3
"""Validate security policy for active GitHub workflows and local actions."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator, Sequence

import yaml

from pinned_github_actions import validate_pinned_actions

FULL_SHA = re.compile(r"^[0-9a-f]{40}$")
TEMPLATE_EXPRESSION = re.compile(r"\$\{\{.*?\}\}", re.DOTALL)


@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    message: str


def _line_for(text: str, needle: str, default: int = 1) -> int:
    for number, line in enumerate(text.splitlines(), start=1):
        if needle in line:
            return number
    return default


def _workflow_paths(repo: Path) -> list[Path]:
    workflow_dir = repo / ".github/workflows"
    paths: list[Path] = []
    for pattern in ("*.yml", "*.yaml"):
        paths.extend(workflow_dir.glob(pattern))
    return sorted(path for path in paths if path.is_file())


def _local_action_paths(repo: Path) -> list[Path]:
    action_root = repo / ".github/actions"
    paths: list[Path] = []
    for pattern in ("*/action.yml", "*/action.yaml"):
        paths.extend(action_root.glob(pattern))
    return sorted(path for path in paths if path.is_file())


def _iter_steps(value: Any) -> Iterator[dict[str, Any]]:
    if isinstance(value, dict):
        if "uses" in value or "run" in value:
            yield value
        for child in value.values():
            yield from _iter_steps(child)
    elif isinstance(value, list):
        for child in value:
            yield from _iter_steps(child)


def _event_config(document: dict[str, Any]) -> Any:
    # PyYAML follows YAML 1.1 and may parse the key "on" as boolean True.
    if "on" in document:
        return document["on"]
    return document.get(True)


def _has_pull_request_target(event_config: Any) -> bool:
    if isinstance(event_config, str):
        return event_config == "pull_request_target"
    if isinstance(event_config, list):
        return "pull_request_target" in event_config
    if isinstance(event_config, dict):
        return "pull_request_target" in event_config
    return False


def _validate_external_uses(
    relative: Path,
    text: str,
    document: dict[str, Any],
) -> list[Finding]:
    findings: list[Finding] = []
    for step in _iter_steps(document):
        uses = step.get("uses")
        if not isinstance(uses, str) or uses.startswith("./"):
            continue
        if "@" not in uses:
            findings.append(
                Finding(relative, _line_for(text, uses), "external action must be pinned to a 40-character commit SHA")
            )
            continue
        action, reference = uses.rsplit("@", 1)
        if not action or not FULL_SHA.fullmatch(reference):
            findings.append(
                Finding(relative, _line_for(text, uses), "external action must be pinned to a 40-character commit SHA")
            )
        if action == "actions/checkout":
            with_config = step.get("with")
            persisted = with_config.get("persist-credentials") if isinstance(with_config, dict) else None
            if persisted is not False and str(persisted).lower() != "false":
                findings.append(
                    Finding(relative, _line_for(text, uses), "actions/checkout must set persist-credentials: false")
                )
    return findings


def _validate_run_blocks(relative: Path, text: str, document: dict[str, Any]) -> list[Finding]:
    findings: list[Finding] = []
    for step in _iter_steps(document):
        run = step.get("run")
        if not isinstance(run, str) or not TEMPLATE_EXPRESSION.search(run):
            continue
        line = 1
        for expression_line in run.splitlines():
            if "${{" in expression_line:
                line = _line_for(text, expression_line.strip())
                break
        findings.append(Finding(relative, line, "template expression inside run block"))
    return findings


def _validate_workflow(
    relative: Path,
    text: str,
    document: dict[str, Any],
) -> list[Finding]:
    findings: list[Finding] = []
    permissions = document.get("permissions")
    if permissions != {"contents": "read"}:
        findings.append(
            Finding(relative, _line_for(text, "permissions:"), "workflow permissions must be exactly contents: read")
        )

    if _has_pull_request_target(_event_config(document)):
        findings.append(
            Finding(relative, _line_for(text, "pull_request_target"), "pull_request_target is prohibited")
        )

    jobs = document.get("jobs")
    if not isinstance(jobs, dict):
        findings.append(Finding(relative, _line_for(text, "jobs:"), "workflow jobs must be a mapping"))
    else:
        for job_name, job in jobs.items():
            if not isinstance(job, dict) or "timeout-minutes" not in job:
                findings.append(
                    Finding(relative, _line_for(text, f"{job_name}:"), f"job {job_name!r} missing timeout-minutes")
                )
    return findings


def _load_document(path: Path, repo: Path) -> tuple[str, dict[str, Any] | None, list[Finding]]:
    relative = path.relative_to(repo)
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as error:
        return "", None, [Finding(relative, 1, f"unable to read YAML: {error}")]
    try:
        loaded = yaml.safe_load(text)
    except yaml.YAMLError as error:
        mark = getattr(error, "problem_mark", None)
        line = mark.line + 1 if mark is not None else 1
        return text, None, [Finding(relative, line, f"YAML parse error: {error}")]
    if not isinstance(loaded, dict):
        return text, None, [Finding(relative, 1, "YAML document must be a mapping")]
    return text, loaded, []


def validate_repository(repo: Path) -> list[Finding]:
    """Validate active workflows and local actions, ignoring workflows.disabled."""
    repo = repo.resolve()
    findings: list[Finding] = []
    workflow_paths = _workflow_paths(repo)
    local_action_paths = _local_action_paths(repo)

    for path in workflow_paths + local_action_paths:
        relative = path.relative_to(repo)
        text, document, load_findings = _load_document(path, repo)
        findings.extend(load_findings)
        if document is None:
            continue
        if path in workflow_paths:
            findings.extend(_validate_workflow(relative, text, document))
        findings.extend(_validate_external_uses(relative, text, document))
        findings.extend(_validate_run_blocks(relative, text, document))

    for pin_finding in validate_pinned_actions(repo):
        findings.append(Finding(pin_finding.path, pin_finding.line, pin_finding.message))

    return sorted(findings, key=lambda finding: (str(finding.path), finding.line, finding.message))


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    findings = validate_repository(args.repo)
    for finding in findings:
        print(f"{finding.path}:{finding.line}: {finding.message}", file=sys.stderr)
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
