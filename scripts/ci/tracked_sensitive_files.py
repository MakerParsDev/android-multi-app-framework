#!/usr/bin/env python3
"""Classify tracked paths that must never contain repository secret material."""

from __future__ import annotations

import fnmatch
from pathlib import PurePosixPath
from typing import Iterable, List

ALLOWED_ENV_TEMPLATES = (
    ".env.example",
    ".env.sample",
    ".env.template",
    "*.env.example",
    "*.env.sample",
    "*.env.template",
)
FORBIDDEN_BASENAMES = {
    "google-services.json",
    "local.properties",
    "keystore.properties",
    "firebase-credentials.json",
    "firebase-app-credentials.json",
    "play-service-account.json",
    "key.properties",
}
FORBIDDEN_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx")
FORBIDDEN_GLOBS = (
    "*service-account*.json",
    "*service_account*.json",
    "*credentials*.json",
    "*secret*.pem",
    "*private*.key",
    "keystore_base64.txt",
    "play_service_account_json_base64.txt",
    "firebase_configs_zip_base64.txt",
)


def is_allowed_env_template(path: str) -> bool:
    name = PurePosixPath(path).name.lower()
    return any(fnmatch.fnmatch(name, pattern) for pattern in ALLOWED_ENV_TEMPLATES)


def sensitive_reason(path: str) -> str:
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    name = PurePosixPath(normalized).name
    lowered = name.lower()
    if lowered == ".env" or lowered.startswith(".env."):
        if not is_allowed_env_template(normalized):
            return "runtime environment file"
    if lowered in FORBIDDEN_BASENAMES:
        return "forbidden credential/config filename"
    if lowered.endswith(FORBIDDEN_SUFFIXES):
        return "private signing/key container"
    if any(fnmatch.fnmatch(lowered, pattern) for pattern in FORBIDDEN_GLOBS):
        return "forbidden secret material filename"
    return ""


def find_sensitive_paths(paths: Iterable[str]) -> List[str]:
    findings = []
    for path in paths:
        reason = sensitive_reason(path)
        if reason:
            findings.append(f"{path}: {reason}")
    return sorted(set(findings))
