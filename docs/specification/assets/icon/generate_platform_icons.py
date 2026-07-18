#!/usr/bin/env python3
"""Derive every per-OS BookLoom icon from the single processed master (DD-29, ADR-0011).

Input : appicon.png  (1024x1024 RGBA, transparent corners — produced by process_icon.py)
Output: dist/
  macos/BookLoom.iconset/*      the 10 Apple iconset PNGs
  macos/BookLoom.icns           compiled macOS icon (Pillow ICNS writer; on macOS use
                                 `iconutil --convert icns BookLoom.iconset -o BookLoom.icns`)
  windows/BookLoom.ico          multi-resolution Windows icon (16..256)
  linux/BookLoom.png            512x512 icon for jpackage --icon
  linux/hicolor/<size>/apps/bookloom.png   freedesktop hicolor theme set

Never hand-fork a platform file: change the source artwork, re-run process_icon.py,
then re-run this script. Same master -> same platform icons.

Usage:
  python3 generate_platform_icons.py appicon.png dist
"""

import sys
from pathlib import Path

from PIL import Image

# Apple .iconset — (filename, pixel size)
ICONSET = [
    ("icon_16x16.png", 16),
    ("icon_16x16@2x.png", 32),
    ("icon_32x32.png", 32),
    ("icon_32x32@2x.png", 64),
    ("icon_128x128.png", 128),
    ("icon_128x128@2x.png", 256),
    ("icon_256x256.png", 256),
    ("icon_256x256@2x.png", 512),
    ("icon_512x512.png", 512),
    ("icon_512x512@2x.png", 1024),
]
ICO_SIZES = [16, 24, 32, 48, 64, 128, 256]
HICOLOR_SIZES = [16, 32, 48, 64, 128, 256, 512]


def fail(msg: str) -> None:
    print(f"generate_platform_icons: ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def resized(master: Image.Image, size: int) -> Image.Image:
    return master.resize((size, size), Image.LANCZOS)


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: generate_platform_icons.py <appicon.png> <out dir>")
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    if not src.is_file():
        fail(f"master not found: {src} — run process_icon.py first")
    master = Image.open(src).convert("RGBA")
    if master.size != (1024, 1024):
        fail(f"master must be 1024x1024, got {master.size}")

    # macOS iconset + .icns
    iconset = out / "macos" / "BookLoom.iconset"
    iconset.mkdir(parents=True, exist_ok=True)
    for name, size in ICONSET:
        resized(master, size).save(iconset / name)
    try:
        master.save(out / "macos" / "BookLoom.icns")
        icns_note = "BookLoom.icns (Pillow)"
    except Exception as exc:  # pragma: no cover - platform-dependent
        icns_note = f"iconset only (Pillow ICNS writer unavailable: {exc}; run iconutil on macOS)"

    # Windows .ico
    (out / "windows").mkdir(parents=True, exist_ok=True)
    master.save(out / "windows" / "BookLoom.ico", sizes=[(s, s) for s in ICO_SIZES])

    # Linux single PNG for jpackage --icon
    (out / "linux").mkdir(parents=True, exist_ok=True)
    resized(master, 512).save(out / "linux" / "BookLoom.png")

    # Linux freedesktop hicolor theme set
    for size in HICOLOR_SIZES:
        d = out / "linux" / "hicolor" / f"{size}x{size}" / "apps"
        d.mkdir(parents=True, exist_ok=True)
        resized(master, size).save(d / "bookloom.png")

    print("generate_platform_icons: wrote")
    print(f"  macos/   BookLoom.iconset ({len(ICONSET)} sizes) + {icns_note}")
    print(f"  windows/ BookLoom.ico ({','.join(map(str, ICO_SIZES))})")
    print(f"  linux/   BookLoom.png (512) + hicolor set ({','.join(str(s) for s in HICOLOR_SIZES)})")


if __name__ == "__main__":
    main()
