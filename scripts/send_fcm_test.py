#!/usr/bin/env python3
"""
Send an FCM push notification using HTTP v1 API.

Targets:
  - single device token
  - FCM topic broadcast

Usage:
  python scripts/send_fcm_test.py --token "<FCM_TOKEN>" --title "Test" --body "Hello"
  python scripts/send_fcm_test.py --topic "dini-bildirim" --title "Cuma" --body "Hayirli cumalar"
  python scripts/send_fcm_test.py --all-apps --title "Cuma" --body "Hayirli cumalar"

Examples:
  # Data-only single-device test (recommended for reliable in-app persistence):
  #   python scripts/send_fcm_test.py --token "<FCM_TOKEN>" --title "Test" --body "Merhaba" --data type=test
  #
  # Notification+data topic broadcast:
  #   python scripts/send_fcm_test.py --topic "dini-bildirim" --use-notification-payload --title "Test" --body "Merhaba"
  #
  # All apps that subscribe to the shared default topic:
  #   python scripts/send_fcm_test.py --all-apps --title "Cuma" --body "Hayirli cumalar"

Credential resolution order:
  1) --service-account
  2) PLAY_SERVICE_ACCOUNT_JSON env
  3) GOOGLE_APPLICATION_CREDENTIALS env
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account


FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


def load_dotenv_if_present(dotenv_path: Path) -> None:
    """
    Minimal .env loader (no external dependency).
    - Supports KEY=VALUE lines
    - Ignores blank lines and # comments
    - Does NOT override existing environment variables
    """
    if not dotenv_path.exists():
        return
    for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if not key:
            continue
        os.environ.setdefault(key, value)


def resolve_service_account_path(cli_value: str | None) -> Path:
    raw = (
        cli_value
        or os.getenv("PLAY_SERVICE_ACCOUNT_JSON")
        or os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    )
    if not raw:
        raise SystemExit(
            "No service account path found. Use --service-account or set "
            "PLAY_SERVICE_ACCOUNT_JSON / GOOGLE_APPLICATION_CREDENTIALS."
        )
    path = Path(raw).expanduser()
    if not path.exists():
        raise SystemExit(f"Service account file not found: {path}")
    return path


def load_project_id(service_account_path: Path) -> str:
    data = json.loads(service_account_path.read_text(encoding="utf-8"))
    project_id = data.get("project_id")
    if not project_id:
        raise SystemExit(
            f"project_id missing in service account json: {service_account_path}")
    return project_id


def fetch_access_token(service_account_path: Path) -> str:
    creds = service_account.Credentials.from_service_account_file(
        str(service_account_path),
        scopes=[FCM_SCOPE],
    )
    creds.refresh(Request())
    if not creds.token:
        raise SystemExit("Failed to obtain OAuth access token.")
    return creds.token


def send_message(
    project_id: str,
    access_token: str,
    token: str | None,
    topic: str | None,
    title: str,
    body: str,
    data: dict[str, str],
    use_notification_payload: bool,
) -> dict:
    endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    message_data = {"title": title, "body": body, **data}
    target: dict[str, str]
    if token:
        target = {"token": token}
    elif topic:
        target = {"topic": topic}
    else:
        raise SystemExit("Either token or topic is required.")

    message: dict = {
        **target,
        "data": message_data,
        "android": {"priority": "HIGH"},
    }
    if use_notification_payload:
        # NOTE: Including notification payload means Android may show it without delivering to
        # FirebaseMessagingService.onMessageReceived when the app is background/terminated.
        # Use this only to test system-handled notifications, not in-app persistence.
        message["notification"] = {"title": title, "body": body}
        message["android"] = {
            "priority": "HIGH",
            "notification": {"channel_id": "app_notifications"},
        }

    payload = {
        "message": message
    }
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json; charset=utf-8",
    }
    response = requests.post(endpoint, headers=headers,
                             json=payload, timeout=20)
    try:
        body_json = response.json()
    except Exception:
        body_json = {"raw": response.text}

    if response.status_code >= 300:
        raise SystemExit(
            "FCM send failed:\n"
            f"HTTP {response.status_code}\n"
            f"{json.dumps(body_json, ensure_ascii=False, indent=2)}"
        )
    return body_json


def parse_data_values(raw_values: list[str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for pair in raw_values:
        if "=" not in pair:
            raise SystemExit(
                f"Invalid --data format (expected key=value): {pair}")
        key, value = pair.split("=", 1)
        key = key.strip()
        if not key:
            raise SystemExit(f"Invalid empty data key in: {pair}")
        out[key] = value
    return out


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Send FCM notification to one token or one topic.")
    target_group = parser.add_mutually_exclusive_group(required=True)
    target_group.add_argument("--token", help="Target FCM registration token")
    target_group.add_argument("--topic", help="Target FCM topic name")
    target_group.add_argument(
        "--all-apps",
        action="store_true",
        help="Shortcut for the shared default topic used by all apps (dini-bildirim)",
    )
    parser.add_argument("--title", default="Test Bildirim",
                        help="Notification title")
    parser.add_argument(
        "--body", default="Python test push gönderildi.", help="Notification body")
    parser.add_argument(
        "--service-account",
        default=None,
        help="Path to service account JSON (optional if env is set)",
    )
    parser.add_argument(
        "--data",
        action="append",
        default=[],
        help="Optional custom data key=value (repeatable)",
    )
    parser.add_argument(
        "--use-notification-payload",
        action="store_true",
        help="Also send notification payload (default is data-only for reliable in-app persistence)",
    )
    args = parser.parse_args()

    token = args.token.strip() if args.token else None
    topic = args.topic.strip() if args.topic else None
    if args.all_apps:
        topic = "dini-bildirim"

    if token is not None and not token:
        raise SystemExit("FCM token is empty.")
    if topic is not None and not topic:
        raise SystemExit("FCM topic is empty.")

    repo_root = Path(__file__).resolve().parent.parent
    load_dotenv_if_present(repo_root / ".env")

    service_account_path = resolve_service_account_path(args.service_account)
    project_id = load_project_id(service_account_path)
    access_token = fetch_access_token(service_account_path)
    data = parse_data_values(args.data)

    mode = "notification+data" if args.use_notification_payload else "data-only"
    if token:
        target_hint = f"token=...{token[-8:]}" if len(token) > 8 else f"token={token}"
    else:
        target_hint = f"topic={topic}"
    print(f"[send_fcm_test] project_id={project_id} mode={mode} {target_hint}")

    result = send_message(
        project_id=project_id,
        access_token=access_token,
        token=token,
        topic=topic,
        title=args.title,
        body=args.body,
        data=data,
        use_notification_payload=args.use_notification_payload,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
