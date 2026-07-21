#!/usr/bin/env python3
"""Validate critical class/package coverage thresholds from a Kover XML report."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Tuple

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_REPORT = ROOT / "build/reports/kover/xml/coverage.xml"
DEFAULT_CONFIG = ROOT / "config/critical-coverage.json"
SUPPORTED_ENTITIES = {"class", "package"}
SUPPORTED_METRICS = {"LINE", "BRANCH"}


@dataclass(frozen=True)
class CounterValue:
    covered: int
    missed: int

    @property
    def total(self) -> int:
        return self.covered + self.missed

    @property
    def percentage(self) -> float:
        if self.total == 0:
            return 0.0
        return self.covered * 100.0 / self.total


@dataclass(frozen=True)
class CoverageTarget:
    name: str
    entity: str
    path: str
    minimum_line: Optional[float]
    minimum_branch: Optional[float]


@dataclass(frozen=True)
class CoverageResult:
    target: CoverageTarget
    counters: Mapping[str, CounterValue]


def normalize_path(value: str) -> str:
    return value.strip().replace(".", "/")


def parse_counter_nodes(nodes: Iterable[ET.Element]) -> Dict[str, CounterValue]:
    counters: Dict[str, CounterValue] = {}
    for node in nodes:
        metric = node.attrib.get("type", "").upper()
        if metric not in SUPPORTED_METRICS:
            continue
        try:
            covered = int(node.attrib["covered"])
            missed = int(node.attrib["missed"])
        except (KeyError, ValueError) as exc:
            raise ValueError("Malformed coverage counter for metric %s" % metric) from exc
        if covered < 0 or missed < 0:
            raise ValueError("Coverage counters cannot be negative for metric %s" % metric)
        counters[metric] = CounterValue(covered=covered, missed=missed)
    return counters


def parse_coverage_report(path: Path) -> Dict[Tuple[str, str], Dict[str, CounterValue]]:
    if not path.is_file():
        raise ValueError("Kover XML report is missing: %s" % path)
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise ValueError("Malformed Kover XML report %s: %s" % (path, exc)) from exc

    entities: Dict[Tuple[str, str], Dict[str, CounterValue]] = {}
    for package in root.findall(".//package"):
        package_name = normalize_path(package.attrib.get("name", ""))
        if package_name:
            entities[("package", package_name)] = parse_counter_nodes(package.findall("counter"))
        for class_node in package.findall("class"):
            class_name = normalize_path(class_node.attrib.get("name", ""))
            if class_name:
                entities[("class", class_name)] = parse_counter_nodes(class_node.findall("counter"))
    return entities


def parse_threshold(value: object, field: str, target_name: str) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("%s for %s must be numeric" % (field, target_name))
    numeric = float(value)
    if numeric < 0.0 or numeric > 100.0:
        raise ValueError("%s for %s must be between 0 and 100" % (field, target_name))
    return numeric


def load_config_document(path: Path) -> Mapping[str, object]:
    if not path.is_file():
        raise ValueError("Critical coverage config is missing: %s" % path)
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError("Malformed critical coverage config %s: %s" % (path, exc)) from exc
    if not isinstance(raw, dict):
        raise ValueError("Critical coverage config root must be an object")
    if raw.get("schema_version") != 1:
        raise ValueError("Unsupported critical coverage schema: %s" % raw.get("schema_version"))
    return raw


def parse_target(item: object, index: int) -> CoverageTarget:
    if not isinstance(item, dict):
        raise ValueError("Coverage target at index %d must be an object" % index)
    name = item.get("name")
    entity = item.get("entity")
    raw_path = item.get("path")
    if not isinstance(name, str) or not name.strip():
        raise ValueError("Coverage target at index %d requires a name" % index)
    if entity not in SUPPORTED_ENTITIES:
        raise ValueError("Coverage target %s has unsupported entity %s" % (name, entity))
    if not isinstance(raw_path, str) or not raw_path.strip():
        raise ValueError("Coverage target %s requires a path" % name)
    minimum_line = parse_threshold(item.get("minimum_line"), "minimum_line", name)
    minimum_branch = parse_threshold(item.get("minimum_branch"), "minimum_branch", name)
    if minimum_line is None and minimum_branch is None:
        raise ValueError("Coverage target %s requires at least one threshold" % name)
    return CoverageTarget(
        name=name.strip(),
        entity=entity,
        path=normalize_path(raw_path),
        minimum_line=minimum_line,
        minimum_branch=minimum_branch,
    )


def reject_duplicate_targets(targets: Iterable[CoverageTarget]) -> None:
    seen = set()
    for target in targets:
        key = (target.entity, target.path)
        if key in seen:
            raise ValueError("Duplicate coverage target: %s" % (key,))
        seen.add(key)


def load_targets(path: Path) -> List[CoverageTarget]:
    raw = load_config_document(path)
    raw_targets = raw.get("targets")
    if not isinstance(raw_targets, list) or not raw_targets:
        raise ValueError("Critical coverage config must contain a non-empty targets array")
    targets = [parse_target(item, index) for index, item in enumerate(raw_targets)]
    reject_duplicate_targets(targets)
    return targets


def evaluate_target(
    target: CoverageTarget,
    entities: Mapping[Tuple[str, str], Mapping[str, CounterValue]],
) -> Tuple[Optional[CoverageResult], List[str]]:
    counters = entities.get((target.entity, target.path))
    if counters is None:
        return None, [
            "Missing %s coverage target: %s (%s)" % (target.entity, target.name, target.path)
        ]
    result = CoverageResult(target=target, counters=counters)
    errors: List[str] = []
    for metric, minimum in (("LINE", target.minimum_line), ("BRANCH", target.minimum_branch)):
        if minimum is None:
            continue
        counter = counters.get(metric)
        if counter is None or counter.total == 0:
            errors.append("%s has no measurable %s coverage" % (target.name, metric.lower()))
        elif counter.percentage + 1e-9 < minimum:
            errors.append(
                "%s %s coverage %.2f%% is below %.2f%% (%d/%d)"
                % (
                    target.name,
                    metric.lower(),
                    counter.percentage,
                    minimum,
                    counter.covered,
                    counter.total,
                )
            )
    return result, errors


def evaluate_targets(
    targets: Iterable[CoverageTarget],
    entities: Mapping[Tuple[str, str], Mapping[str, CounterValue]],
) -> Tuple[List[CoverageResult], List[str]]:
    results: List[CoverageResult] = []
    errors: List[str] = []
    for target in targets:
        result, target_errors = evaluate_target(target, entities)
        if result is not None:
            results.append(result)
        errors.extend(target_errors)
    return results, errors


def format_result(result: CoverageResult) -> str:
    parts = []
    for metric in ("LINE", "BRANCH"):
        counter = result.counters.get(metric)
        if counter is not None and counter.total > 0:
            parts.append(
                "%s=%.2f%%(%d/%d)"
                % (metric.lower(), counter.percentage, counter.covered, counter.total)
            )
    return "  %s [%s %s]: %s" % (
        result.target.name,
        result.target.entity,
        result.target.path,
        ", ".join(parts) if parts else "no counters",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        targets = load_targets(args.config)
        entities = parse_coverage_report(args.report)
        results, errors = evaluate_targets(targets, entities)
    except ValueError as exc:
        print("Critical coverage validation failed:\n  - %s" % exc, file=sys.stderr)
        return 1

    print("Critical coverage targets:")
    for result in results:
        print(format_result(result))
    if errors:
        print("Critical coverage validation failed:", file=sys.stderr)
        for error in errors:
            print("  - %s" % error, file=sys.stderr)
        return 1
    print("Critical coverage validation passed: %d targets" % len(results))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
