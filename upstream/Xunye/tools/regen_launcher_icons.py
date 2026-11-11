"""SPEC-252/253/256: regenerate launcher/about icons from square logo PNG.

SPEC-256: adaptive safe-zone pad_ratio=0.15; do not overwrite splash (keep pre-252).
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

SRC = Path(r"C:\Users\32082\Documents\browsesth-logo-512-square.png")
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
# SPEC-256: 15% inset (was 18% in SPEC-253).
SAFE_PAD = 0.15


def fit_square(im: Image.Image, size: int, pad_ratio: float = 0.0) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = size if pad_ratio <= 0 else int(size * (1 - 2 * pad_ratio))
    scaled = ImageOps.contain(im, (content, content), Image.Resampling.LANCZOS)
    x = (size - scaled.width) // 2
    y = (size - scaled.height) // 2
    canvas.paste(scaled, (x, y), scaled)
    return canvas


def to_monochrome(im: Image.Image) -> Image.Image:
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    px = im.load()
    op = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, al = px[x, y]
            if al < 8:
                continue
            # Drop near-navy plate so themed mono shows glyph only.
            if r < 40 and g < 50 and b < 90 and abs(r - g) < 20:
                continue
            lum = int(0.299 * r + 0.587 * g + 0.114 * b)
            op[x, y] = (255, 255, 255, min(al, max(lum, 40)))
    return out


def main() -> None:
    src = Image.open(SRC).convert("RGBA")
    print("source", src.size, src.mode, "pad", SAFE_PAD)

    fg = fit_square(src, 432, pad_ratio=SAFE_PAD)
    fg_path = RES / "drawable-xxhdpi" / "ic_launcher_foreground.png"
    fg.save(fg_path, optimize=True)
    print("wrote", fg_path)

    mono_path = RES / "drawable-xxhdpi" / "ic_launcher_foreground_m.png"
    to_monochrome(fg).save(mono_path, optimize=True)
    print("wrote", mono_path)

    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in densities.items():
        base = fit_square(src, size, pad_ratio=SAFE_PAD)
        out = RES / folder / "ic_launcher.webp"
        base.save(out, "WEBP", quality=90)
        circle = base.copy()
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        circle.putalpha(mask)
        out_r = RES / folder / "ic_launcher_round.webp"
        circle.save(out_r, "WEBP", quality=90)
        print("wrote", out.name, out_r.name, "in", folder)

    about = fit_square(src, 192, pad_ratio=SAFE_PAD)
    about_path = RES / "drawable" / "ic_launcher.webp"
    about.save(about_path, "WEBP", quality=90)
    print("wrote", about_path)
    print("skip splash (SPEC-253: keep pre-252 assets)")
    print("done")


if __name__ == "__main__":
    main()
