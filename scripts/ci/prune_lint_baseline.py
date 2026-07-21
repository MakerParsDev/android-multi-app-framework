#!/usr/bin/env python3
"""
Prune stale entries from Android lint baseline XML without reformatting the file.

This script compares baseline issues against one or more lint report XML files and
removes only baseline entries that no longer appear in the reports (stale entries).
It preserves the original baseline formatting by deleting whole <issue>...</issue>
blocks directly from the original text.
"""

from __future__ import annotations

import argparse
import glob
import os
import pathlib
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from typing import Iterable


ABS_WIN_RE = re.compile(r"^[A-Za-z]:[\\/]")


@dataclass(frozen=True)
class IssueFingerprint:
    issue_id: str
    message: str
    location_file: str
    location_line: str
    location_column: str


@dataclass
class IssueRecord:
    fingerprint: IssueFingerprint
    issue_id: str
    message: str
    location_file: str
    location_line: str
    location_column: str
    source_report: str | None = None
    block_start: int | None = None
    block_end: int | None = None


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Prune stale Android lint baseline entries")
    p.add_argument(
        "--baseline",
        default="app/lint-baseline.xml",
        help="Path to lint baseline XML (default: app/lint-baseline.xml)",
    )
    p.add_argument(
        "--reports-glob",
        default="app/build/reports/lint-results-*Debug.xml",
        help="Glob for lint report XML files (default: app/build/reports/lint-results-*Debug.xml)",
    )
    p.add_argument(
        "--candidate-baseline",
        help="Optional candidate baseline XML to compare against instead of lint reports.",
    )
    p.add_argument(
        "--module-dir",
        default="app",
        help="Module directory used to normalize absolute lint report paths (default: app)",
    )
    p.add_argument(
        "--mode",
        choices=("check", "write"),
        default="check",
        help="check = report stale entries only, write = prune baseline",
    )
    p.add_argument(
        "--output",
        help="Optional output path (write mode). Defaults to overwriting --baseline.",
    )
    p.add_argument(
        "--fail-on-stale",
        action="store_true",
        help="Exit non-zero in check mode if stale entries are found.",
    )
    p.add_argument(
        "--backup",
        dest="backup",
        action="store_true",
        default=True,
        help="Create .bak backup when writing in place (default: enabled).",
    )
    p.add_argument(
        "--no-backup",
        dest="backup",
        action="store_false",
        help="Disable backup creation in write mode.",
    )
    p.add_argument(
        "--verbose",
        action="store_true",
        help="Print sample stale entries.",
    )
    return p.parse_args()


def looks_absolute(path_str: str) -> bool:
    return path_str.startswith("/") or path_str.startswith("\\\\") or bool(ABS_WIN_RE.match(path_str))


def normalize_path(path_str: str | None, module_dir: pathlib.Path) -> str:
    raw = (path_str or "").strip()
    if not raw:
        return ""
    if looks_absolute(raw):
        try:
            rel = os.path.relpath(raw, str(module_dir))
            return rel.replace("\\", "/")
        except Exception:
            return raw.replace("\\", "/")
    return raw.replace("\\", "/")


def extract_issue_record(issue: ET.Element, module_dir: pathlib.Path, source_report: str | None = None) -> IssueRecord:
    issue_id = issue.attrib.get("id", "")
    message = issue.attrib.get("message", "")

    location = issue.find("location")
    if location is None:
        location = issue.find(".//location")

    file_attr = normalize_path(location.attrib.get("file") if location is not None else "", module_dir)
    line_attr = (location.attrib.get("line") if location is not None else "") or ""
    col_attr = (location.attrib.get("column") if location is not None else "") or ""

    fp = IssueFingerprint(
        issue_id=issue_id,
        message=message,
        location_file=file_attr,
        location_line=line_attr,
        location_column=col_attr,
    )
    return IssueRecord(
        fingerprint=fp,
        issue_id=issue_id,
        message=message,
        location_file=file_attr,
        location_line=line_attr,
        location_column=col_attr,
        source_report=source_report,
    )


