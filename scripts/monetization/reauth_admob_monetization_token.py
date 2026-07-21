#!/usr/bin/env python3
"""
Re-authorize an AdMob OAuth token with monetization scope.

This script is intentionally separate from the reporting helpers because
some AdMob monetization APIs need an additional scope:

- https://www.googleapis.com/auth/admob.monetization

It launches a local browser OAuth flow and overwrites the target token file.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

try:
    from google_auth_oauthlib.flow import InstalledAppFlow
except ImportError as exc:  # pragma: no cover - runtime dependency check
    raise SystemExit(
        "Missing dependency: google-auth-oauthlib\n"
        "Install with:\n"
        "  pip install google-auth google-auth-oauthlib google-auth-httplib2 google-api-python-client"
    ) from exc


DEFAULT_CLIENT_SECRET = "SECRET/ADMOB_KOTNROL/client_secret.json"
DEFAULT_TOKEN_FILE = "SECRET/ADMOB_KOTNROL/token.json"
SCOPES = [
    "https://www.googleapis.com/auth/admob.readonly",
    "https://www.googleapis.com/auth/admob.report",
    "https://www.googleapis.com/auth/admob.monetization",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Re-authorize AdMob OAuth token with monetization scope"
    )
    parser.add_argument(
        "--client-secret",
        default=DEFAULT_CLIENT_SECRET,
        help=f"OAuth desktop client secret JSON (default: {DEFAULT_CLIENT_SECRET})",
    )
    parser.add_argument(
        "--token-file",
        default=DEFAULT_TOKEN_FILE,
        help=f"Where to write authorized token JSON (default: {DEFAULT_TOKEN_FILE})",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=0,
        help="Local callback server port. 0 lets Google choose a free port.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    client_secret = Path(args.client_secret)
    token_file = Path(args.token_file)

    if not client_secret.exists():
        raise SystemExit(f"Client secret file not found: {client_secret}")

    flow = InstalledAppFlow.from_client_secrets_file(str(client_secret), SCOPES)
    creds = flow.run_local_server(
        host="localhost",
        port=args.port,
        authorization_prompt_message=(
            "Tarayicida Google onay ekrani aciliyor. "
            "Bitince bu pencereye otomatik donecek."
        ),
        success_message="AdMob OAuth tamamlandi. Bu sekmeyi kapatabilirsiniz.",
        open_browser=True,
    )

    token_file.parent.mkdir(parents=True, exist_ok=True)
    token_file.write_text(creds.to_json(), encoding="utf-8")

    payload = json.loads(token_file.read_text(encoding="utf-8"))
    scopes = payload.get("scopes", [])
    print("OK: token written")
    print(f"token_file={token_file.resolve()}")
    print(f"scopes={scopes}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
