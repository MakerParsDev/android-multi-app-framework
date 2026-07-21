import json
import re
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE = REPO_ROOT / "TEMP_FOLDER" / "esma_veri.txt"
TARGET = REPO_ROOT / "app" / "src" / "esmaulhusna" / "assets" / "data.json"

ENTRY_RE = re.compile(r"^\s*([^(]+?)\s*\(([^)]+)\)\s*:\s*(.+?)\s*$")


def main() -> None:
    text = SOURCE.read_text(encoding="utf-8")
    non_empty_lines = [line.strip() for line in text.splitlines() if line.strip()]

    rows = []
    skipped = []
    for line in non_empty_lines:
        match = ENTRY_RE.match(line)
        if not match:
            skipped.append(line)
            continue
        latin = match.group(1).strip()
        arabic = match.group(2).strip()
        description = match.group(3).strip()
        rows.append(
            {
                "duaIsim": latin,
                "duaLatinOkunus": latin,
                "duaArapca": arabic,
                "duaAciklama": description,
                "duaBesmele": latin,
            }
        )

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(
        json.dumps(rows[:99], ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Source: {SOURCE}")
    print(f"Target: {TARGET}")
    print(f"Total non-empty lines: {len(non_empty_lines)}")
    print(f"Parsed entries: {len(rows)}")
    print(f"Written entries: {min(len(rows), 99)}")
    print(f"Skipped lines: {len(skipped)}")
    if skipped:
        print("Skipped sample:")
        for line in skipped[:3]:
            print(f"  - {line}")


if __name__ == "__main__":
    main()
