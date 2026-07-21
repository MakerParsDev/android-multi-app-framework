#!/usr/bin/env python3
"""
Fill missing Play Store listing translations for all app flavors.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
import time
from dataclasses import dataclass

from deep_translator import GoogleTranslator
from google.oauth2 import service_account
from googleapiclient.discovery import build


FLAVOR_PATTERN = re.compile(
    r'FlavorConfig\(\s*"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"'
)
MAX_TITLE = 30
MAX_SHORT = 80
MAX_FULL = 4000


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fill missing Play Store listing translations")
    parser.add_argument("--flavors", default="all", help="Comma-separated flavor list or 'all' (default)")
    parser.add_argument("--service-account", default="SECRET/makerpars-oaslananka-mobil-FullAdminServiceAccount.json", help="Path to Play service account json")
    parser.add_argument("--reference-package", default="com.parsfilo.amenerrasulu", help="Package used as target language set reference")
    parser.add_argument("--source-language", default="tr-TR", help="Preferred source listing language")
    parser.add_argument("--sleep-ms", type=int, default=200, help="Delay between listing update calls")
    parser.add_argument("--dry-run", action="store_true", help="Do not write changes to Play")
    return parser.parse_args()


def trim_limit(text: str, limit: int) -> str:
    t = (text or "").strip()
    if len(t) <= limit:
        return t
    return t[:limit].rstrip()


def read_flavors(flavor_file: pathlib.Path) -> dict[str, FlavorInfo]:
    text = flavor_file.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}
    for match in FLAVOR_PATTERN.finditer(text):
        name = match.group("name").strip()
        pkg = match.group("pkg").strip()
        result[name] = FlavorInfo(name=name, package_name=pkg)
    if not result:
        raise RuntimeError(f"No flavors parsed from {flavor_file}")
    return result


def build_android_publisher(service_account_path: pathlib.Path):
    if not service_account_path.exists():
        raise RuntimeError(f"Service account file not found: {service_account_path}")
    service_account_info = json.loads(service_account_path.read_text(encoding="utf-8"))
    creds = service_account.Credentials.from_service_account_info(
        service_account_info,
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def to_translator_target(locale_code: str, supported: set[str]) -> str:
    raw = (locale_code or "").replace("_", "-").strip()
    if not raw:
        return "en"

    lowered = raw.lower()
    base = lowered.split("-")[0]

    special = {
        "es-419": "es",
        "pt-br": "pt",
        "pt-pt": "pt",
        "zh-cn": "zh-cn",
        "zh-tw": "zh-tw",
        "nb-no": "no",
        "he-il": "he",
        "fil": "tl",
    }

    for candidate in (
        special.get(lowered),
        lowered,
        base,
        {"nb": "no", "he": "he", "fil": "tl"}.get(base),
    ):
        if candidate and candidate in supported:
            return candidate

    return "en"


def translate_cached(text: str, target_locale: str, supported_codes: set[str], cache: dict[tuple[str, str], str]) -> str:
    if not text:
        return ""

    target_code = to_translator_target(target_locale, supported_codes)
    key = (text, target_code)
    cached = cache.get(key)
    if cached is not None:
        return cached

    try:
        translated = GoogleTranslator(source="auto", target=target_code).translate(text)
        if not translated:
            translated = text
    except Exception:
        translated = text

    cache[key] = translated
    return translated


def open_edit(service, package_name: str) -> str:
    resp = service.edits().insert(packageName=package_name, body={}).execute()
    edit_id = str(resp.get("id") or "").strip()
    if not edit_id:
        raise RuntimeError("Missing edit id in response")
    return edit_id


def list_listings(service, package_name: str, edit_id: str) -> list[dict]:
    resp = service.edits().listings().list(packageName=package_name, editId=edit_id).execute()
    listings = resp.get("listings") or []
    return listings if isinstance(listings, list) else []


def find_source_listing(listings: list[dict], preferred_language: str) -> dict | None:
    if not listings:
        return None
    preferred = (preferred_language or "").strip()
    if preferred:
        for item in listings:
            if str(item.get("language") or "").strip().lower() == preferred.lower():
                return item
    for fallback in ("tr-TR", "tr"):
        for item in listings:
            if str(item.get("language") or "").strip().lower() == fallback.lower():
                return item
    return listings[0]


def update_listing(service, package_name: str, edit_id: str, language: str, title: str, short_desc: str, full_desc: str) -> None:
    body = {
        "language": language,
        "title": trim_limit(title, MAX_TITLE),
        "shortDescription": trim_limit(short_desc, MAX_SHORT),
        "fullDescription": trim_limit(full_desc, MAX_FULL),
    }
    service.edits().listings().update(
        packageName=package_name,
        editId=edit_id,
        language=language,
        body=body,
    ).execute()


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    service_account_path = (repo_root / args.service_account).resolve()

    all_flavors = read_flavors(flavor_file)
    requested = [x.strip() for x in args.flavors.split(",") if x.strip()]
    if not requested or requested == ["all"]:
        selected = list(all_flavors.values())
    else:
        unknown = [x for x in requested if x not in all_flavors]
        if unknown:
            print(f"ERROR: Unknown flavor(s): {', '.join(unknown)}")
            return 1
        selected = [all_flavors[x] for x in requested]

    service = build_android_publisher(service_account_path)

    ref_edit = open_edit(service, args.reference_package)
    try:
        ref_listings = list_listings(service, args.reference_package, ref_edit)
        target_languages = sorted({str(item.get("language") or "").strip() for item in ref_listings if str(item.get("language") or "").strip()})
    finally:
        try:
            service.edits().delete(packageName=args.reference_package, editId=ref_edit).execute()
        except Exception:
            pass

    if not target_languages:
        print("ERROR: Could not determine target language set from reference package.")
        return 1

    supported_values = set(GoogleTranslator().get_supported_languages(as_dict=True).values())
    translation_cache: dict[tuple[str, str], str] = {}

    print(f"Target language count: {len(target_languages)} (reference: {args.reference_package})")

    total_created = 0
    total_skipped = 0
    failed_packages: list[str] = []

    for flavor in selected:
        package_name = flavor.package_name
        print(f"\n[{flavor.name}] {package_name}")
        edit_id = ""
        changed = 0
        try:
            edit_id = open_edit(service, package_name)
            listings = list_listings(service, package_name, edit_id)
            existing_langs = {str(item.get("language") or "").strip() for item in listings if str(item.get("language") or "").strip()}

            source_listing = find_source_listing(listings, args.source_language)
            if source_listing is None:
                print("  - SKIP: no existing source listing found.")
                total_skipped += 1
                continue

            src_title = str(source_listing.get("title") or "").strip()
            src_short = str(source_listing.get("shortDescription") or "").strip()
            src_full = str(source_listing.get("fullDescription") or "").strip()
            if not (src_title and src_short and src_full):
                print("  - SKIP: source listing has missing fields.")
                total_skipped += 1
                continue

            missing = [lang for lang in target_languages if lang not in existing_langs]
            print(f"  - existing={len(existing_langs)} missing={len(missing)} source={source_listing.get('language')}")
            if not missing:
                total_skipped += 1
                continue

            for lang in missing:
                title = translate_cached(src_title, lang, supported_values, translation_cache)
                short_desc = translate_cached(src_short, lang, supported_values, translation_cache)
                full_desc = translate_cached(src_full, lang, supported_values, translation_cache)

                if args.dry_run:
                    changed += 1
                else:
                    update_listing(service, package_name, edit_id, lang, title, short_desc, full_desc)
                    changed += 1
                    time.sleep(max(args.sleep_ms, 0) / 1000.0)

            if changed > 0 and not args.dry_run:
                service.edits().commit(packageName=package_name, editId=edit_id).execute()
                edit_id = ""
            total_created += changed
            print(f"  - created={changed}")
        except Exception as exc:
            failed_packages.append(f"{flavor.name}: {str(exc).splitlines()[0]}")
            print(f"  - ERROR: {exc}")
        finally:
            if edit_id:
                try:
                    service.edits().delete(packageName=package_name, editId=edit_id).execute()
                except Exception:
                    pass

    print("\n=== SUMMARY ===")
    print(f"Selected flavors: {len(selected)}")
    print(f"Created listings: {total_created}")
    print(f"Skipped flavors: {total_skipped}")
    if failed_packages:
        print(f"Failed flavors: {len(failed_packages)}")
        for item in failed_packages:
            print(f"  - {item}")
        return 1

    print("All done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
