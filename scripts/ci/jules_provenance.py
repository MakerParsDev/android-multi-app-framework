#!/usr/bin/env python3
"""Trusted Jules worker provenance helpers.

The pinned Jules Fleet runtime creates same-repository branches ending in a
numeric Jules session ID and adds an auto-generated task link to the PR body.
A worker PR is accepted only when those two independent metadata signals refer
to the same session ID.

Legacy s-* session markers remain supported only for the older jules/* branch
shape and still require the same ID to appear in trusted-looking body metadata.
"""

from __future__ import annotations

import re

NUMERIC_SESSION_ID = r"[0-9]{10,}"
LEGACY_SESSION_ID = r"s-[A-Za-z0-9][A-Za-z0-9._-]*"


def _numeric_branch_session_id(head_ref: str) -> str | None:
    match = re.search(rf"-({NUMERIC_SESSION_ID})$", head_ref)
    return match.group(1) if match else None


def _numeric_body_session_ids(body: str) -> set[str]:
    return set(
        re.findall(
            rf"https://jules\.google\.com/(?:task|session)/({NUMERIC_SESSION_ID})(?=$|[/?#\s)\]])",
            body,
        )
    )


def _legacy_branch_session_id(head_ref: str) -> str | None:
    if not head_ref.startswith("jules/"):
        return None

    last_segment = head_ref.rsplit("/", 1)[-1]
    if re.fullmatch(LEGACY_SESSION_ID, last_segment):
        return last_segment

    match = re.search(rf"-({LEGACY_SESSION_ID})$", head_ref)
    return match.group(1) if match else None


def _legacy_body_session_ids(body: str) -> set[str]:
    ids = set(
        re.findall(
            rf"https://jules\.google\.com/session/({LEGACY_SESSION_ID})(?=$|[/?#\s)\]])",
            body,
        )
    )
    ids.update(
        re.findall(
            rf"(?:source\s*[:=]\s*|source:\s*)jules:session:({LEGACY_SESSION_ID})(?=$|\s)",
            body,
            flags=re.IGNORECASE,
        )
    )
    return ids


def extract_verified_jules_session_id(head_ref: str, body: str) -> str | None:
    """Return the correlated Jules session ID or None.

    Current Fleet provenance requires:
    - branch name ending in -<10+ digit session id>, and
    - PR body containing a Jules task/session URL with that exact ID.

    The exact correlation is intentional: merely pasting a Jules URL or merely
    naming a branch with digits is insufficient.
    """

    numeric_id = _numeric_branch_session_id(head_ref)
    if numeric_id and numeric_id in _numeric_body_session_ids(body):
        return numeric_id

    legacy_id = _legacy_branch_session_id(head_ref)
    if legacy_id and legacy_id in _legacy_body_session_ids(body):
        return legacy_id

    return None


def has_verified_jules_session_provenance(head_ref: str, body: str) -> bool:
    return extract_verified_jules_session_id(head_ref, body) is not None
