---
paths:
  - "modules/ui/src/main/resources/**/*.css"
---

# Theming Tokens

Scope: `:ui` styling — `**/ui/src/main/resources/**/*.css`, theme setup in `:ui`. Spec: `docs/specification/01_Product/09_THEMING.md`. Visual source of truth: `docs/specification/mockups/ui-mockup.html`. See also `javafx-ui.md`.

## MUST

- **MUST** style with **tokens only** — define **looked-up colors on `.root`** and reference them everywhere; no hard-coded hex in component selectors, no `node.setStyle(...)` in Java. — Rationale: one token set drives the whole UI and both themes.
- **MUST** derive light and dark from **one set of token roles with two value blocks** (light + dark, swapped at `.root`; dark = Charcoal base + Cognac/Sand accents), not stylesheets per component; use the full published token catalogue (`docs/specification/01_Product/09_THEMING.md#token-catalog` — every role incl. surface, border, text, primary, nav-*, status `-bg`/`-bd`, shadow, and `focus`, each with a light and a dark hex or rgba value — `selected` and `nav-active-bg` are rgba). **Carve-out (design D12):** the three elevation roles `shadow-sm`/`shadow`/`shadow-lg` are **not** looked-up colours — they are drop-shadow effects (`-fx-effect: dropshadow(...)`) on the `.elevation-sm`/`.elevation`/`.elevation-lg` style classes (descendant selectors `.root .elevation*` for light and `.root.theme-dark .elevation*` for dark), each with a light and a dark value; a looked-up colour holding a box-shadow triple is silently dropped by JavaFX and the element renders with no background. — Rationale: themes stay in lockstep; no divergence, no invented tokens.
- **MUST** use the exact brand palette as looked-up colors: Charcoal `#3a4a52` (text, sidebar/title bar, dark base), Slate `#b2babd` (borders, scroll thumbs; muted text and the switch's off track take darker slate roles, `muted` and `toggle-off`, for contrast), Sand Dollar `#e7d6c0` (warm surfaces, selected rows, chips, draft), Cognac `#a58075` (primary action / brand / accent; its words are Charcoal `primary-fg`, since white on Cognac measures 3.5:1). — Rationale: matches the mockup and brand.
- **MUST** use the exact desaturated status colours: success sage `#5f8a6b`, warning ochre `#bd863a`, danger terracotta `#b0574c`, info slate-blue `#4d6b78` — for **fills, edges and icons**. A status shown as **words** (a chip, a log tag, a banner glyph, a diff, the danger button's label) uses the text role `-color-<status>-fg`, never the brand hue, which measures under 4.5:1 as text; on the title bar it uses `-color-title-ok-fg`/`-color-title-warn-fg`. — Rationale: consistent status semantics from the mockup, readable as text.
- **MUST** keep every rendered pair at WCAG AA (`09_THEMING.md#contrast`, enforced by `ContrastTest`): text 4.5:1 against the surface it is painted on, icons and control edges (inputs take `-color-input-border`, the switch's tracks `-color-toggle-off`/`-color-toggle-on`) 3:1, informative text at least 12px (a badge repeating adjacent words 11px). Compute a new token value against the worst surface it sits on, both blocks, before adding it. — Rationale: the palette is chosen for contrast, not assumed.
- **MUST** apply stylesheets at the **`Scene` level** so tokens cascade to every node from `.root`. — Rationale: one attachment point; looked-up colors resolve globally.
- **MUST** honour the JavaFX 25 **OS colour-scheme** (`prefers-color-scheme`) to pick the initial light/dark token set. — Rationale: respects the user's system preference.

## SHOULD

- **SHOULD** name tokens by role, not by hue (e.g. `-color-primary`, `-color-sand-soft`, `-color-err`), so a palette change is a value swap. — Rationale: semantic tokens survive re-theming.
- **SHOULD** validate rendered screens against the mockup for the named screen/state/theme (P6). — Rationale: the mockup is binding for visuals.
- **SHOULD** keep the accent **fixed to Cognac** (FR-THEME-4 — not user-selectable in v1) and add **no density/spacing tokens** (density is deferred; no compact mode exists in v1). — Rationale: the v1 Appearance surface is theme light/dark/system + fixed accent only.

## Reject if

- A component stylesheet hard-codes a hex value instead of referencing a `.root` looked-up color.
- Java code calls `node.setStyle("...")` to set visual styling.
- Light and dark are maintained as separate per-component stylesheets rather than one token set with swapped values.
- A stylesheet is attached at node level instead of Scene level.
- A palette or status colour deviates from the values above without a spec/mockup change.
- A status brand hue (`-color-ok`/`-warn`/`-err`/`-info`) is used as a text fill, or a new token pair falls under the contrast floors.
- The initial theme ignores the OS colour-scheme.
