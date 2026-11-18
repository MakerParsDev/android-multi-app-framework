#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from resolve_ci_flavor_matrix import load_flavors

ALLOWED_RETENTION = {7, 14, 30}
AD_INSPECTOR_DEBUG_FLAVORS = frozenset(
    {"namazsurelerivedualarsesli", "ismiazamduasi"}
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--flavor", required=True)
    parser.add_argument("--retention-days", required=True)
    parser.add_argument(
        "--enable-ad-inspector-debug",
        choices=("true", "false"),
        default="false",
    )
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        flavors = load_flavors(root)
        apps = json.loads((root / ".ci/apps.json").read_text(encoding="utf-8"))
        package_by_flavor = {entry["flavor"]: entry["package"] for entry in apps}
        retention = int(args.retention_days)
    except (RuntimeError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    if args.flavor not in flavors:
        print(
            f"ERROR: unknown flavor {args.flavor!r}; allowed: {', '.join(flavors)}",
            file=sys.stderr,
        )
        return 1
    if retention not in ALLOWED_RETENTION:
        print("ERROR: retention days must be one of 7, 14, or 30", file=sys.stderr)
        return 1

    enable_ad_inspector_debug = args.enable_ad_inspector_debug == "true"
    if enable_ad_inspector_debug and args.flavor not in AD_INSPECTOR_DEBUG_FLAVORS:
        allowed = ", ".join(sorted(AD_INSPECTOR_DEBUG_FLAVORS))
        print(
            "ERROR: Ad Inspector debug is only supported for the #337 test flavors: "
            f"{allowed}",
            file=sys.stderr,
        )
        return 1

    print(f"flavor={args.flavor}")
    package_name = package_by_flavor.get(args.flavor)
    if not isinstance(package_name, str) or not package_name.strip():
        print(f"ERROR: missing package name for flavor {args.flavor!r}", file=sys.stderr)
        return 1
    print(f"capitalized={args.flavor[0].upper() + args.flavor[1:]}")
    print(f"package_name={package_name}")
    print(f"retention_days={retention}")
    print(f"enable_ad_inspector_debug={str(enable_ad_inspector_debug).lower()}")
    release_name_prefix = "internal-ad-inspector-debug" if enable_ad_inspector_debug else "internal"
    print(f"release_name_prefix={release_name_prefix}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
