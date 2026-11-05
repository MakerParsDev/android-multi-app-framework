"""Contract checks for the accessible account/data deletion request entry and static page.

No network, user data, credentials, or email is used in these tests.
"""
from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WEB_ROOT = ROOT / "side-projects/firebase/mobil_web/public"
URL = "https://parsfilo.com/account-deletion/"
EMAIL = "support@parsfilo.com"
KEYS = {
    "settings_account_deletion_title",
    "settings_account_deletion_description",
    "settings_account_deletion_action",
    "settings_account_deletion_unavailable",
}


class StaticPageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.sections: set[str] = set()
        self.deletion_scopes: dict[str, int] = {}
        self.current_section: str | None = None
        self.emails: list[str] = []
        self.canonicals: list[str] = []
        self.links: set[str] = set()

    def handle_endtag(self, tag: str) -> None:
        if tag == "section":
            self.current_section = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "section" and values.get("id"):
            self.sections.add(values["id"])
            self.current_section = values["id"]
        if values.get("data-deletion-scope") == "account-data" and self.current_section:
            self.deletion_scopes[self.current_section] = self.deletion_scopes.get(self.current_section, 0) + 1
        if tag == "a" and values.get("href"):
            self.links.add(values["href"])
            if values["href"].startswith("mailto:"):
                self.emails.append(values["href"])
        if tag == "link" and values.get("rel") == "canonical":
            self.canonicals.append(values.get("href", ""))


