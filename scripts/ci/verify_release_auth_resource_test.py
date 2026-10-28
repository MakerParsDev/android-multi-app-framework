"""Offline fail-closed contract tests of generated release Google Sign-In resources."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).with_name("verify_release_auth_resource.py")
spec = importlib.util.spec_from_file_location("verify_release_auth_resource", MODULE)
assert spec is not None and spec.loader is not None
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

GOOD = "1234567890-test-client.apps.googleusercontent.com"


def xml(value: str) -> str:
    return f'<resources><string name="web_client_id">{value}</string></resources>'


class ReleaseAuthResourceTest(unittest.TestCase):
    def check(self, payload: str | None, expected: str, should_pass: bool) -> None:
        with tempfile.TemporaryDirectory(prefix="release-auth-preflight-") as tmp:
            path = Path(tmp) / "gradleResValues.xml"
            if payload is not None:
                path.write_text(payload)
            if should_pass:
                module.verify_resource(path, expected)
            else:
                with self.assertRaises(ValueError):
                    module.verify_resource(path, expected)

    def test_accepted_release_resource(self) -> None:
        self.check(xml(GOOD), GOOD, True)

    def test_missing_resource(self) -> None:
        self.check(None, GOOD, False)

    def test_missing_client(self) -> None:
        self.check("<resources/>", GOOD, False)

    def test_blank_client(self) -> None:
        self.check(xml(""), GOOD, False)

    def test_malformed_client(self) -> None:
        self.check(xml("placeholder"), GOOD, False)

    def test_mismatch_rejected(self) -> None:
        self.check(xml("567890-other.apps.googleusercontent.com"), GOOD, False)

    def test_duplicate_resource_rejected(self) -> None:
        self.check(xml(GOOD).replace("</resources>", f'<string name="web_client_id">{GOOD}</string></resources>'), GOOD, False)

    def test_invalid_or_missing_authorized_config_rejected(self) -> None:
        for expected in ("", "foo", "123-invalid.example.com"):
            self.check(xml(GOOD), expected, False)

    def test_invalid_xml_rejected(self) -> None:
        self.check("<resources><string>", GOOD, False)


if __name__ == "__main__":
    unittest.main()
