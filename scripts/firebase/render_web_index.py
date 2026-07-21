#!/usr/bin/env python3
"""Render firebase web index from template by injecting env-driven placeholders."""
from __future__ import annotations

import argparse
import os
from pathlib import Path

PLACEHOLDER_API_KEY = "__FIREBASE_WEB_API_KEY__"
PLACEHOLDER_RECAPTCHA_SITE_KEY = "__GOOGLE_RECAPTCHA_SITE_KEY__"
PLACEHOLDER_RECAPTCHA_VERIFY_ENDPOINT = "__RECAPTCHA_VERIFY_ENDPOINT__"


def main() -> int:
    parser = argparse.ArgumentParser(description="Render Firebase mobil_web index.html from template")
    parser.add_argument(
        "--template",
        default="side-projects/firebase/mobil_web/public/index.template.html",
        help="Template HTML path",
    )
    parser.add_argument(
        "--output",
        default="side-projects/firebase/mobil_web/public/index.html",
        help="Rendered output path",
    )
    parser.add_argument(
        "--api-key",
        default=os.getenv("FIREBASE_WEB_API_KEY", ""),
        help="Firebase web API key (defaults to FIREBASE_WEB_API_KEY env var)",
    )
    parser.add_argument(
        "--recaptcha-site-key",
        default=(
            os.getenv("GOOGLE_RECAPTCHA_SITE_KEY", "")
            or os.getenv("GOOGLE_reCAPTCHA_SITE_KEY", "")
        ),
        help="reCAPTCHA site key (defaults to GOOGLE_RECAPTCHA_SITE_KEY env var)",
    )
    parser.add_argument(
        "--recaptcha-verify-endpoint",
        default=os.getenv("RECAPTCHA_VERIFY_ENDPOINT", "/api/recaptcha/verify"),
        help="reCAPTCHA verify endpoint path or URL",
    )
    parser.add_argument(
        "--allow-placeholder",
        action="store_true",
        help="Allow writing placeholder when key is empty",
    )
    args = parser.parse_args()

    template_path = Path(args.template)
    output_path = Path(args.output)

    if not template_path.exists():
        raise SystemExit(f"Template not found: {template_path}")

    content = template_path.read_text(encoding="utf-8")

    for placeholder in (
        PLACEHOLDER_API_KEY,
        PLACEHOLDER_RECAPTCHA_SITE_KEY,
        PLACEHOLDER_RECAPTCHA_VERIFY_ENDPOINT,
    ):
        if placeholder not in content:
            raise SystemExit(f"Placeholder '{placeholder}' not found in template: {template_path}")

    api_key = (args.api_key or "").strip()
    if not api_key and not args.allow_placeholder:
        raise SystemExit(
            "FIREBASE_WEB_API_KEY is empty. Set env var or use --api-key. "
            "Use --allow-placeholder only when you intentionally keep analytics disabled."
        )

    recaptcha_site_key = (args.recaptcha_site_key or "").strip()
    verify_endpoint = (args.recaptcha_verify_endpoint or "").strip() or "/api/recaptcha/verify"

    rendered = (
        content
        .replace(PLACEHOLDER_API_KEY, api_key or PLACEHOLDER_API_KEY)
        .replace(PLACEHOLDER_RECAPTCHA_SITE_KEY, recaptcha_site_key or PLACEHOLDER_RECAPTCHA_SITE_KEY)
        .replace(PLACEHOLDER_RECAPTCHA_VERIFY_ENDPOINT, verify_endpoint)
    )
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(rendered, encoding="utf-8")
    print(f"Rendered {output_path} from {template_path}")
    if api_key:
        print("Firebase web API key injected.")
    else:
        print("No API key injected (placeholder kept).")

    if recaptcha_site_key:
        print("reCAPTCHA site key injected.")
    else:
        print("No reCAPTCHA site key injected (feature disabled).")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
