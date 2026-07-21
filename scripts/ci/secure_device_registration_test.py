from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from validate_secure_device_registration import validate


ROOT = Path(__file__).resolve().parents[2]


VALID_MODULE = """package sample
import sample.HttpPushRegistrationSender
abstract class PushRegistrationModule {
    abstract fun bind(impl: HttpPushRegistrationSender): PushRegistrationSender
}
"""
VALID_SENDER = """
class HttpPushRegistrationSender
fun request() {
  header(\"X-Firebase-AppCheck\", token)
  put(\"installationId\", value)
  put(\"fcmToken\", value)
  put(\"tokenHash\", value)
  put(\"adRuntimeFunnelCounts\", value)
  put(\"adRuntimeSuppressReasonCounts\", value)
}
"""
VALID_WORKER = """
import { handleDeviceRegistration } from \"./deviceRegistration\";
async function handleRegisterDevice() {}
if (path === \"/registerDevice\" || path === \"/register-device\") {
  response = await handleRegisterDevice(request, env);
}
"""
VALID_RULES = """
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /devices/{deviceId} {
      allow read: if isAdmin();
      allow write: if false;
    }
  }
}
"""
VALID_WRANGLER = """
[vars]
REGISTER_DEVICE_REQUIRE_APP_CHECK = \"true\"
"""


class SecureDeviceRegistrationValidatorTest(unittest.TestCase):
    def write_fixture(self, root: Path) -> None:
        files = {
            "core/firebase/src/main/java/sample/di/PushRegistrationModule.kt": VALID_MODULE,
            "core/firebase/src/main/java/sample/push/HttpPushRegistrationSender.kt": VALID_SENDER,
            "side-projects/cloudflare/workers/admin-api/src/index.ts": VALID_WORKER,
            "side-projects/cloudflare/workers/admin-api/src/deviceRegistration.ts": "export function handleDeviceRegistration() {}",
            "side-projects/cloudflare/workers/admin-api/wrangler.toml": VALID_WRANGLER,
            "side-projects/firebase/firestore.rules": VALID_RULES,
        }
        for relative, content in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

    def test_accepts_backend_only_registration_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.write_fixture(root)

            result = validate(root)

            self.assertEqual(6, result.files_checked)

    def test_gradle_task_passes_resolved_report_path(self) -> None:
        gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        task = gradle.split(
            'val validateSecureDeviceRegistrationTask = tasks.register<Exec>("validateSecureDeviceRegistration")',
            maxsplit=1,
        )[1].split('val auditDependencyCatalogTask', maxsplit=1)[0]

        self.assertIn(
            'val reportFile = layout.buildDirectory.file("reports/security/secure-device-registration.json")',
            task,
        )
        self.assertIn('"--report"', task)
        self.assertIn("reportFile.get().asFile.absolutePath", task)

    def test_rejects_each_security_regression(self) -> None:
        mutations = {
            "Firestore sender binding": (
                "core/firebase/src/main/java/sample/di/PushRegistrationModule.kt",
                VALID_MODULE.replace("HttpPushRegistrationSender", "FirestorePushRegistrationSender"),
                "Hilt must bind HttpPushRegistrationSender",
            ),
            "production Firestore sender": (
                "core/firebase/src/main/java/sample/push/FirestorePushRegistrationSender.kt",
                "class FirestorePushRegistrationSender",
                "must not exist",
            ),
            "client write permission": (
                "side-projects/firebase/firestore.rules",
                VALID_RULES.replace("allow write: if false;", "allow create, update: if true;"),
                "deny every client write",
            ),
            "missing App Check header": (
                "core/firebase/src/main/java/sample/push/HttpPushRegistrationSender.kt",
                VALID_SENDER.replace('header(\"X-Firebase-AppCheck\", token)', ""),
                "X-Firebase-AppCheck",
            ),
            "disabled Worker enforcement": (
                "side-projects/cloudflare/workers/admin-api/wrangler.toml",
                VALID_WRANGLER.replace('"true"', '"false"'),
                "REGISTER_DEVICE_REQUIRE_APP_CHECK",
            ),
            "missing Worker route": (
                "side-projects/cloudflare/workers/admin-api/src/index.ts",
                VALID_WORKER.replace('/registerDevice', '/missing'),
                "/registerDevice",
            ),
        }

        for label, (relative, content, expected) in mutations.items():
            with self.subTest(label=label), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                self.write_fixture(root)
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")

                with self.assertRaisesRegex(ValueError, expected):
                    validate(root)


if __name__ == "__main__":
    unittest.main()
