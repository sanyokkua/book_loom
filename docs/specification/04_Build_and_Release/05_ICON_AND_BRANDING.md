**Status:** Final **Owner:** architect **Audience:** architect, coder, tester, release management **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` (DD-29),
`docs/adr/ADR-0011-app-icon-and-branding.md`, `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md`,
`docs/specification/assets/icon/README.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`

# App Icon & Branding

The normative description of the BookLoom app icon: the canonical source artwork, the deterministic pipeline that
removes its backdrop, the single master every platform icon derives from, and how jpackage consumes the per-OS files.
Owned by **PHASE_13** (`docs/implementation_plan/phases/PHASE_13_PACKAGING_RELEASE.md`); recorded in **DD-29 /
ADR-0011**.

## Table of Contents

1. [Product name](#product-name)
2. [Canonical source artwork](#source-artwork)
3. [Processing contract — background removal](#processing-contract)
4. [Single master, per-OS derivation](#per-os-derivation)
5. [jpackage integration](#jpackage-integration)
6. [In-app usage](#in-app-usage)
7. [Edge cases (ICON)](#edge-cases)

## 1. Product name {#product-name}

The application is named **BookLoom** everywhere it is user-visible: the window title, the About dialog, the sidebar
brand, installer metadata, the macOS bundle name, the Windows Start-menu entry, and the Linux `.desktop` entry. The base
Java package is **`ua.bookloom`**; the SQLite database file is **`bookloom.db`**; the per-OS data/log directories are **
`BookLoom`** (Windows `%APPDATA%\BookLoom`, macOS `~/Library/Application Support/BookLoom`, logs
`~/Library/Logs/BookLoom`) and **`bookloom`**
(Linux `${XDG_DATA_HOME:-~/.local/share}/bookloom`). There is no other spelling or short form in persisted paths; the
sidebar mark uses the initials **BL**.

## 2. Canonical source artwork {#source-artwork}

The owner-provided icon is a **rounded glass tile** — an open book from which two speech bubbles rise, one showing the
CJK glyph **文** and one showing the Latin **A**, over a charcoal glass panel with a warm-sand book — floating on a
**solid near-black backdrop** (1254×1254, RGB, no alpha). It expresses the product: translating a book between scripts,
offline, in the app's charcoal/sand/cognac palette (`01_Product/09_THEMING.md#palette`).

- **Location:** `docs/specification/assets/icon/BookLoom-source.png`.
- **Immutability:** the source is never edited in place. A visual change replaces this file and re-runs the pipeline
  (§4).

## 3. Processing contract — background removal {#processing-contract}

The source cannot ship as-is: its near-black backdrop would render as a dark square plate behind the tile on the macOS
Dock (which expects transparent margins and applies its own squircle mask), in the Windows taskbar, and in Linux
launchers. `docs/specification/assets/icon/process_icon.py` is the **deterministic** fix (same input → same output, no
interactive knobs):

1. **Locate the tile** — the bounding box of every pixel meaningfully brighter than the backdrop (luminance threshold).
2. **Crop** to a centered square around that box.
3. **Mask** — apply a rounded-rectangle alpha mask (radius = 22.5 % of the side, matching both the artwork's corners and
   the macOS squircle) with a 2 px feather, so everything outside the tile's silhouette — including the dark corner
   remnants — becomes transparent.
4. **Resize** to 1024×1024 and export RGBA.
5. **Assert the output contract** — 1024×1024, RGBA, **alpha == 0 at all four corner pixels**; a violation fails the run
   (EC-ICON-2).

> **Why not the "keep it opaque" rule.** Icon guidance for artwork *designed to bleed edge-to-edge*
> says keep a fully opaque canvas and let the OS clip it. BookLoom's artwork is the opposite: a
> floating rounded tile on a backdrop. Its **silhouette is the icon**, so the correct move is to
> remove the backdrop and ship the tile on transparency — the pipeline above.

## 4. Single master, per-OS derivation {#per-os-derivation}

The processed output is committed as the **single master** `docs/specification/assets/icon/appicon.png`
(1024×1024 RGBA). It — not the source — is the one input every platform icon derives from.
`generate_platform_icons.py` produces them into `assets/icon/dist/`:

| Platform | Artifact(s)                                                                                       | Sizes                                                       |
|----------|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| macOS    | `dist/macos/BookLoom.iconset/` → `dist/macos/BookLoom.icns`                                       | 16, 16@2x, 32, 32@2x, 128, 128@2x, 256, 256@2x, 512, 512@2x |
| Windows  | `dist/windows/BookLoom.ico` (multi-resolution container)                                          | 16, 24, 32, 48, 64, 128, 256                                |
| Linux    | `dist/linux/BookLoom.png` (for `jpackage --icon`) + `dist/linux/hicolor/<size>/apps/bookloom.png` | 512 (primary); hicolor 16, 32, 48, 64, 128, 256, 512        |

**Never-hand-fork rule (ADR-0011).** No per-OS file may be edited or replaced independently. A change means: replace the
source, re-run `process_icon.py`, re-run `generate_platform_icons.py`, re-commit. Any other path creates cross-platform
drift.

## 5. jpackage integration {#jpackage-integration}

Packaging (`03_PACKAGING_JPACKAGE.md`) feeds jpackage the matching per-OS file via `--icon`, on the OS that builds that
installer (jpackage cannot cross-compile):

- **macOS** (`.dmg` / `.app`): `--icon dist/macos/BookLoom.icns`.
- **Windows** (portable app-image zip — no installer): `--icon dist/windows/BookLoom.ico`.
- **Linux** (`.deb`): `--icon dist/linux/BookLoom.png`; the `.desktop` entry and hicolor theme set give launchers crisp
  icons at every size.

The committed packaging scripts (`scripts/package-<os>`, `03_PACKAGING_JPACKAGE.md#approach`) select the icon by target
OS from the committed
`assets/icon/dist/` tree; the app name passed to jpackage is **BookLoom** and the version is the single Gradle project
version, so installer, About dialog, and artifact names agree (`03_PACKAGING_JPACKAGE.md#icons-metadata`).

## 6. In-app usage {#in-app-usage}

The same master seeds the runtime window/taskbar icon: `appicon.png` (or a small PNG set derived from it) is bundled as
a classpath resource and set as the JavaFX `Stage` icon (s) on startup, so the running window matches the installed-app
icon. The sidebar brand mark in the UI shell uses the initials **BL** on a cognac chip
(`01_Product/08_UI_SCREENS_AND_STATES.md#shell-and-navigation`), not a raster of the full icon, to stay crisp at small
sizes and theme-agnostic.

## 7. Edge cases (ICON) {#edge-cases}

- **EC-ICON-1** — `BookLoom-source.png` is missing → `process_icon.py` **fails fast** with a clear message; the pipeline
  never emits a master from an absent or wrong source.
- **EC-ICON-2** — the processed master violates the contract (not 1024×1024 / not RGBA / any opaque corner) →
  `process_icon.py` fails the run; a non-conforming `appicon.png` is never committed.
- **EC-ICON-3** — a per-OS icon is hand-edited so it diverges from the master → prohibited by the never-hand-fork rule;
  the fix is to regenerate from `appicon.png`, not to patch the platform file.
- **EC-ICON-4** — a build host lacks `iconutil` (e.g. Linux CI) → the reproducible Pillow `.icns`
  writer in `generate_platform_icons.py` is used; a macOS host may instead compile the emitted iconset with `iconutil`
  for the canonical result. Both derive from the same master.
