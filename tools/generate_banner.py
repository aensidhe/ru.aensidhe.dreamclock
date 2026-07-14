#!/usr/bin/env python3
"""Generate the Android TV banner (320x180 xhdpi) for Reverie.

Draws the five-stop dawn-to-dusk gradient, a small clock mark, and the
"Reverie" wordmark. Run with the repo-local Pillow venv:

    tmp/imgvenv/bin/python tools/generate_banner.py

Output: app/src/main/res/drawable-xhdpi/tv_banner.png (committed asset).
"""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

W, H = 320, 180
SS = 4  # supersample factor: draw at SS× then downscale for crisp edges
SHIFT = 10  # nudge clock + wordmark left so they aren't stuck to the right edge
STOPS = [
    (0.00, (0x1F, 0xA9, 0xA0)),
    (0.25, (0x9F, 0xC9, 0x7C)),
    (0.50, (0xF5, 0xB3, 0x42)),
    (0.75, (0xB0, 0x7C, 0x6E)),
    (1.00, (0x4B, 0x2A, 0x4A)),
]
CREAM = (0xFF, 0xF6, 0xE9)
INK = (0x2A, 0x23, 0x40)
OUT = "app/src/main/res/drawable-xhdpi/tv_banner.png"
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/noto/NotoSans-Bold.ttf",
]


def lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def color_at(t: float) -> tuple[int, int, int]:
    for i in range(len(STOPS) - 1):
        o0, c0 = STOPS[i]
        o1, c1 = STOPS[i + 1]
        if o0 <= t <= o1:
            k = 0.0 if o1 == o0 else (t - o0) / (o1 - o0)
            return tuple(lerp(c0[j], c1[j], k) for j in range(3))
    return STOPS[-1][1]


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main() -> None:
    s = SS
    sw, sh = W * s, H * s
    img = Image.new("RGB", (sw, sh))
    px = img.load()
    for x in range(sw):
        r, g, b = color_at(x / (sw - 1))
        for y in range(sh):
            px[x, y] = (r, g, b)

    draw = ImageDraw.Draw(img)
    # Clock mark on the left.
    cx, cy, rad = (78 - SHIFT) * s, (H // 2) * s, 52 * s
    draw.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=CREAM, outline=INK, width=4 * s)
    draw.line([cx, cy, cx, cy - 34 * s], fill=INK, width=5 * s)      # minute hand -> 12
    draw.line([cx, cy, cx + 26 * s, cy - 13 * s], fill=INK, width=6 * s)  # hour hand -> ~2
    draw.ellipse([cx - 5 * s, cy - 5 * s, cx + 5 * s, cy + 5 * s], fill=INK)

    # Wordmark on the right.
    font = load_font(46 * s)
    draw.text(((150 - SHIFT) * s, cy), "Reverie", font=font, fill=CREAM, anchor="lm")

    img = img.resize((W, H), Image.LANCZOS)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
