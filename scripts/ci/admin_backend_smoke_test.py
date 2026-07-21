#!/usr/bin/env python3
from __future__ import annotations

import json
import threading
import unittest
from contextlib import redirect_stdout
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import StringIO
from pathlib import Path
from types import SimpleNamespace
from urllib.parse import urlsplit

from admin_backend_smoke import SmokeFailure, normalized_origin, run_smoke as execute_smoke

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_SHA = "ef6bdf66499874269a26337f35c549a83cd19078"


class ContractHandler(BaseHTTPRequestHandler):
    health_payload: dict[str, object] = {}
    purchase_status = 401
    missing_app_check_status = 401
    invalid_app_check_status = 401

    def log_message(self, format: str, *args: object) -> None:
        return

    def _write_json(self, status: int, payload: dict[str, object]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._write_json(200, self.health_payload)
            return
        self._write_json(404, {"error": "not found"})

    def do_POST(self) -> None:
        content_length = int(self.headers.get("content-length", "0"))
        if content_length:
            self.rfile.read(content_length)
        if self.path == "/verifyPurchase":
            self._write_json(self.purchase_status, {"error": "secret-response-body"})
            return
        if self.path == "/registerDevice":
            status = (
                self.invalid_app_check_status
                if self.headers.get("x-firebase-appcheck")
                else self.missing_app_check_status
            )
            self._write_json(status, {"error": "secret-response-body"})
            return
        self._write_json(404, {"error": "not found"})


class OriginNormalizationTest(unittest.TestCase):
    def test_explicit_default_ports_match_implicit_default_ports(self) -> None:
        self.assertEqual(
            normalized_origin(urlsplit("https://example.test")),
            normalized_origin(urlsplit("https://example.test:443")),
        )
        self.assertEqual(
            normalized_origin(urlsplit("http://localhost")),
            normalized_origin(urlsplit("http://localhost:80")),
        )


class AdminBackendSmokeTest(unittest.TestCase):
    def setUp(self) -> None:
        ContractHandler.health_payload = {
            "ok": True,
            "service": "cloudflare-admin-api",
            "environment": "production",
            "gitSha": EXPECTED_SHA,
            "workerVersionId": "fa9b0f15-d196-4cbb-988e-07b64d055df5",
            "workerVersionTag": None,
            "workerVersionTimestamp": "2026-07-20T16:21:49.994Z",
        }
        ContractHandler.purchase_status = 401
        ContractHandler.missing_app_check_status = 401
        ContractHandler.invalid_app_check_status = 401
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), ContractHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    def run_smoke(self) -> SimpleNamespace:
        origin = f"http://127.0.0.1:{self.server.server_port}"
        args = SimpleNamespace(
            purchase_url=f"{origin}/verifyPurchase",
            push_url=f"{origin}/registerDevice",
            expected_git_sha=EXPECTED_SHA,
            allow_http_localhost=True,
            timeout=2.0,
        )
        stdout = StringIO()
        stderr = StringIO()
        returncode = 0
        with redirect_stdout(stdout):
            try:
                execute_smoke(args)
            except SmokeFailure as error:
                returncode = 1
                print(f"ADMIN_BACKEND_SMOKE=FAIL reason={error}", file=stderr)
        return SimpleNamespace(
            returncode=returncode,
            stdout=stdout.getvalue(),
            stderr=stderr.getvalue(),
        )

    def test_accepts_traceable_health_and_expected_security_rejections(self) -> None:
        result = self.run_smoke()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("HEALTH_HTTP=200", result.stdout)
        self.assertIn(f"HEALTH_GIT_SHA={EXPECTED_SHA}", result.stdout)
        self.assertIn("PURCHASE_UNAUTHENTICATED_HTTP=401", result.stdout)
        self.assertIn("REGISTER_MISSING_APPCHECK_HTTP=401", result.stdout)
        self.assertIn("REGISTER_INVALID_APPCHECK_HTTP=401", result.stdout)
        self.assertIn("ADMIN_BACKEND_SMOKE=PASS", result.stdout)
        self.assertNotIn("secret-response-body", result.stdout + result.stderr)

    def test_rejects_missing_traceability_metadata(self) -> None:
        ContractHandler.health_payload = {
            "ok": True,
            "service": "cloudflare-admin-api",
            "environment": "unknown",
            "gitSha": "unknown",
            "workerVersionId": None,
        }

        result = self.run_smoke()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("ADMIN_BACKEND_SMOKE=FAIL", result.stderr)

    def test_rejects_unexpected_source_commit(self) -> None:
        ContractHandler.health_payload["gitSha"] = "a" * 40

        result = self.run_smoke()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("git SHA", result.stderr)

    def test_rejects_unprotected_purchase_endpoint(self) -> None:
        ContractHandler.purchase_status = 200

        result = self.run_smoke()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("purchase", result.stderr.lower())

    def test_rejects_invalid_app_check_status_drift(self) -> None:
        ContractHandler.invalid_app_check_status = 500

        result = self.run_smoke()

        self.assertNotEqual(0, result.returncode)
        self.assertIn("App Check", result.stderr)


class ReleaseIntegrationContractTest(unittest.TestCase):
    def test_release_script_runs_backend_smoke_before_gradle(self) -> None:
        release_script = (ROOT / "scripts" / "azure" / "release.sh").read_text(
            encoding="utf-8"
        )
        smoke_call = "python3 ./scripts/ci/admin_backend_smoke.py"
        self.assertIn(smoke_call, release_script)
        self.assertLess(release_script.index(smoke_call), release_script.index("./gradlew"))
        self.assertIn("EXPECTED_ADMIN_BACKEND_GIT_SHA", release_script)


if __name__ == "__main__":
    unittest.main()