class AccountDeletionContractTest(unittest.TestCase):
    def test_all_four_resource_sets_define_same_keys(self) -> None:
        for locale in ("values", "values-tr", "values-en", "values-de"):
            with self.subTest(locale=locale):
                path = ROOT / f"feature/settings/src/main/res/{locale}/strings.xml"
                entries = ET.parse(path).getroot().findall("string")
                texts = {entry.attrib["name"]: (entry.text or "") for entry in entries}
                self.assertTrue(KEYS.issubset(texts))
                self.assertTrue(all(texts[key].strip() for key in KEYS))

    def test_android_uses_public_https_link_with_fragment_for_selected_language(self) -> None:
        source = (ROOT / "feature/settings/src/main/java/com/parsfilo/contentapp/"
                  "feature/settings/ui/SettingsScreen.kt").read_text()
        self.assertIn(f'ACCOUNT_DELETION_REQUEST_URL = "{URL}"', source)
        self.assertIn('"$ACCOUNT_DELETION_REQUEST_URL#$language".toUri()', source)
        self.assertIn("Intent(Intent.ACTION_VIEW", source)
        self.assertIn("ActivityNotFoundException", source)
        self.assertIn("settings_account_deletion_title", source)

    def test_web_request_path_is_accessible_in_three_languages(self) -> None:
        path = WEB_ROOT / "account-deletion/index.html"
        page = path.read_text()
        parser = StaticPageParser()
        parser.feed(page)
        self.assertEqual(parser.sections, {"tr", "en", "de"})
        self.assertEqual(parser.canonicals, [URL])
        self.assertEqual(set(parser.emails), {
            "mailto:support@parsfilo.com?subject=Parsfilo%20hesap%20ve%20veri%20silme%20talebi",
            "mailto:support@parsfilo.com?subject=Parsfilo%20account%20and%20data%20deletion%20request",
            "mailto:support@parsfilo.com?subject=Parsfilo%20Konto%20und%20Daten%20loeschen",
        })
        self.assertIn("Abonnement", page)
        self.assertIn("Google Play", page)
        self.assertIn("automatically", page)
        self.assertNotIn("mailto:?to=", page)
        self.assertIn(EMAIL, page)

    def test_all_locales_explain_account_data_retention_and_device_boundaries(self) -> None:
        page = (WEB_ROOT / "account-deletion/index.html").read_text()
        parser = StaticPageParser()
        parser.feed(page)
        self.assertEqual(parser.deletion_scopes, {"tr": 1, "en": 1, "de": 1})
        for phrase in (
            "Firebase Authentication",
            "satın alma doğrulama kayıtları",
            "Purchase-verification records",
            "Kaufprüfungsdaten",
            "Bildirim tokenları",
            "Push tokens",
            "Push-Tokens",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, page)
        self.assertNotIn("automatically deleted within 90 days", page)
        self.assertNotIn("90 gün içinde otomatik", page)

    def test_privacy_url_sources_are_synchronized_and_link_to_request(self) -> None:
        page = (WEB_ROOT / "privacy/index.html").read_bytes()
        self.assertEqual(page, (WEB_ROOT / "privacy.html").read_bytes())
        self.assertIn(b'href="/account-deletion/"', page)

    def test_deletion_site_does_not_drop_existing_published_app(self) -> None:
        """The live public directory includes an app omitted from older sources."""
        import json

        catalog = json.loads((WEB_ROOT / "other_apps.json").read_text())
        matches = [row for row in catalog if row.get("packageName") == "com.parsfilo.imsakiye"]
        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0]["appName"], "İmsakiye")
        privacy = (WEB_ROOT / "privacy/index.html").read_text()
        self.assertEqual(privacy.count("<code>com.parsfilo.imsakiye</code>"), 2)

    def test_hosting_uses_dynamic_firebase_app_associations(self) -> None:
        """Keep Firebase-generated verification data fresh on app certificate rotations.

        Firebase Hosting defaults appAssociation to AUTO and generates .well-known
        responses at request time; copying a live response freezes its fingerprints.
        """
        import json

        hosting = json.loads(
            (ROOT / "side-projects/firebase/mobil_web/firebase.json").read_text()
        )["hosting"]
        self.assertEqual(hosting["site"], "makerpars-oaslananka-mobil")
        self.assertEqual(hosting["public"], "public")
        self.assertEqual(hosting.get("appAssociation", "AUTO"), "AUTO")
        self.assertIn("**/.*", hosting["ignore"])
        self.assertFalse((WEB_ROOT / ".well-known/assetlinks.json").exists())
        self.assertFalse((WEB_ROOT / ".well-known/apple-app-site-association").exists())

    def test_operator_procedure_maps_actual_backend_records_and_safe_order(self) -> None:
        guide = (ROOT / "docs/ACCOUNT_DELETION_REQUEST_OPERATIONS.md").read_text()
        messages = (ROOT / "feature/messages/src/main/java/com/parsfilo/contentapp/"
                    "feature/messages/data/MessageRepository.kt").read_text()
        purchases = (ROOT / "side-projects/firebase/functions/src/verifyPurchase.ts").read_text()
        devices = (ROOT / "side-projects/firebase/functions/src/registerDevice.ts").read_text()
        self.assertIn('collection("user_messages")', messages)
        self.assertIn('whereEqualTo("userId", user.uid)', messages)
        self.assertIn('collection("purchase_verifications")', purchases)
        self.assertIn('uid: record.uid', purchases)
        self.assertIn('doc(installationId)', devices)
        for requirement in (
            'Verify account ownership',
            'getUserByEmail(verifiedEmail)',
            'where("userId", "==", uid)',
            'where("uid", "==", uid)',
            'devices/{installationId}',
            'no reliable UID link is stored',
            'deleteUser(uid)',
            'Delete **last**',
            'no default 90-day deadline',
            'does not cancel a Google Play subscription',
        ):
            with self.subTest(requirement=requirement):
                self.assertIn(requirement, guide)

    def test_operator_procedure_is_human_approved_and_not_an_automatic_delete(self) -> None:
        guide = (ROOT / "docs/ACCOUNT_DELETION_REQUEST_OPERATIONS.md").read_text()
        self.assertIn('STOP before any irreversible operation', guide)
        self.assertIn('Do not trigger any of these deletes for a demonstration', guide)
        self.assertIn('test identities', guide)
        self.assertNotIn('firebase firestore:delete', guide)
        self.assertNotIn('firebase auth:delete', guide)
        self.assertNotIn('serviceAccountKey.json', guide)

    def test_provider_erasure_does_not_infer_uid_to_device_or_analytics_ids(self) -> None:
        guide = (ROOT / "docs/ACCOUNT_DELETION_REQUEST_OPERATIONS.md").read_text()
        analytics = (ROOT / "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/"
                     "AppAnalytics.kt").read_text()
        policy = (ROOT / "core/firebase/src/main/java/com/parsfilo/contentapp/core/firebase/"
                  "AnalyticsPayloadPolicy.kt").read_text()
        devices = (ROOT / "side-projects/firebase/functions/src/registerDevice.ts").read_text()
        self.assertIn('userId.startsWith("anon_")', policy)
        self.assertIn('opaque anon_* identifier', analytics)
        self.assertIn('.doc(installationId)', devices)
        for phrase in (
            "Do not equate Firebase Auth UID",
            "no supported",
            "properties.submitUserDeletion",
            "resetAnalyticsData()",
            "not interchangeable",
            "submission is not verified deletion",
            "explicit approval",
            "unresolved",
        ):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase.lower(), guide.lower())

    def test_page_is_static_and_never_auto_submits(self) -> None:
        html = (WEB_ROOT / "account-deletion/index.html").read_text().lower()
        self.assertNotIn("<form", html)
        self.assertNotIn("<script", html)
        self.assertNotIn('type="submit"', html)
        self.assertNotIn("fetch(", html)


if __name__ == "__main__":
    unittest.main()