def load_report_records(report_paths: Iterable[pathlib.Path], module_dir: pathlib.Path) -> list[IssueRecord]:
    records: list[IssueRecord] = []
    ignored_ids = {"LintBaseline", "LintBaselineFixed"}
    for report_path in report_paths:
        tree = ET.parse(report_path)
        root = tree.getroot()
        for issue in root.findall(".//issue"):
            issue_id = issue.attrib.get("id", "")
            if issue_id in ignored_ids:
                continue
            records.append(extract_issue_record(issue, module_dir, source_report=str(report_path)))
    return records


def load_baseline_issue_blocks(baseline_text: str, module_dir: pathlib.Path) -> list[IssueRecord]:
    records: list[IssueRecord] = []
    cursor = 0
    while True:
        issue_start = baseline_text.find("<issue", cursor)
        if issue_start == -1:
            break

        # Skip the root <issues ...> tag.
        next_char_index = issue_start + len("<issue")
        next_char = baseline_text[next_char_index : next_char_index + 1]
        if next_char and (next_char.isalnum() or next_char == "_"):
            cursor = issue_start + 1
            continue

        line_start = baseline_text.rfind("\n", 0, issue_start)
        block_start = 0 if line_start == -1 else line_start + 1
        if any(ch not in " \t\r" for ch in baseline_text[block_start:issue_start]):
            block_start = issue_start

        start_tag_end = baseline_text.find(">", issue_start)
        if start_tag_end == -1:
            raise SystemExit(f"Malformed baseline XML: unterminated <issue tag near offset {issue_start}")

        start_tag = baseline_text[issue_start : start_tag_end + 1]
        if start_tag.rstrip().endswith("/>"):
            block_end = start_tag_end + 1
        else:
            close_start = baseline_text.find("</issue>", start_tag_end + 1)
            if close_start == -1:
                raise SystemExit(f"Malformed baseline XML: missing </issue> near offset {issue_start}")
            block_end = close_start + len("</issue>")

        # Include the issue line ending and a following blank separator line if present,
        # but do not consume the indentation of the next <issue> block.
        while block_end < len(baseline_text) and baseline_text[block_end] in " \t":
            block_end += 1

        def _consume_newline(pos: int) -> int:
            if baseline_text.startswith("\r\n", pos):
                return pos + 2
            if pos < len(baseline_text) and baseline_text[pos] in "\r\n":
                return pos + 1
            return pos

        after_line = _consume_newline(block_end)
        if after_line != block_end:
            probe = after_line
            while probe < len(baseline_text) and baseline_text[probe] in " \t":
                probe += 1
            after_blank = _consume_newline(probe)
            block_end = after_blank if after_blank != probe else after_line

        block = baseline_text[block_start:block_end]
        try:
            issue = ET.fromstring(block)
        except ET.ParseError as exc:
            raise SystemExit(f"Failed to parse <issue> block in baseline near offset {issue_start}: {exc}")
        record = extract_issue_record(issue, module_dir)
        record.block_start = block_start
        record.block_end = block_end
        records.append(record)
        cursor = block_end
    return records


def summarize_records(records: list[IssueRecord]) -> tuple[Counter[str], list[str]]:
    issue_counts: Counter[str] = Counter()
    sample_paths: list[str] = []
    seen_paths: set[str] = set()
    for rec in records:
        issue_counts[rec.issue_id] += 1
        if rec.location_file and rec.location_file not in seen_paths and len(sample_paths) < 10:
            sample_paths.append(rec.location_file)
            seen_paths.add(rec.location_file)
    return issue_counts, sample_paths


