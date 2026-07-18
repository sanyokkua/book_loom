# UI Mockup — Source of Truth

`ui-mockup.html` is the **binding visual and interaction source of truth** for the application UI. It is a
self-contained, clickable, low-fidelity-but-complete prototype: open it in any browser.

Where the written specification and the mockup ever appear to diverge on a *visual or interaction* detail, **the mockup
wins** and the spec clause is corrected. Where they diverge on *behaviour or data*, the functional spec
(`../01_Product/`) wins. UI stories cite the mockup as their visual acceptance reference using acceptance-criteria
pattern **P6** (see `../../implementation_plan/05_ACCEPTANCE_CRITERIA_PATTERNS.md`).

**How "binding" is enforced.** The binding contract is tiered: **structure, design tokens, and geometry** (widget
presence/hierarchy, token values, spacing/radii/size relationships) are **CI-gated** — a mismatch fails the build.
**Pixel fidelity** is *not* a per-commit gate: it is checked by a **nightly / on-demand tolerant screenshot diff**
against mockup renders, with a small tolerance for font rasterisation and platform differences.

## What it defines

- **Palette** (applied as looked-up colors on `.root`): Charcoal `#3a4a52`, Slate `#b2babd`, Sand Dollar `#e7d6c0`,
  Cognac `#a58075` (primary/accent), plus desaturated functional status colours (success `#5f8a6b`, warning `#bd863a`,
  danger `#b0574c`, info `#4d6b78`). Light **and** dark themes from **one set of token roles, two value blocks (
  light/dark)** — see `../01_Product/09_THEMING.md`.
- **Every screen and state:** Projects (+ empty), Import (detected-OK / language-mismatch / DRM-blocked / unsupported),
  Book Brief, Structure, Names & Style (glossary), Translating (running / paused / stopped / provider-error), Review
  queue (side-by-side, no diff), Export, and Settings (Providers / Models / Generation / Appearance / Automation /
  Storage & logs).
- **Every dialog:** welcome, add/edit provider (with the Test connection / models / inference trio), add glossary term,
  retry-with-note, confirm-delete, unsaved-changes, error-with-details, export-complete, the resume-binding pair
  (bound-provider-unavailable / provider-model-changed), about.
- **Notifications & inline states:** toasts (ok/info/warn/err), banners, empty states, and a component library that
  names the **JavaFX control** each widget maps to.

## JavaFX implementability

Every widget in the mockup maps to a real JavaFX control (labelled in the component library): segmented pickers →
`SegmentedButton`/`ToggleGroup`; side-by-side panes → two `TextArea`; tables → `TableView`; chapter tree → `TreeView`;
toggles → `ToggleSwitch`; tabs → `TabPane`; dialogs → `Dialog`/`Alert`; toasts → a `Notifications`-style overlay;
icons → Ikonli. Nothing in the mockup requires a capability JavaFX lacks. The written mapping and control list live in
`../01_Product/08_UI_SCREENS_AND_STATES.md` and `../02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`.
