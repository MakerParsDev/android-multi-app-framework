#!/usr/bin/env python3
"""Parse and classify the subset of TOML used by the Gradle version catalog."""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Match, Optional, Tuple

SECTION_RE = re.compile(r"^\[([A-Za-z0-9_.-]+)]$")
ASSIGNMENT_RE = re.compile(r"^([A-Za-z0-9_.-]+)\s*=\s*(.+)$")
BASIC_QUOTED_RE = re.compile(r'^"((?:[^"\\]|\\.)*)"$')
LITERAL_QUOTED_RE = re.compile(r"^'([^']*)'$")
INLINE_FIELD_RE = re.compile(
    r'''([A-Za-z0-9_.-]+)\s*=\s*(?:"((?:[^"\\]|\\.)*)"|'([^']*)')'''
)
PRERELEASE_RE = re.compile(
    r"(?:^|[.\-])(?:alpha|beta|rc|cr|preview|eap|snapshot|milestone|m\d+)(?:[.\-]?\d*)?(?:$|[.\-])",
    re.IGNORECASE,
)


@dataclass
class CatalogParseState:
    sections: Dict[str, Dict[str, Any]]
    errors: List[str]


def strip_comment(line: str) -> str:
    in_double_quote = False
    in_single_quote = False
    escaped = False
    output: List[str] = []
    for character in line:
        if escaped:
            output.append(character)
            escaped = False
        elif character == "\\" and in_double_quote:
            output.append(character)
            escaped = True
        elif character == '"' and not in_single_quote:
            in_double_quote = not in_double_quote
            output.append(character)
        elif character == "'" and not in_double_quote:
            in_single_quote = not in_single_quote
            output.append(character)
        elif character == "#" and not in_double_quote and not in_single_quote:
            break
        else:
            output.append(character)
    return "".join(output).strip()


def decode_quoted(raw: str) -> Optional[str]:
    value = raw.strip()
    basic_match = BASIC_QUOTED_RE.fullmatch(value)
    if basic_match:
        return bytes(basic_match.group(1), "utf-8").decode("unicode_escape")
    literal_match = LITERAL_QUOTED_RE.fullmatch(value)
    return literal_match.group(1) if literal_match else None


def decoded_inline_value(match: Match[str]) -> str:
    basic_value = match.group(2)
    if basic_value is not None:
        return bytes(basic_value, "utf-8").decode("unicode_escape")
    return match.group(3)


def parse_inline_table(raw: str) -> Dict[str, str]:
    value = raw.strip()
    if not (value.startswith("{") and value.endswith("}")):
        return {}
    return {
        match.group(1): decoded_inline_value(match)
        for match in INLINE_FIELD_RE.finditer(value[1:-1])
    }


def store_assignment(
    state: CatalogParseState,
    section: str,
    key: str,
    raw_value: str,
    line_number: int,
) -> None:
    if key in state.sections[section]:
        state.errors.append(f"Duplicate {section} key '{key}' on line {line_number}")
        return
    if section == "versions":
        value = decode_quoted(raw_value)
        if value is None:
            state.errors.append(f"Version '{key}' must be a quoted string on line {line_number}")
            return
        state.sections[section][key] = value
        return
    fields = parse_inline_table(raw_value)
    if not fields:
        state.errors.append(
            f"{section[:-1].title()} '{key}' must use an inline table on line {line_number}"
        )
        return
    state.sections[section][key] = fields


def parse_catalog(path: Path) -> Tuple[Dict[str, Dict[str, Any]], List[str]]:
    state = CatalogParseState(
        sections={"versions": {}, "libraries": {}, "plugins": {}},
        errors=[],
    )
    section = ""
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = strip_comment(raw_line)
        section_match = SECTION_RE.fullmatch(line)
        if section_match:
            section = section_match.group(1)
            continue
        assignment = ASSIGNMENT_RE.fullmatch(line)
        if not assignment or section not in state.sections:
            continue
        key, raw_value = assignment.groups()
        store_assignment(state, section, key, raw_value, line_number)
    return state.sections, state.errors


def library_coordinate(fields: Dict[str, str]) -> str:
    module = fields.get("module", "").strip()
    if module:
        return module
    group = fields.get("group", "").strip()
    name = fields.get("name", "").strip()
    return f"{group}:{name}" if group and name else ""


def resolved_version(
    alias: str,
    fields: Dict[str, str],
    versions: Dict[str, str],
    errors: List[str],
) -> Tuple[Optional[str], str]:
    inline = fields.get("version")
    reference = fields.get("version.ref")
    if inline and reference:
        errors.append(f"Alias '{alias}' declares both version and version.ref")
        return inline, "invalid"
    if inline:
        return inline, "inline"
    if reference and reference not in versions:
        errors.append(f"Alias '{alias}' references missing version key '{reference}'")
        return None, f"ref:{reference}"
    if reference:
        return versions[reference], f"ref:{reference}"
    return None, "platform-managed"


def is_prerelease(version: str) -> bool:
    return bool(PRERELEASE_RE.search(version))


def is_dynamic_version(version: str) -> bool:
    normalized = version.strip()
    lowered = normalized.lower()
    checks = (
        "+" in normalized,
        lowered == "latest",
        lowered.startswith("latest."),
        normalized.startswith(("[", "(")),
        normalized.endswith(("]", ")")),
    )
    return any(checks)
