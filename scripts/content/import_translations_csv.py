import csv
import json
import sys
from collections import defaultdict
from pathlib import Path


def _load_csv(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8", newline="") as f:
        r = csv.DictReader(f)
        return list(r)


def _normalize_int(value: str) -> int | None:
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    try:
        return int(s)
    except Exception:
        return None


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    if len(sys.argv) < 2:
        print("usage: import_translations_csv.py TEMP_OUT/translations_export.csv", file=sys.stderr)
        return 2

    csv_path = (repo_root / sys.argv[1]).resolve()
    rows = _load_csv(csv_path)

    by_file: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        by_file[row.get("file", "")].append(row)

    changed_files: list[str] = []

    for rel, file_rows in by_file.items():
        if not rel:
            continue
        json_path = repo_root / rel
        if not json_path.exists():
            print(f"[skip] missing file: {rel}", file=sys.stderr)
            continue

        try:
            items = json.loads(json_path.read_text(encoding="utf-8"))
        except Exception as e:
            print(f"[skip] failed to parse {rel}: {e}", file=sys.stderr)
            continue

        if not isinstance(items, list) or not items:
            continue

        schema = file_rows[0].get("schema", "")
        if schema not in ("verse", "prayer"):
            continue

        touched = False

        if schema == "verse":
            index = {int(obj.get("ayetID")): obj for obj in items if isinstance(obj, dict) and "ayetID" in obj}
            for r in file_rows:
                ayet_id = _normalize_int(r.get("ayetID", ""))
                if ayet_id is None:
                    continue
                obj = index.get(ayet_id)
                if not obj:
                    continue
                en = (r.get("ayetEN") or "").strip()
                de = (r.get("ayetDE") or "").strip()
                if en != obj.get("ayetEN", ""):
                    if en:
                        obj["ayetEN"] = en
                    else:
                        obj.pop("ayetEN", None)
                    touched = True
                if de != obj.get("ayetDE", ""):
                    if de:
                        obj["ayetDE"] = de
                    else:
                        obj.pop("ayetDE", None)
                    touched = True

        elif schema == "prayer":
            prayers_by_id = {}
            for p in items:
                if isinstance(p, dict) and "sureID" in p:
                    prayers_by_id[int(p.get("sureID"))] = p

            # Build verse index per sureID
            verse_index: dict[tuple[int, int], dict] = {}
            for sure_id, p in prayers_by_id.items():
                verses = p.get("ayetler", [])
                if not isinstance(verses, list):
                    continue
                for v in verses:
                    if isinstance(v, dict) and "ayetID" in v:
                        verse_index[(sure_id, int(v.get("ayetID")))] = v

            for r in file_rows:
                sure_id = _normalize_int(r.get("sureID", ""))
                ayet_id = _normalize_int(r.get("ayetID", ""))
                if sure_id is None or ayet_id is None:
                    continue
                v = verse_index.get((sure_id, ayet_id))
                if not v:
                    continue
                en = (r.get("ayetEN") or "").strip()
                de = (r.get("ayetDE") or "").strip()
                if en != v.get("ayetEN", ""):
                    if en:
                        v["ayetEN"] = en
                    else:
                        v.pop("ayetEN", None)
                    touched = True
                if de != v.get("ayetDE", ""):
                    if de:
                        v["ayetDE"] = de
                    else:
                        v.pop("ayetDE", None)
                    touched = True

        if touched:
            json_path.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            changed_files.append(rel)

    for f in changed_files:
        print(f)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

