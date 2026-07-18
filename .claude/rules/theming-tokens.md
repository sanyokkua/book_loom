# Theming Tokens

Scope: `:ui` styling — `**/ui/src/main/resources/**/*.css`, theme setup in `:ui`. Spec: `docs/specification/01_Product/09_THEMING.md`. Visual source of truth: `docs/specification/mockups/ui-mockup.html`. See also `javafx-ui.md`.

## MUST

- **MUST** style with **tokens only** — define **looked-up colors on `.root`** and reference them everywhere; no hard-coded hex in component selectors, no `node.setStyle(...)` in Java. — Rationale: one token set drives the whole UI and both themes.
- **MUST** derive light and dark from **one set of token roles with two value blocks** (light + dark, swapped at `.root`; dark = Charcoal base + Cognac/Sand accents), not stylesheets per component; use the full published token catalogue (`docs/specification/01_Product/09_THEMING.md#token-catalog` — every role incl. surface, border, text, primary, nav-*, status `-bg`/`-bd`, shadow, and `focus`, each with a light and a dark hex). — Rationale: themes stay in lockstep; no divergence, no invented tokens.
- **MUST** use the exact brand palette as looked-up colors: Charcoal `#3a4a52` (text, sidebar/title bar, dark base), Slate `#b2babd` (borders, muted, toggle tracks), Sand Dollar `#e7d6c0` (warm surfaces, selected rows, chips, draft), Cognac `#a58075` (primary action / brand / accent). — Rationale: matches the mockup and brand.
- **MUST** use the exact desaturated status colours: success sage `#5f8a6b`, warning ochre `#bd863a`, danger terracotta `#b0574c`, info slate-blue `#4d6b78`. — Rationale: consistent, accessible status semantics from the mockup.
- **MUST** apply stylesheets at the **`Scene` level** so tokens cascade to every node from `.root`. — Rationale: one attachment point; looked-up colors resolve globally.
- **MUST** honour the JavaFX 25 **OS colour-scheme** (`prefers-color-scheme`) to pick the initial light/dark token set. — Rationale: respects the user's system preference.

## SHOULD

- **SHOULD** name tokens by role, not by hue (e.g. `-color-accent`, `-color-surface-warm`, `-color-status-danger`), so a palette change is a value swap. — Rationale: semantic tokens survive re-theming.
- **SHOULD** validate rendered screens against the mockup for the named screen/state/theme (P6). — Rationale: the mockup is binding for visuals.
- **SHOULD** keep the accent **fixed to Cognac** (FR-THEME-4 — not user-selectable in v1) and add **no density/spacing tokens** (density is deferred; no compact mode exists in v1). — Rationale: the v1 Appearance surface is theme light/dark/system + fixed accent only.

## Reject if

- A component stylesheet hard-codes a hex value instead of referencing a `.root` looked-up color.
- Java code calls `node.setStyle("...")` to set visual styling.
- Light and dark are maintained as separate per-component stylesheets rather than one token set with swapped values.
- A stylesheet is attached at node level instead of Scene level.
- A palette or status colour deviates from the values above without a spec/mockup change.
- The initial theme ignores the OS colour-scheme.
