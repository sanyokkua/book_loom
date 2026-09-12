# Layout, Motion & Accessibility

Supporting reference for `javafx-ui-designer/SKILL.md`. Pinned platform: **JavaFX 26** (`org.openjfx:javafx-*:26.0.2`,
ADR-0019 — supersedes the spec's "JavaFX 25" mentions; every such mention elsewhere in `docs/specification/`
is read through that ADR). CSS `@media` feature queries were introduced in JavaFX 25 itself (viewport `width`/
`height`/`aspect-ratio`/`orientation`/`display-mode`, plus the user-preference features `prefers-color-scheme`/
`prefers-reduced-motion`/`prefers-reduced-data`/`prefers-reduced-transparency`), so they are fully available on
BookLoom's JavaFX 26 runtime — this is real, current-toolkit capability, not a forward-looking note.

## Responsive / adaptive layout

Set a sane floor so the window can't be resized into an unusable state:

```java
stage.setMinWidth(960);
stage.setMinHeight(640);
```

For purely **stylistic** breakpoints — colors, padding, font size, anything expressible as a `-fx-*` property —
prefer a CSS `@media` width query over a Java listener; it stays declarative and colocated with the rest of the
theme CSS:

```css
.sidebar-label {
    -fx-font-size: 13px;
}
@media (width < 880px) {
    .sidebar-label {
        -fx-font-size: 12px;
    }
}
```

CSS has no property for `Node.managed` — pulling a node out of layout entirely (not just hiding it) is a Java-side
operation, so **structural** adaptive behavior (e.g. actually collapsing a sidebar so its space is reclaimed) still
needs a `scene.widthProperty()` listener rather than `@media`:

```java
scene.widthProperty().addListener((obs, oldW, newW) -> {
    boolean collapsed = newW.doubleValue() < 880;
    sidebar.setVisible(!collapsed);
    sidebar.setManaged(!collapsed);
});
```

(Note the `setVisible`/`setManaged` pairing from `SKILL.md`'s Gotchas — collapsing a sidebar with only `setVisible`
leaves its layout space reserved.) Use the CSS query for what CSS can express and the listener only for what it
can't — don't reach for the listener out of habit once a pure style swap would do.

## CSS transitions

Implicit transitions on pseudo-class state changes (e.g. `:hover`, `:focused`) were introduced in JavaFX 23 via
`transition-property`/`transition-duration`/`transition-timing-function` (shorthand `transition:`), so they are
available on BookLoom's JavaFX 26:

```css
.button {
    -fx-background-color: -color-surface;
    transition: -fx-background-color 0.2s ease;
}
.button:hover {
    -fx-background-color: derive(-color-surface, -5%);
}
```

Guidance:

- **100–300ms** for ordinary UI state changes (hover, focus-enter, selection).
- `ease-out` for a hover/focus **entering** transition; `ease-in` for the corresponding **exit**.
- Prefer transitioning `opacity`/`-fx-background-color` over a layout-affecting property (`-fx-pref-width`,
  `-fx-padding`, …) — layout properties trigger a relayout pass per frame and read as janky rather than smooth.

`prefers-reduced-motion` is one of the `@media` user-preference features JavaFX CSS supports directly (alongside
`prefers-color-scheme`, `prefers-reduced-data`, `prefers-reduced-transparency`) — it reads the real OS setting, not
an app-guessed proxy. Gate a transition's duration on it directly in the stylesheet rather than skipping the
feature:

```css
.button {
    transition: -fx-background-color 0.2s ease;
}
@media (prefers-reduced-motion: reduce) {
    .button {
        transition: -fx-background-color 0s;
    }
}
```

This is a real capability of BookLoom's pinned JavaFX 26, not the "advisory gap, no direct API" situation an older
JavaFX pin would have — use it. Accessibility checks remain advisory, not a merge gate, per `testing.md`; that
governs how the *check* is enforced, not whether the *feature* exists.

## Accessibility techniques

- **`Node.setAccessibleText(...)`** on any icon-only button or bare `ImageView` — a screen reader has nothing else to
  announce for a control with no visible label.
- **Mnemonics and accelerators** for keyboard operability: an underscore in a `Button`/`MenuItem` text
  (`"_Save"` → Alt+S on the platforms that support mnemonics) and `KeyCombination`-based `accelerator` properties
  for global shortcuts.
- **Minimum interactive-target size ~32×32px** for anything clickable — smaller targets are hard to hit accurately,
  especially with trackpads and touch.
- **A `Tooltip` on every icon-only or non-obvious control** — the same information `setAccessibleText` gives a
  screen reader, given to a sighted mouse user who pauses over the control.
- **WCAG AA contrast reference numbers** for anyone hand-picking a `derive()` shade against an existing token: body
  text ≥ 4.5:1, large text (≥18pt or ≥14pt bold) ≥ 3:1. Check a derived shade against both the light and dark token
  values it's layered on, not just one.

## Design Audit Checklist

A reusable pre-merge scan across four dimensions. This **supplements** the existing P6 mockup-conformance check in
`javafx-ui.md`/`theming-tokens.md` — it does not replace it; P6 remains the binding acceptance gate against the
mockup.

- **Color & contrast** — every color is a looked-up token (no hex in a selector, no `setStyle`); AA contrast holds
  for body and large text in both themes; status colors map to the correct `ok`/`warn`/`err`/`info` role, not an
  arbitrary token.
- **Typography** — no ad hoc `-fx-font-size` literal outside the existing catalogue's implied scale; heading vs.
  body distinction is consistent with sibling screens.
- **Spacing & layout** — consistent `-fx-spacing`/`-fx-padding` values across sibling containers (v1 has no spacing
  token scale — see `css-control-patterns.md`'s exclusions — so "consistent," not "tokenized," is the bar); no
  `AnchorPane` pixel-pinning where a resizable pane would do; grow-priority set on the one child meant to absorb
  extra space.
- **Interactivity** — hover/focus/disabled/pressed states are all visibly distinct; `-fx-cursor: hand` on
  clickable-but-non-button nodes; interactive targets meet the ~32×32px minimum; every icon-only control has a
  `Tooltip` and `setAccessibleText`.
- **Consistency** — the screen reuses the mapped controls from `SKILL.md`'s control-mapping table rather than a
  bespoke substitute; new style classes follow existing naming (`primary`/`danger`/`ghost`, `error`, `row-flagged`)
  rather than inventing a parallel vocabulary.
