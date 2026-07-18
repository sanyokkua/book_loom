# ADR-0011 — App icon & branding: one background-removed master, per-OS derivation

**Status:** accepted **Date:** 2026-07-17 **Deciders:** architect

## Context and problem statement

The product needs an identity — a name and an application icon — that renders correctly across macOS, Windows, and Linux
and is reproducible as part of the build. The owner supplied a finished piece of artwork: a rounded **glass tile**
depicting an open book with two speech bubbles (文 → A) in the app's charcoal/sand/cognac palette. The catch is that the
artwork is delivered as a tile **floating on a solid near-black backdrop** (1254×1254, RGB, no alpha).

Two things must be settled. First, the product name and where it appears (window, installers, package names, data
directories) so nothing carries an inconsistent spelling. Second — and the substantive part — how to turn one piece of
artwork into the three platform icon formats jpackage needs (`.icns`, `.ico`, `.png`) without the backdrop becoming a
visible dark plate behind the icon, and without the three platforms drifting apart over time.

The naïve path (hand-export three files in an image editor, keep the opaque backdrop) fails on both counts: macOS masks
app icons to a squircle and expects transparent margins, so the near-black square would show as a plate on the Dock; and
three independently edited files drift the moment anyone touches one.

## Decision drivers

- **Cross-platform correctness.** The icon must look right on the macOS Dock (squircle mask), the Windows
  taskbar/Explorer, and Linux launchers — no dark plate, crisp at every size.
- **One source of truth.** All platform icons must derive from a single processed image so they can never disagree.
- **Reproducibility.** Regenerating the icons must be deterministic and runnable in CI, on any OS, with no manual
  image-editor steps.
- **Offline / no new dependencies of substance.** A tiny Python + Pillow pipeline committed in-repo, not an online icon
  service.
- **Consistent naming.** One product name and one set of persisted identifiers, everywhere.

## Considered options

- **Option A — Ship the artwork as-is; hand-export per-OS files.** Keep the near-black backdrop, export `.icns`/`.ico`/
  `.png` by hand in an image editor.
- **Option B — Keep an opaque, edge-to-edge canvas ("fill the canvas, let the OS clip").** Treat the artwork as a
  full-bleed icon and rely on each OS to mask it.
- **Option C — Deterministic background removal to one master, then derive per-OS.** A committed script crops the tile
  off the backdrop into a single 1024×1024 RGBA master with transparent corners; a second script derives every platform
  file from that master; jpackage consumes them.

## Decision outcome

Chosen: **Option C.** The app is named **BookLoom** in every user-visible and persisted location (window title, About
dialog, sidebar brand `BL`, installer/bundle metadata, base package
`ua.bookloom`, database `bookloom.db`, data/log dirs `BookLoom`/`bookloom`). The icon pipeline is two deterministic,
committed scripts under `docs/specification/assets/icon/`:

1. `process_icon.py` locates the glass tile by luminance, crops to it, applies a rounded-rectangle alpha mask (22.5 %
   corner radius, matching the artwork and the macOS squircle), and writes the single master `appicon.png` (1024×1024
   RGBA), asserting transparent corners.
2. `generate_platform_icons.py` derives `BookLoom.icns` (+ the 10-size Apple iconset), the multi-resolution
   `BookLoom.ico`, and the Linux `BookLoom.png` plus the freedesktop `hicolor` set — all from that one master.

jpackage is fed the matching file per OS on the OS that builds that installer. No per-OS icon is ever hand-forked: a
visual change replaces the source artwork and re-runs both scripts. The normative description lives in
`docs/specification/04_Build_and_Release/05_ICON_AND_BRANDING.md`.

Option B is explicitly rejected for *this* artwork: "keep it opaque and let the OS clip" is correct for art designed to
bleed edge-to-edge, but BookLoom's artwork is a floating tile whose silhouette is the icon — keeping the backdrop opaque
would ship the dark plate the mask is meant to remove.

### Consequences

Positive:

- The icon renders correctly on all three platforms from day one, with no dark plate.
- All platform icons provably agree — they are functions of one committed master.
- Regeneration is deterministic and CI-runnable on any OS (Pillow `.icns` writer), with `iconutil`
  as the canonical macOS route when a macOS host is available.
- One product name and identifier set everywhere; no drift between UI, installers, and paths.

Negative:

- A visual change is a pipeline step (replace source, run two scripts, re-commit), not an in-editor tweak —
  deliberately, to prevent drift.
- The build depends on Python 3 + Pillow being available where icons are (re)generated.

Neutral:

- The processed master intentionally ships as a floating tile with transparent corners; macOS applies its squircle on
  top, which is the intended look for glass-tile iconography.

## Links

- Design decisions: DD-29 (`docs/specification/00_Foundation/04_DESIGN_DECISIONS.md#dd-29-app-icon-and-branding`)
- Spec clauses: `docs/specification/04_Build_and_Release/05_ICON_AND_BRANDING.md`,
  `docs/specification/04_Build_and_Release/03_PACKAGING_JPACKAGE.md#icons-metadata`,
  `docs/specification/assets/icon/README.md`
- Related: DD-24 / jpackage per-OS matrix (`#dd-24-jpackage-unsigned`)
- Stories: none yet
