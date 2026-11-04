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
        self.emails: list[str] = []
        self.canonicals: list[str] = []
        self.links: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "section" and values.get("id"):
            self.sections.add(values["id"])
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

    def test_privacy_url_sources_are_synchronized_and_link_to_request(self) -> None:
        page = (WEB_ROOT / "privacy/index.html").read_bytes()
        self.assertEqual(page, (WEB_ROOT / "privacy.html").read_bytes())
        self.assertIn(b'href="/account-deletion/"', page)

    def test_page_is_static_and_never_auto_submits(self) -> None:
        html = (WEB_ROOT / "account-deletion/index.html").read_text().lower()
        self.assertNotIn("<form", html)
        self.assertNotIn("<script", html)
        self.assertNotIn('type="submit"', html)
        self.assertNotIn("fetch(", html)


if __name__ == "__main__":
    unittest.main()
