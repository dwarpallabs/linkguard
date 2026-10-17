#!/usr/bin/env python3
"""Generates Google Play graphics from the app's own icon geometry and raw screenshots.

Needs Pillow (a dev-only tool; not an app dependency):
  python3 -m venv /tmp/venv && /tmp/venv/bin/pip install pillow
  /tmp/venv/bin/python docs/play/make_assets.py [raw_screenshot_dir]

Outputs docs/play/graphics/: icon-512.png, feature-1024x500.png and, if a raw screenshot
directory is given (1080x2400 phone captures named <name>_<lang>.png), screenshots fitted to
1080x1920 (Play allows at most a 2:1 aspect ratio).
"""
import math
import pathlib
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).parent / "graphics"
INDIGO = (30, 58, 138)  # @drawable/ic_launcher_background #1E3A8A
WHITE = (255, 255, 255)
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
SS = 4  # supersampling factor for smooth edges


def cubic(p0, p1, p2, p3, steps=40):
    return [
        tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d
              for a, b, c, d in zip(p0, p1, p2, p3))
        for t in (i / steps for i in range(steps + 1))
    ]


def shield_points():
    """The shield from ic_launcher_foreground.xml, in its 108-unit viewport:
    M54,27 L76,35 L76,52 C76,66 66.5,76.5 54,81 C41.5,76.5 32,66 32,52 L32,35 Z"""
    return ([(54, 27), (76, 35), (76, 52)]
            + cubic((76, 52), (76, 66), (66.5, 76.5), (54, 81))[1:]
            + cubic((54, 81), (41.5, 76.5), (32, 66), (32, 52))[1:]
            + [(32, 35)])


def draw_mark(size, units, offset=(0, 0)):
    """Shield + chain link on a transparent layer of `size` px, showing `units` viewport units."""
    s = size * SS
    scale = s / units
    shift = (108 - units) / 2

    def px(p):
        return ((p[0] - shift) * scale + offset[0] * SS, (p[1] - shift) * scale + offset[1] * SS)

    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon([px(p) for p in shield_points()], fill=WHITE)

    # Two interlocking links (stadium outlines, stroke 4), rotated -45 degrees about (54,54).
    links = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(links)
    stroke = max(1, round(4 * scale))
    for x0, x1 in ((37, 57), (51, 71)):
        a, b = px((x0, 49)), px((x1, 59))
        d.rounded_rectangle([a, b], radius=5 * scale, outline=INDIGO + (255,), width=stroke)
    centre = px((54, 54))
    links = links.rotate(45, resample=Image.BICUBIC, center=centre)
    layer.alpha_composite(links)
    return layer.resize((size, size), Image.LANCZOS)


def icon(path):
    size = 512
    img = Image.new("RGBA", (size, size), INDIGO + (255,))
    img.alpha_composite(draw_mark(size, units=78))
    img.convert("RGB").save(path, optimize=True)  # Play: 32-bit PNG is fine; opaque avoids halo issues


def feature(path):
    w, h = 1024, 500
    img = Image.new("RGBA", (w, h), INDIGO + (255,))
    mark = draw_mark(360, units=60)
    img.alpha_composite(mark, (60, (h - 360) // 2))
    d = ImageDraw.Draw(img)
    d.text((450, 120), "LinkGuard", font=ImageFont.truetype(FONT_BOLD, 84), fill=WHITE)
    body = ImageFont.truetype(FONT, 34)
    d.text((454, 235), "Checks chat and SMS links", font=body, fill=WHITE)
    d.text((454, 280), "for scams before they open.", font=body, fill=WHITE)
    d.text((454, 352), "English · Hindi · Tamil  |  Free · Open source",
           font=ImageFont.truetype(FONT, 24), fill=(199, 210, 254))
    img.convert("RGB").save(path, optimize=True)


def fit_screenshot(src, dst, bg=(248, 246, 252)):
    """Scale a 1080x2400 capture into a 1080x1920 canvas (Play: long side <= 2x short side)."""
    shot = Image.open(src).convert("RGB")
    target_h = 1920
    target_w = round(shot.width * target_h / shot.height)
    shot = shot.resize((target_w, target_h), Image.LANCZOS)
    canvas = Image.new("RGB", (1080, 1920), bg)
    canvas.paste(shot, ((1080 - target_w) // 2, 0))
    canvas.save(dst, optimize=True)


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    icon(OUT / "icon-512.png")
    feature(OUT / "feature-1024x500.png")
    if len(sys.argv) > 1:
        shots = OUT / "screenshots"
        shots.mkdir(exist_ok=True)
        for src in sorted(pathlib.Path(sys.argv[1]).glob("*.png")):
            fit_screenshot(src, shots / src.name)
    for p in sorted(OUT.rglob("*.png")):
        with Image.open(p) as im:
            print(f"{p.relative_to(OUT.parent)}  {im.size[0]}x{im.size[1]}  {im.mode}")
