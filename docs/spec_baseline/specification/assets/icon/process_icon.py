#!/usr/bin/env python3
"""Deterministic BookLoom app-icon pipeline (DD-29, ADR-0011).

Takes the canonical "book + speech-bubble" glass-tile artwork (a square PNG whose
rounded glass tile floats on a solid near-black backdrop) and produces the
1024x1024 transparent-background master `appicon.png` that every per-OS icon
(.icns / .ico / .png) is derived from — never hand-forked.

Steps (same input -> same output, no interactive knobs):
  1. Locate the tile: scan for pixels meaningfully brighter than the near-black
     backdrop (luminance threshold) and take their bounding box.
  2. Crop to a square around that box (the rounded glass tile, edge glow included).
  3. Apply a rounded-rectangle alpha mask (radius = CORNER_RATIO * side) so
     everything outside the tile silhouette — including the dark corner remnants —
     is transparent.
  4. Fit onto a square transparent canvas and resize to 1024x1024 (LANCZOS).
  5. Assert the output contract: 1024x1024 RGBA, alpha == 0 at all four corners.

Usage:
  python3 process_icon.py BookLoom-source.png appicon.png
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

LUMA_THRESHOLD = 24      # backdrop is near-black (~#000000); tile edges glow well above this
CORNER_RATIO = 0.225     # rounded-corner radius as a fraction of the tile side (matches macOS squircle + the artwork)
OUT_SIZE = 1024
EDGE_SOFTEN_PX = 2       # slight mask feather so the silhouette edge is not aliased


def fail(msg: str) -> None:
    print(f"process_icon: ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: process_icon.py <BookLoom-source.png> <output appicon.png>")
    src_path, out_path = Path(sys.argv[1]), Path(sys.argv[2])
    if not src_path.is_file():
        fail(f"source not found: {src_path} — place the owner-provided artwork here first (see README.md)")

    im = Image.open(src_path).convert("RGBA")

    # 1. Bounding box of everything brighter than the backdrop.
    luma = im.convert("L")
    mask = luma.point(lambda p: 255 if p > LUMA_THRESHOLD else 0)
    bbox = mask.getbbox()
    if bbox is None:
        fail("could not find the tile (image entirely below luminance threshold)")

    # 2. Crop to the tile, squared around the longer side, centered.
    left, top, right, bottom = bbox
    w, h = right - left, bottom - top
    side = max(w, h)
    cx, cy = left + w // 2, top + h // 2
    half = side // 2
    box = (max(cx - half, 0), max(cy - half, 0),
           min(cx + half, im.width), min(cy + half, im.height))
    tile = im.crop(box)
    side = min(tile.size)
    tile = tile.crop((0, 0, side, side))

    # 3. Rounded-rect alpha mask -> transparent outside the silhouette.
    radius = int(side * CORNER_RATIO)
    alpha = Image.new("L", (side, side), 0)
    ImageDraw.Draw(alpha).rounded_rectangle((0, 0, side - 1, side - 1), radius=radius, fill=255)
    if EDGE_SOFTEN_PX:
        alpha = alpha.filter(ImageFilter.GaussianBlur(EDGE_SOFTEN_PX))
    tile.putalpha(alpha)

    # 4. Resize to the canonical size.
    out = tile.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)

    # 5. Output contract.
    if out.size != (OUT_SIZE, OUT_SIZE) or out.mode != "RGBA":
        fail("output contract violated (size/mode)")
    for corner in [(0, 0), (OUT_SIZE - 1, 0), (0, OUT_SIZE - 1), (OUT_SIZE - 1, OUT_SIZE - 1)]:
        if out.getpixel(corner)[3] != 0:
            fail(f"output contract violated: corner {corner} is not transparent")

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path, "PNG")
    print(f"process_icon: wrote {out_path} ({OUT_SIZE}x{OUT_SIZE} RGBA, transparent corners)")


if __name__ == "__main__":
    main()
