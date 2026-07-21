#!/usr/bin/env python3
from __future__ import annotations

import re
import argparse
from pathlib import Path

from PIL import Image, ImageDraw


REPO_ROOT = Path(__file__).resolve().parents[2]
FLAVOR_CONFIG = REPO_ROOT / "buildSrc" / "src" / "main" / "kotlin" / "FlavorConfig.kt"
APP_SRC = REPO_ROOT / "app" / "src"


def parse_audio_flavors(flavor_config_path: Path) -> list[str]:
    text = flavor_config_path.read_text(encoding="utf-8")
    audio_flavors: list[str] = []
    line_pattern = re.compile(r'FlavorConfig\("(?P<name>[^"]+)"')
    for line in text.splitlines():
        if ".mp3" not in line:
            continue
        match = line_pattern.search(line)
        if match:
            audio_flavors.append(match.group("name"))
    return audio_flavors


def draw_speaker_glyph(draw: ImageDraw.ImageDraw, w: int, h: int) -> None:
    size = int(min(w, h) * 0.24)
    x = (w - size) // 2
    y = int(h - size * 1.16)

    fg = (0, 0, 0, 245)

    # Speaker body
    body_left = x + int(size * 0.06)
    body_top = y + int(size * 0.32)
    body_right = x + int(size * 0.28)
    body_bottom = y + int(size * 0.68)
    draw.rounded_rectangle(
        [body_left, body_top, body_right, body_bottom],
        radius=max(1, int(size * 0.03)),
        fill=fg,
    )

    # Speaker cone
    cone = [
        (body_right - 1, y + int(size * 0.24)),
        (body_right - 1, y + int(size * 0.76)),
        (x + int(size * 0.56), y + int(size * 0.50)),
    ]
    draw.polygon(cone, fill=fg)

    # Sound waves
    wave_width = max(1, int(size * 0.08))
    wave_1 = [
        x + int(size * 0.50),
        y + int(size * 0.29),
        x + int(size * 0.82),
        y + int(size * 0.71),
    ]
    wave_2 = [
        x + int(size * 0.58),
        y + int(size * 0.16),
        x + int(size * 0.98),
        y + int(size * 0.84),
    ]
    draw.arc(wave_1, start=300, end=60, fill=fg, width=wave_width)
    draw.arc(wave_2, start=306, end=54, fill=fg, width=wave_width)


def has_existing_badge(image: Image.Image) -> bool:
    rgba = image.convert("RGBA")
    w, h = rgba.size
    size = int(min(w, h) * 0.24)
    x0 = (w - size) // 2
    y0 = int(h - size * 1.16)
    x1 = min(w, x0 + size)
    y1 = min(h, y0 + size)
    if x1 <= x0 or y1 <= y0:
        return False

    dark_pixels = 0
    sampled = 0
    pixels = rgba.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = pixels[x, y]
            sampled += 1
            if a >= 200 and r <= 30 and g <= 30 and b <= 30:
                dark_pixels += 1

    # Badge area has a concentrated black symbol.
    return sampled > 0 and (dark_pixels / sampled) >= 0.06


def apply_badge(image_path: Path, force: bool) -> bool:
    with Image.open(image_path) as image:
        if not force and has_existing_badge(image):
            return False
        rgba = image.convert("RGBA")
        draw = ImageDraw.Draw(rgba)
        draw_speaker_glyph(draw, *rgba.size)
        rgba.save(image_path)
        return True


def collect_targets(flavor: str) -> list[Path]:
    base = APP_SRC / flavor
    if not base.exists():
        return []

    targets: list[Path] = []
    patterns = [
        "res/mipmap-*/ic_launcher.png",
        "res/mipmap-*/ic_launcher_round.png",
        "res/mipmap-*/ic_launcher_foreground.png",
        "res/mipmap-*/ic_launcher_round_foreground.png",
        "play_store_512.png",
    ]
    for pattern in patterns:
        targets.extend(base.glob(pattern))
    return sorted(set(targets))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Apply speaker badge to audio app icons")
    parser.add_argument("--force", action="store_true", help="Apply badge even if already detected")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    flavors = parse_audio_flavors(FLAVOR_CONFIG)
    if not flavors:
        raise SystemExit("No audio flavors found in FlavorConfig.kt")

    touched: list[Path] = []
    skipped = 0
    for flavor in flavors:
        for target in collect_targets(flavor):
            if apply_badge(target, force=args.force):
                touched.append(target)
            else:
                skipped += 1

    print(f"Audio flavors: {', '.join(flavors)}")
    print(f"Updated {len(touched)} icon files.")
    print(f"Skipped {skipped} files (already badged).")


if __name__ == "__main__":
    main()
