#!/usr/bin/env python3
"""Block raw privacy-sensitive values in production Timber/Crashlytics log calls."""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

CALL_MARKERS = (
    "Timber.",
    "crashlytics.log(",
    "crashlytics.recordException(",
    "FirebaseCrashlytics.getInstance().log(",
    "FirebaseCrashlytics.getInstance().recordException(",
)

FORBIDDEN_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    (
        "sensitive_identifier",
        re.compile(
            r"\b(?:installationId|fcmToken|purchaseToken|idToken|remoteUrl|filePath|latitude|longitude)\b"
        ),
    ),
    ("absolute_file_path", re.compile(r"\.(?:absolutePath|canonicalPath)\b")),
    (
        "google_credential_identity",
        re.compile(r"\bgoogleIdTokenCredential\.(?:id|displayName)\b"),
    ),
    (
        "full_request_url",
        re.compile(r"\brequest\.url\b|\burl\.toString\s*\("),
    ),
)


@dataclass(frozen=True)
class Finding:
    path: str
    line: int
    rule: str
    excerpt: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/reports/security/privacy-safe-logging.json"),
    )
    return parser.parse_args()


def production_kotlin_files(root: Path) -> list[Path]:
    files: set[Path] = set()
    for module_root in (root / "app", root / "core", root / "feature"):
        if not module_root.exists():
            continue
        for source_set in ("main", "release"):
            files.update(module_root.glob(f"**/src/{source_set}/**/*.kt"))
    return sorted(path for path in files if "build" not in path.parts)


def code_character_mask(text: str) -> list[bool]:
    """Return a mask for characters that are Kotlin code, not literals/comments."""
    mask = [False] * len(text)
    index = 0
    block_comment_depth = 0
    in_string = False
    in_char = False
    in_triple_string = False
    escaped = False

    while index < len(text):
        if block_comment_depth > 0:
            if text.startswith("/*", index):
                block_comment_depth += 1
                index += 2
            elif text.startswith("*/", index):
                block_comment_depth -= 1
                index += 2
            else:
                index += 1
            continue

        if in_triple_string:
            if text.startswith('"""', index):
                in_triple_string = False
                index += 3
            else:
                index += 1
            continue

        if in_string:
            if escaped:
                escaped = False
            elif text[index] == "\\":
                escaped = True
            elif text[index] == '"':
                in_string = False
            index += 1
            continue

        if in_char:
            if escaped:
                escaped = False
            elif text[index] == "\\":
                escaped = True
            elif text[index] == "'":
                in_char = False
            index += 1
            continue

        if text.startswith("//", index):
            newline = text.find("\n", index + 2)
            index = len(text) if newline < 0 else newline + 1
            continue
        if text.startswith("/*", index):
            block_comment_depth = 1
            index += 2
            continue
        if text.startswith('"""', index):
            in_triple_string = True
            index += 3
            continue
        if text[index] == '"':
            in_string = True
            index += 1
            continue
        if text[index] == "'":
            in_char = True
            index += 1
            continue

        mask[index] = True
        index += 1

    return mask


def find_code_token(text: str, token: str, start: int = 0) -> int:
    mask = code_character_mask(text)
    index = text.find(token, start)
    while index >= 0:
        if mask[index]:
            return index
        index = text.find(token, index + 1)
    return -1


def active_logging_marker_index(line: str) -> int:
    indexes = [find_code_token(line, marker) for marker in CALL_MARKERS]
    active = [index for index in indexes if index >= 0]
    return min(active, default=-1)


def parenthesis_balance(text: str) -> int:
    mask = code_character_mask(text)
    return sum(
        1 if character == "(" else -1
        for index, character in enumerate(text)
        if mask[index] and character in "()"
    )


def matching_parenthesis_end(text: str, open_paren: int) -> int:
    mask = code_character_mask(text)
    depth = 0
    for index in range(open_paren, len(text)):
        if not mask[index]:
            continue
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index + 1
    return len(text)


def extract_logging_calls(text: str) -> list[tuple[int, str]]:
    lines = text.splitlines()
    calls: list[tuple[int, str]] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        marker_index = active_logging_marker_index(line)
        if marker_index < 0:
            index += 1
            continue

        start = index
        collected = [line[marker_index:]]
        while parenthesis_balance("\n".join(collected)) > 0 and index + 1 < len(lines):
            index += 1
            collected.append(lines[index])
        calls.append((start + 1, "\n".join(collected)))
        index += 1
    return calls


def strip_sanitized_expressions(call: str) -> str:
    marker = "sanitizeLogMessage"
    cleaned: list[str] = []
    index = 0
    while index < len(call):
        marker_index = find_code_token(call, marker, index)
        if marker_index < 0:
            cleaned.append(call[index:])
            break
        cleaned.append(call[index:marker_index])
        open_paren = marker_index + len(marker)
        while open_paren < len(call) and call[open_paren].isspace():
            open_paren += 1
        mask = code_character_mask(call)
        if open_paren >= len(call) or call[open_paren] != "(" or not mask[open_paren]:
            cleaned.append(marker)
            index = marker_index + len(marker)
            continue
        cleaned.append("[SANITIZED_EXPRESSION]")
        index = matching_parenthesis_end(call, open_paren)
    return "".join(cleaned)


def scan_file(root: Path, path: Path) -> list[Finding]:
    findings: list[Finding] = []
    text = path.read_text(encoding="utf-8")
    for line, call in extract_logging_calls(text):
        if "recordException(" in call and not (
            "toPrivacySafeThrowable()" in call or "runtimeSignalException(" in call
        ):
            findings.append(
                Finding(
                    path=path.relative_to(root).as_posix(),
                    line=line,
                    rule="raw_crashlytics_exception",
                    excerpt=" ".join(part.strip() for part in call.splitlines())[:300],
                )
            )
        cleaned_call = strip_sanitized_expressions(call)
        for rule, pattern in FORBIDDEN_PATTERNS:
            if pattern.search(cleaned_call):
                findings.append(
                    Finding(
                        path=path.relative_to(root).as_posix(),
                        line=line,
                        rule=rule,
                        excerpt=" ".join(part.strip() for part in call.splitlines())[:300],
                    )
                )
    return findings


def scan_repository(root: Path) -> tuple[list[Path], list[Finding]]:
    files = production_kotlin_files(root)
    findings = [finding for path in files for finding in scan_file(root, path)]
    return files, findings


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    files, findings = scan_repository(root)
    output = args.output if args.output.is_absolute() else root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "files_scanned": len(files),
                "findings": [asdict(finding) for finding in findings],
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )

    if findings:
        print("Privacy-safe logging validation failed:", file=sys.stderr)
        for finding in findings:
            print(
                f"  - {finding.path}:{finding.line} [{finding.rule}] {finding.excerpt}",
                file=sys.stderr,
            )
        return 1

    print(f"Privacy-safe logging validation passed: {len(files)} production Kotlin files scanned")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