def print_summary(
    *,
    baseline_records: list[IssueRecord],
    report_records: list[IssueRecord],
    stale_records: list[IssueRecord],
    report_paths: list[pathlib.Path],
    verbose: bool,
) -> None:
    union_fingerprints = {r.fingerprint for r in report_records}
    stale_counts, sample_paths = summarize_records(stale_records)

    print("Lint baseline prune summary")
    print(f"- reports_scanned: {len(report_paths)}")
    print(f"- baseline_issue_count: {len(baseline_records)}")
    print(f"- report_issue_count_total: {len(report_records)}")
    print(f"- report_issue_count_union: {len(union_fingerprints)}")
    print(f"- stale_issue_count: {len(stale_records)}")

    if stale_counts:
        print("- stale_issue_types:")
        for issue_id, count in stale_counts.most_common():
            print(f"  - {issue_id}: {count}")
    else:
        print("- stale_issue_types: none")

    if sample_paths:
        print("- stale_sample_paths:")
        for path in sample_paths:
            print(f"  - {path}")

    if verbose and stale_records:
        print("- stale_samples_verbose (max 20):")
        for rec in stale_records[:20]:
            loc = rec.location_file or "<no-location>"
            line_col = ""
            if rec.location_line:
                line_col = f":{rec.location_line}"
                if rec.location_column:
                    line_col += f":{rec.location_column}"
            print(f"  - [{rec.issue_id}] {loc}{line_col}")
            print(f"    message={rec.message}")


def prune_baseline_text(baseline_text: str, stale_records: list[IssueRecord]) -> str:
    stale_spans = sorted(
        (
            (rec.block_start, rec.block_end)
            for rec in stale_records
            if rec.block_start is not None and rec.block_end is not None
        ),
        key=lambda x: x[0],
    )
    if not stale_spans:
        return baseline_text

    parts: list[str] = []
    cursor = 0
    for start, end in stale_spans:
        if start < cursor:
            # Overlap should not happen, but guard against malformed matching.
            continue
        parts.append(baseline_text[cursor:start])
        cursor = end
    parts.append(baseline_text[cursor:])
    return "".join(parts)


def main() -> int:
    args = parse_args()

    baseline_path = pathlib.Path(args.baseline)
    if not baseline_path.exists():
        print(f"ERROR: baseline file not found: {baseline_path}", file=sys.stderr)
        return 2

    module_dir = pathlib.Path(args.module_dir).resolve()
    if not module_dir.exists():
        print(f"ERROR: module dir not found: {module_dir}", file=sys.stderr)
        return 2

    baseline_text = baseline_path.read_text(encoding="utf-8")
    baseline_records = load_baseline_issue_blocks(baseline_text, module_dir)
    report_paths: list[pathlib.Path]
    if args.candidate_baseline:
        candidate_path = pathlib.Path(args.candidate_baseline)
        if not candidate_path.exists():
            print(f"ERROR: candidate baseline file not found: {candidate_path}", file=sys.stderr)
            return 2
        candidate_text = candidate_path.read_text(encoding="utf-8")
        report_records = load_baseline_issue_blocks(candidate_text, module_dir)
        report_paths = [candidate_path]
        print(f"- comparison_mode: candidate-baseline ({candidate_path})")
    else:
        report_paths = sorted(pathlib.Path(p) for p in glob.glob(args.reports_glob))
        if not report_paths:
            print(f"ERROR: no lint reports matched: {args.reports_glob}", file=sys.stderr)
            return 2
        report_records = load_report_records(report_paths, module_dir)

    active_fingerprints = {rec.fingerprint for rec in report_records}
    stale_records = [rec for rec in baseline_records if rec.fingerprint not in active_fingerprints]

    print_summary(
        baseline_records=baseline_records,
        report_records=report_records,
        stale_records=stale_records,
        report_paths=report_paths,
        verbose=args.verbose,
    )

    if args.mode == "check":
        if stale_records and args.fail_on_stale:
            return 1
        return 0

    output_path = pathlib.Path(args.output) if args.output else baseline_path
    if output_path.resolve() == baseline_path.resolve() and args.backup:
        backup_path = baseline_path.with_suffix(baseline_path.suffix + ".bak")
        shutil.copy2(baseline_path, backup_path)
        print(f"- backup_created: {backup_path}")

    pruned_text = prune_baseline_text(baseline_text, stale_records)
    output_path.write_text(pruned_text, encoding="utf-8", newline="")
    print(f"- wrote_baseline: {output_path}")
    print(f"- removed_issue_blocks: {len(stale_records)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
