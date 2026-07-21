#!/usr/bin/env python3
"""Fail when critical side-project endpoint security/response contracts drift."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def require(text: str, needles: tuple[str, ...], label: str) -> list[str]:
    return [f"{label}: missing {needle}" for needle in needles if needle not in text]


def validate() -> list[str]:
    paths = {
        "worker": ROOT / "side-projects/cloudflare/workers/admin-api/src/index.ts",
        "functions_index": ROOT / "side-projects/firebase/functions/src/index.ts",
        "functions_purchase": ROOT / "side-projects/firebase/functions/src/verifyPurchase.ts",
        "functions_push": ROOT / "side-projects/firebase/functions/src/sendTestNotification.ts",
        "functions_remote": ROOT / "side-projects/firebase/functions/src/adminRemoteConfig.ts",
        "content": ROOT / "side-projects/cloudflare/workers/content-api/src/index.ts",
        "ssv": ROOT / "side-projects/cloudflare/workers/ssv-callback/src/index.ts",
    }
    missing = [str(path.relative_to(ROOT)) for path in paths.values() if not path.is_file()]
    if missing:
        return [f"missing source: {item}" for item in missing]
    source = {name: path.read_text(encoding="utf-8") for name, path in paths.items()}
    source["worker"] += (
        ROOT / "side-projects/cloudflare/workers/admin-api/src/appCheck.ts"
    ).read_text(encoding="utf-8")
    errors: list[str] = []
    errors += require(source["worker"], ('"/verifyPurchase"', '"/registerDevice"', '"/health"', 'x-firebase-appcheck'), "admin worker")
    errors += require(source["functions_index"], ('verifyPurchase', 'sendTestNotification', 'adminGetRemoteConfig', 'adminUpdateRemoteConfig', 'healthCheck'), "functions exports")
    errors += require(source["functions_purchase"], ('Bearer ', 'x-firebase-appcheck', 'purchases.products.get', 'purchases.subscriptionsv2.get'), "purchase")
    for field in ("verified", "purchaseState", "acknowledgementState", "expiryTimeMillis", "autoRenewing"):
        if field not in source["worker"] or field not in source["functions_purchase"]:
            errors.append(f"purchase response drift: {field}")
    errors += require(source["functions_push"], ('authenticateAdminRequest', 'admin.messaging().send', 'installationId'), "FCM admin push")
    errors += require(source["functions_remote"], ('authenticateAdminRequest', 'If-Match', 'REMOTE_CONFIG_SCOPE'), "Remote Config")
    errors += require(source["content"], ('/api/recaptcha-verify', 'GOOGLE_RECAPTCHA_SECRET_KEY', 'isAllowedOrigin', '/health'), "content API")
    errors += require(source["ssv"], ('key_id', 'signature', 'transaction_id', 'SSV_DEDUP', '/health'), "SSV")
    for label in ("worker", "content", "ssv"):
        if "gitSha" not in source[label]:
            errors.append(f"{label}: health gitSha missing")
    return errors


if __name__ == "__main__":
    failures = validate()
    if failures:
        raise SystemExit("Side-project endpoint contract failed:\n" + "\n".join(failures))
    print("Side-project endpoint contracts passed")
