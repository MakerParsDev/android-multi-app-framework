import csv
import json
import sys
from pathlib import Path


def _detect_schema(items: list[dict]) -> str:
    if not items:
        return "unknown"
    first = items[0]
    # Verse schema: [{"ayetID": 1, "ayetAR": "...", "ayetLAT": "...", "ayetTR": "...", ...}, ...]
    if isinstance(first, dict) and "ayetID" in first and "ayetAR" in first:
        return "verse"
    # Prayer schema: [{"sureID": 0, "ayetler": [{"ayetID": 1, ...}], ...}, ...]
    if isinstance(first, dict) and "sureID" in first and "ayetler" in first:
        return "prayer"
    return "unknown"


def _iter_data_json_files(repo_root: Path) -> list[Path]:
    files: list[Path] = []
    for p in repo_root.glob("app/src/*/assets/data.json"):
        files.append(p)
    # Feature fallback asset (used if app flavor doesn't provide its own)
    fc = repo_root / "feature/content/src/main/assets/data.json"
    if fc.exists():
        files.append(fc)
    return sorted(set(files))


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    out_path = repo_root / "TEMP_OUT" / "translations_export.csv"
    out_path.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict] = []

    for path in _iter_data_json_files(repo_root):
        try:
            items = json.loads(path.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"[skip] failed to read {path}: {e}", file=sys.stderr)
            continue

        if not isinstance(items, list):
            print(f"[skip] not a list: {path}", file=sys.stderr)
            continue

        schema = _detect_schema(items)
        if schema == "unknown":
            print(f"[skip] unknown schema: {path}", file=sys.stderr)
            continue

        rel = str(path.relative_to(repo_root)).replace("\\", "/")

        if schema == "verse":
            for obj in items:
                if not isinstance(obj, dict):
                    continue
                rows.append(
                    {
                        "file": rel,
                        "schema": "verse",
                        "sureID": "",
                        "ayetID": obj.get("ayetID", ""),
                        "ayetAR": obj.get("ayetAR", ""),
                        "ayetLAT": obj.get("ayetLAT", ""),
                        "ayetTR": obj.get("ayetTR", ""),
                        "ayetEN": obj.get("ayetEN", ""),
                        "ayetDE": obj.get("ayetDE", ""),
                    }
                )
        elif schema == "prayer":
            for prayer in items:
                if not isinstance(prayer, dict):
                    continue
                sure_id = prayer.get("sureID", "")
                verses = prayer.get("ayetler", [])
                if not isinstance(verses, list):
                    continue
                for v in verses:
                    if not isinstance(v, dict):
                        continue
                    rows.append(
                        {
                            "file": rel,
                            "schema": "prayer",
                            "sureID": sure_id,
                            "ayetID": v.get("ayetID", ""),
                            "ayetAR": v.get("ayetAR", ""),
                            "ayetLAT": v.get("ayetLAT", ""),
                            "ayetTR": v.get("ayetTR", ""),
                            "ayetEN": v.get("ayetEN", ""),
                            "ayetDE": v.get("ayetDE", ""),
                        }
                    )

    fieldnames = [
        "file",
        "schema",
        "sureID",
        "ayetID",
        "ayetAR",
        "ayetLAT",
        "ayetTR",
        "ayetEN",
        "ayetDE",
    ]

    with out_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for r in rows:
            w.writerow(r)

    print(str(out_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

