from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from validate_privacy_safe_logging import scan_repository


class PrivacySafeLoggingValidatorTest(unittest.TestCase):
    def make_source(self, root: Path, relative: str, content: str) -> None:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_finds_sensitive_values_in_multiline_timber_calls(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/main/java/sample/Unsafe.kt",
                """
                fun log(installationId: String) {
                    Timber.i(
                        "registered installationId=%s",
                        installationId,
                    )
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(["sensitive_identifier"], [finding.rule for finding in findings])

    def test_finds_absolute_path_and_full_request_url(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "feature/audio/src/main/java/sample/Unsafe.kt",
                """
                fun log(file: File, request: Request) {
                    Timber.d("path=%s", file.absolutePath)
                    Timber.w("request=%s", request.url)
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(
                ["absolute_file_path", "full_request_url"],
                [finding.rule for finding in findings],
            )

    def test_allows_safe_metadata_and_explicit_sanitization(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "core/common/src/main/java/sample/Safe.kt",
                """
                fun log(packageName: String, installationId: String) {
                    Timber.d("completed pkg=%s", packageName)
                    Timber.w("safe=%s", sanitizeLogMessage(installationId))
                }
                """,
            )

            files, findings = scan_repository(root)

            self.assertEqual(1, len(files))
            self.assertEqual([], findings)

    def test_blocks_raw_crashlytics_exceptions_and_allows_safe_wrappers(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "core/firebase/src/main/java/sample/Unsafe.kt",
                """
                fun report(crashlytics: FirebaseCrashlytics, error: Throwable) {
                    crashlytics.recordException(error)
                    crashlytics.recordException(error.toPrivacySafeThrowable())
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(
                ["raw_crashlytics_exception"],
                [finding.rule for finding in findings],
            )

    def test_ignores_debug_and_test_sources(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/debug/java/sample/Debug.kt",
                'fun log(idToken: String) = Timber.d("idToken=%s", idToken)',
            )
            self.make_source(
                root,
                "app/src/test/java/sample/Test.kt",
                'fun log(fcmToken: String) = Timber.d("fcmToken=%s", fcmToken)',
            )

            files, findings = scan_repository(root)

            self.assertEqual([], files)
            self.assertEqual([], findings)

    def test_blocks_raw_sensitive_values_when_same_call_contains_sanitizer(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/main/java/sample/Mixed.kt",
                """
                fun log(installationId: String, fcmToken: String) {
                    Timber.w(
                        "safe=%s raw=%s",
                        sanitizeLogMessage(installationId),
                        fcmToken,
                    )
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(
                ["sensitive_identifier"],
                [finding.rule for finding in findings],
            )

    def test_detects_raw_argument_after_unmatched_close_parenthesis_in_format_string(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/main/java/sample/UnmatchedClose.kt",
                """
                fun log(fcmToken: String) {
                    Timber.w(
                        "value=) %s",
                        fcmToken,
                    )
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(
                ["sensitive_identifier"],
                [finding.rule for finding in findings],
            )

    def test_detects_raw_argument_after_unmatched_open_parenthesis_in_sanitized_string(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/main/java/sample/UnmatchedOpen.kt",
                """
                fun log(fcmToken: String) {
                    Timber.w(
                        "safe=%s raw=%s",
                        sanitizeLogMessage("(safe"),
                        fcmToken,
                    )
                }
                """,
            )

            _, findings = scan_repository(root)

            self.assertEqual(
                ["sensitive_identifier"],
                [finding.rule for finding in findings],
            )

    def test_ignores_commented_out_logging_calls(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/src/main/java/sample/Commented.kt",
                """
                fun log(idToken: String) {
                    // Timber.d("idToken=%s", idToken)
                }
                """,
            )

            files, findings = scan_repository(root)

            self.assertEqual(1, len(files))
            self.assertEqual([], findings)

    def test_ignores_build_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.make_source(
                root,
                "app/build/src/main/java/sample/Generated.kt",
                'fun log(idToken: String) = Timber.d("idToken=%s", idToken)',
            )

            files, findings = scan_repository(root)

            self.assertEqual([], files)
            self.assertEqual([], findings)


if __name__ == "__main__":
    unittest.main()
