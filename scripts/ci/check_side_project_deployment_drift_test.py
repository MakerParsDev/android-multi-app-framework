import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading
import unittest

from scripts.ci.check_side_project_deployment_drift import evaluate, fetch_json, parse_endpoints

SHA = "1234567890abcdef1234567890abcdef12345678"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/html-error":
            body = b"<html><body>upstream unavailable</body></html>"
            self.send_response(502)
            self.send_header("content-type", "text/html")
            self.end_headers()
            self.wfile.write(body)
            return
        payload = {
            "ok": True,
            "service": "contentapp-content-api",
            "environment": "test",
            "gitSha": SHA,
            "builtAt": "2026-07-21T00:00:00Z",
        }
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("content-type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_args):
        return


class DriftTest(unittest.TestCase):
    def setUp(self):
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def test_current_and_drift_results(self):
        config = {"url": f"http://127.0.0.1:{self.server.server_port}/health", "method": "GET"}
        self.assertEqual("current", evaluate("content-api", config, SHA, 2).status)
        self.assertEqual("drift", evaluate("content-api", config, "a" * 40, 2).status)

    def test_non_json_http_error_preserves_status(self):
        config = {
            "url": f"http://127.0.0.1:{self.server.server_port}/html-error",
            "method": "GET",
        }
        result = evaluate("content-api", config, SHA, 2)
        self.assertEqual("error", result.status)
        self.assertEqual(502, result.http_status)
        self.assertEqual("unexpected-http-status", result.error)

    def test_fetch_rejects_unsafe_scheme_when_parser_is_bypassed(self):
        unsafe_url = "file:" + "///tmp/metadata.json"
        with self.assertRaisesRegex(RuntimeError, "unsafe-endpoint-url"):
            fetch_json(unsafe_url, "GET", 2)

    def test_endpoint_parser_rejects_unknown_or_insecure_services(self):
        with self.assertRaisesRegex(ValueError, "unsupported"):
            parse_endpoints('{"unknown":"https://example.test"}')
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            parse_endpoints('{"content-api":"http://example.test"}')
        credential_url = "https://user:" + "pass@example.test/health"
        with self.assertRaisesRegex(ValueError, "credentials"):
            parse_endpoints(json.dumps({"content-api": credential_url}))


if __name__ == "__main__":
    unittest.main()
