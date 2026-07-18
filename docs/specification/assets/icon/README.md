# App icon assets (DD-29, ADR-0011)

The canonical BookLoom icon source and its deterministic processing pipeline. Normative doc:
`../../04_Build_and_Release/05_ICON_AND_BRANDING.md`; decision: DD-29 (ADR-0011).

## Files

| File                         | Role                                                                                                                                                                                                                                                                     |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `BookLoom-source.png`        | **Canonical source** — the owner-provided artwork exactly as supplied (a rounded glass tile — an open book with two speech bubbles, 文 → A — floating on a solid near-black backdrop, 1254×1254, RGB). Never edited in place.                                            |
| `process_icon.py`            | **Step 1 — deterministic background removal.** Crops the tile out of the dark backdrop, makes everything outside the tile's rounded-rect silhouette transparent, and exports the 1024×1024 RGBA master `appicon.png`. Re-runnable at any time; same input → same output. |
| `appicon.png`                | **The single master** every per-OS icon is derived from (1024×1024, RGBA, fully transparent corners). Committed output of `process_icon.py`. No platform icon is ever hand-forked from a different source.                                                               |
| `generate_platform_icons.py` | **Step 2 — per-OS derivation.** From `appicon.png` produces `dist/macos/` (`BookLoom.iconset` + `BookLoom.icns`), `dist/windows/BookLoom.ico`, and `dist/linux/` (`BookLoom.png` + the freedesktop `hicolor` set).                                                       |
| `dist/`                      | Generated per-OS icons. Regenerated from `appicon.png`; not hand-edited.                                                                                                                                                                                                 |

## Reproducing the assets

```bash
# From this directory:
python3 process_icon.py BookLoom-source.png appicon.png      # step 1: remove backdrop -> 1024² master
python3 generate_platform_icons.py appicon.png dist          # step 2: derive .icns / .ico / .png
```

Requires Python 3 with Pillow (`pip install pillow`).

**macOS `.icns` note.** `generate_platform_icons.py` writes a valid `.icns` via Pillow so the assets are reproducible on
any OS (including CI/Linux). On a macOS build host the canonical, Apple-blessed route is to compile the emitted iconset
instead:

```bash
iconutil --convert icns dist/macos/BookLoom.iconset -o dist/macos/BookLoom.icns
```

## Output contracts (asserted by the scripts)

- `appicon.png`: 1024×1024, RGBA, **alpha == 0 at all four corner pixels** (no dark plate ships).
- `dist/windows/BookLoom.ico`: multi-resolution — 16, 24, 32, 48, 64, 128, 256.
- `dist/macos/BookLoom.iconset`: the 10 Apple sizes (16²…512@2x); `.icns` compiled from them.
- `dist/linux/BookLoom.png`: 512×512 for `jpackage --icon`; `hicolor/<size>/apps/bookloom.png` for theme integration
  (16, 32, 48, 64, 128, 256, 512).

## Why processing is required

The provided artwork sits on a solid near-black backdrop. Shipping it unprocessed would give the app a visible dark
square plate behind the rounded tile — on the macOS Dock (which expects transparent margins and applies its own squircle
mask), in the Windows taskbar, and in Linux launchers. The pipeline removes the backdrop so only the rounded glass tile
ships, on transparency. This is the opposite of the "fill the canvas, keep it opaque" rule that applies to artwork
*designed* to bleed edge-to-edge; BookLoom's artwork is a floating tile, so its silhouette is the icon.

## Never hand-fork a platform file

A visual change to the icon means: replace `BookLoom-source.png`, re-run `process_icon.py`, re-run
`generate_platform_icons.py`, re-commit. Editing a single `.icns`/`.ico`/`.png` by hand creates drift between platforms
and is prohibited (ADR-0011).
