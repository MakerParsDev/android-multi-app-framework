#!/usr/bin/env python3
"""
Ensure Play subscriptions exist for all flavors.

Subscription:
- productId: reklamsiz_kullanim
- title: Reklamsız Kullanım

Base plans (prepaid):
- gunluk   : P1D, TRY 20
- haftalik : P1W, TRY 100
- aylik    : P1M, TRY 300
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from dataclasses import dataclass

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

FLAVOR_PATTERN = re.compile(
    r'FlavorConfig\(\s*"(?P<name>[^"]+)"\s*,\s*"[^"]*"\s*,\s*"(?P<pkg>[^"]+)"'
)

PRODUCT_ID = "reklamsiz_kullanim"
TR_LANGUAGE = "tr-TR"
TR_REGION = "TR"

SUB_TITLE = "Reklamsız Kullanım"
SUB_DESC = "Uygulamayı reklamsız kullanmanızı sağlar."
SUB_BENEFITS = [
    "Tüm reklamları kaldırır",
    "Kesintisiz kullanım",
    "Daha sade deneyim",
]

TARGET_BASE_PLANS = {
    "gunluk": ("P1D", 20),
    "haftalik": ("P1W", 100),
    "aylik": ("P1M", 300),
}


@dataclass(frozen=True)
class FlavorInfo:
    name: str
    package_name: str


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Ensure Play subscriptions/base plans for all flavors")
    p.add_argument("--flavors", default="all", help="Comma-separated flavor list or 'all'")
    p.add_argument(
        "--service-account",
        default="SECRET/makerpars-oaslananka-mobil-FullAdminServiceAccount.json",
        help="Path to Play service account JSON",
    )
    p.add_argument("--dry-run", action="store_true", help="Only report, do not write")
    return p.parse_args()


def read_flavors(flavor_file: pathlib.Path) -> dict[str, FlavorInfo]:
    text = flavor_file.read_text(encoding="utf-8")
    result: dict[str, FlavorInfo] = {}
    for m in FLAVOR_PATTERN.finditer(text):
        result[m.group("name").strip()] = FlavorInfo(
            name=m.group("name").strip(),
            package_name=m.group("pkg").strip(),
        )
    if not result:
        raise RuntimeError(f"No flavors parsed from {flavor_file}")
    return result


def money_try(units: int) -> dict:
    return {"currencyCode": "TRY", "units": str(units), "nanos": 0}


def build_service(sa_path: pathlib.Path):
    if not sa_path.exists():
        raise RuntimeError(f"Service account not found: {sa_path}")
    sa = json.loads(sa_path.read_text(encoding="utf-8"))
    creds = service_account.Credentials.from_service_account_info(
        sa,
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def get_regions_version(service, package_name: str) -> str:
    resp = service.monetization().convertRegionPrices(
        packageName=package_name,
        body={"price": money_try(20)},
    ).execute()
    version = ((resp.get("regionVersion") or {}).get("version") or "").strip()
    if not version:
        raise RuntimeError("Could not resolve regionsVersion.version")
    return version


def sanitize_offer_tags(tags: list[dict] | None, base_plan_id: str) -> list[dict]:
    out = []
    seen = set()
    for t in tags or []:
        tag = str((t or {}).get("tag") or "").strip().lower()
        if not tag or tag in seen:
            continue
        out.append({"tag": tag})
        seen.add(tag)
    for required in (base_plan_id, "reklamsiz"):
        if required not in seen:
            out.append({"tag": required})
            seen.add(required)
    return out


def upsert_tr_listing(listings: list[dict] | None) -> list[dict]:
    items = []
    has_tr = False
    for l in listings or []:
        lang = str((l or {}).get("languageCode") or "").strip()
        if not lang:
            continue
        if lang.lower() == TR_LANGUAGE.lower():
            has_tr = True
            items.append(
                {
                    "languageCode": TR_LANGUAGE,
                    "title": SUB_TITLE,
                    "description": SUB_DESC,
                    "benefits": SUB_BENEFITS,
                }
            )
        else:
            item = {
                "languageCode": lang,
                "title": str((l or {}).get("title") or "").strip(),
                "description": str((l or {}).get("description") or "").strip(),
            }
            benefits = (l or {}).get("benefits")
            if isinstance(benefits, list) and benefits:
                item["benefits"] = [str(x) for x in benefits if str(x).strip()]
            items.append(item)
    if not has_tr:
        items.append(
            {
                "languageCode": TR_LANGUAGE,
                "title": SUB_TITLE,
                "description": SUB_DESC,
                "benefits": SUB_BENEFITS,
            }
        )
    return items


def build_target_base_plan(base_plan_id: str, period: str, tr_units: int, existing: dict | None) -> dict:
    bp = {
        "basePlanId": base_plan_id,
        "prepaidBasePlanType": {
            "billingPeriodDuration": period,
            "timeExtension": "TIME_EXTENSION_ACTIVE",
        },
        "regionalConfigs": [],
        "offerTags": sanitize_offer_tags((existing or {}).get("offerTags"), base_plan_id),
    }

    existing_rc = (existing or {}).get("regionalConfigs") or []
    tr_found = False
    normalized_rc = []
    for rc in existing_rc:
        region = str((rc or {}).get("regionCode") or "").strip().upper()
        if not region:
            continue
        if region == TR_REGION:
            tr_found = True
            normalized_rc.append(
                {
                    "regionCode": TR_REGION,
                    "newSubscriberAvailability": True,
                    "price": money_try(tr_units),
                }
            )
        else:
            normalized = {
                "regionCode": region,
                "newSubscriberAvailability": bool((rc or {}).get("newSubscriberAvailability", False)),
            }
            price = (rc or {}).get("price") or {}
            if price:
                normalized["price"] = {
                    "currencyCode": str(price.get("currencyCode") or "").strip() or "USD",
                    "units": str(price.get("units") or "0"),
                    "nanos": int(price.get("nanos") or 0),
                }
            normalized_rc.append(normalized)

    if not tr_found:
        normalized_rc.append(
            {
                "regionCode": TR_REGION,
                "newSubscriberAvailability": True,
                "price": money_try(tr_units),
            }
        )

    bp["regionalConfigs"] = normalized_rc

    if existing and existing.get("otherRegionsConfig"):
        bp["otherRegionsConfig"] = existing.get("otherRegionsConfig")

    return bp


def main() -> int:
    args = parse_args()
    repo_root = pathlib.Path(__file__).resolve().parents[2]
    flavor_file = repo_root / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
    sa_path = (repo_root / args.service_account).resolve()

    flavors = read_flavors(flavor_file)
    req = [x.strip() for x in args.flavors.split(",") if x.strip()]
    if not req or req == ["all"]:
        selected = list(flavors.values())
    else:
        missing = [x for x in req if x not in flavors]
        if missing:
            print(f"ERROR: Unknown flavor(s): {', '.join(missing)}")
            return 1
        selected = [flavors[x] for x in req]

    service = build_service(sa_path)
    region_version = get_regions_version(service, selected[0].package_name)
    print(f"Using regionsVersion.version = {region_version}")

    failures = []
    changed = 0

    for f in selected:
        pkg = f.package_name
        print(f"\n[{f.name}] {pkg}")
        try:
            try:
                sub = service.monetization().subscriptions().get(
                    packageName=pkg,
                    productId=PRODUCT_ID,
                ).execute()
                exists = True
                print("  - subscription exists")
            except HttpError as e:
                if e.resp.status != 404:
                    raise
                exists = False
                print("  - subscription missing, creating")

            if not exists:
                create_body = {
                    "packageName": pkg,
                    "productId": PRODUCT_ID,
                    "listings": [
                        {
                            "languageCode": TR_LANGUAGE,
                            "title": SUB_TITLE,
                            "description": SUB_DESC,
                            "benefits": SUB_BENEFITS,
                        }
                    ],
                    "taxAndComplianceSettings": {
                        "eeaWithdrawalRightType": "WITHDRAWAL_RIGHT_SERVICE"
                    },
                    "basePlans": [
                        {
                            "basePlanId": "gunluk",
                            "prepaidBasePlanType": {
                                "billingPeriodDuration": "P1D",
                                "timeExtension": "TIME_EXTENSION_ACTIVE",
                            },
                            "regionalConfigs": [
                                {
                                    "regionCode": TR_REGION,
                                    "newSubscriberAvailability": True,
                                    "price": money_try(20),
                                }
                            ],
                            "offerTags": [{"tag": "gunluk"}, {"tag": "reklamsiz"}],
                        }
                    ],
                }
                if not args.dry_run:
                    service.monetization().subscriptions().create(
                        packageName=pkg,
                        productId=PRODUCT_ID,
                        body=create_body,
                        regionsVersion_version=region_version,
                    ).execute()
                    changed += 1
                sub = create_body

            # Refresh after potential create.
            if not args.dry_run:
                sub = service.monetization().subscriptions().get(
                    packageName=pkg,
                    productId=PRODUCT_ID,
                ).execute()

            existing_bps = {
                str((bp or {}).get("basePlanId") or "").strip(): bp
                for bp in (sub.get("basePlans") or [])
                if str((bp or {}).get("basePlanId") or "").strip()
            }

            merged_base_plans = []
            for bp_id, (period, price_units) in TARGET_BASE_PLANS.items():
                merged_base_plans.append(
                    build_target_base_plan(bp_id, period, price_units, existing_bps.get(bp_id))
                )

            # Keep non-target base plans as-is (sanitized), don't delete existing extras.
            for bp_id, bp in existing_bps.items():
                if bp_id in TARGET_BASE_PLANS:
                    continue
                keep = {"basePlanId": bp_id}
                if bp.get("autoRenewingBasePlanType"):
                    keep["autoRenewingBasePlanType"] = bp.get("autoRenewingBasePlanType")
                if bp.get("prepaidBasePlanType"):
                    keep["prepaidBasePlanType"] = bp.get("prepaidBasePlanType")
                if bp.get("installmentsBasePlanType"):
                    keep["installmentsBasePlanType"] = bp.get("installmentsBasePlanType")
                if bp.get("regionalConfigs"):
                    keep["regionalConfigs"] = bp.get("regionalConfigs")
                if bp.get("otherRegionsConfig"):
                    keep["otherRegionsConfig"] = bp.get("otherRegionsConfig")
                if bp.get("offerTags"):
                    keep["offerTags"] = bp.get("offerTags")
                merged_base_plans.append(keep)

            patch_body = {
                "packageName": pkg,
                "productId": PRODUCT_ID,
                "listings": upsert_tr_listing(sub.get("listings") or []),
                "basePlans": merged_base_plans,
                "taxAndComplianceSettings": (
                    sub.get("taxAndComplianceSettings")
                    or {"eeaWithdrawalRightType": "WITHDRAWAL_RIGHT_SERVICE"}
                ),
            }

            if not args.dry_run:
                service.monetization().subscriptions().patch(
                    packageName=pkg,
                    productId=PRODUCT_ID,
                    body=patch_body,
                    updateMask="listings,basePlans,taxAndComplianceSettings",
                    regionsVersion_version=region_version,
                ).execute()
                changed += 1

                # Activate required base plans.
                refreshed = service.monetization().subscriptions().get(
                    packageName=pkg,
                    productId=PRODUCT_ID,
                ).execute()
                state_by_id = {
                    (bp.get("basePlanId") or ""): (bp.get("state") or "")
                    for bp in (refreshed.get("basePlans") or [])
                }

                for bp_id in TARGET_BASE_PLANS.keys():
                    if state_by_id.get(bp_id) != "ACTIVE":
                        try:
                            service.monetization().subscriptions().basePlans().activate(
                                packageName=pkg,
                                productId=PRODUCT_ID,
                                basePlanId=bp_id,
                                body={},
                            ).execute()
                        except HttpError as e:
                            # Ignore already active races.
                            if e.resp.status not in (400, 409):
                                raise

                # Verify final status and TR prices.
                final_sub = service.monetization().subscriptions().get(
                    packageName=pkg,
                    productId=PRODUCT_ID,
                ).execute()
                final_map = {bp.get("basePlanId"): bp for bp in (final_sub.get("basePlans") or [])}
                for bp_id, (_, tr_price) in TARGET_BASE_PLANS.items():
                    bp = final_map.get(bp_id)
                    if not bp:
                        raise RuntimeError(f"Missing base plan after patch: {bp_id}")
                    if (bp.get("state") or "") != "ACTIVE":
                        raise RuntimeError(f"Base plan not ACTIVE: {bp_id}")
                    tr_configs = [x for x in (bp.get("regionalConfigs") or []) if (x.get("regionCode") or "").upper() == TR_REGION]
                    if not tr_configs:
                        raise RuntimeError(f"Missing TR regional config: {bp_id}")
                    p = tr_configs[0].get("price") or {}
                    units = int(p.get("units") or 0)
                    nanos = int(p.get("nanos") or 0)
                    if units != tr_price or nanos != 0:
                        raise RuntimeError(f"Unexpected TR price for {bp_id}: {units}.{nanos}")
                print("  - ensured subscription + base plans")
            else:
                print("  - dry-run: would ensure subscription + base plans")

        except Exception as exc:  # noqa: BLE001
            failures.append(f"{f.name}: {str(exc).splitlines()[0]}")
            print(f"  - ERROR: {exc}")

    print("\n=== SUMMARY ===")
    print(f"Selected flavors: {len(selected)}")
    print(f"Mutations applied: {changed}")
    if failures:
        print(f"Failures: {len(failures)}")
        for item in failures:
            print(f"  - {item}")
        return 1

    print("All subscriptions/base plans ensured successfully.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

