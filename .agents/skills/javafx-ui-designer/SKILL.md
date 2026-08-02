---
name: javafx-ui-designer
description: >-
  Use when building or editing a JavaFX screen or dialog for BookLoom so it
  matches `docs/specification/mockups/ui-mockup.html`. Covers FXML views with a Guice
  controller factory, the `ViewNames` enum + `Navigator`, token-only theming (the fixed
  palette), the observable state mirror and `Platform.runLater`, mapping mockup widgets
  to real JavaFX/ControlsFX controls (SegmentedButton, TableView, TreeView, ToggleSwitch,
  TabPane, Dialog/Alert, Notifications, Ikonli), and the TestFX visual reference.
allowed-tools: Read, Write, Bash, Glob, Grep
---

# JavaFX UI Designer

`:ui` (with `:app`) is the only module that `requires javafx.*`. It is an MVVM front-end
over the FX-free core, driven by an observable state mirror. The mockup at
`docs/specification/mockups/ui-mockup.html` is the binding visual acceptance reference
(pattern P6): every screen, state, and dialog must map to it.

## When to use

- Building a new screen or dialog listed in
  `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md` and the mockup.
- Adding a state to an existing screen (e.g. Import `language-mismatch`, Translating
  `paused`, `provider-error`).
- Wiring a view to core progress/state through the state mirror.
- Adding or adjusting a control, a toast/banner, or a theme token.

## When NOT to use

- Do NOT put business logic in `:ui`. Translation, parsing, provider, and persistence
  logic live in the FX-free core and are reached only through `:api` ports and `:pipeline`.
- Do NOT `requires javafx.*` from any core module — ArchUnit `fx-free-core` fails the build.
- Do NOT invent screens, states, widgets, or colors not in the mockup. The mockup is the
  source of truth; deviations fail the P6 visual reference.
- Do NOT hard-code hex colors in FXML/CSS — use looked-up color tokens only.
- Do NOT touch the scene graph from a worker thread — go through the state mirror.

## Workflow

1. **Read the mockup and the spec screen.** Open `docs/specification/mockups/ui-mockup.html`
   for the target screen/state, and its clause in
   `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md` plus the UI architecture in
   `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`.
2. **Create the View (FXML + thin controller).** The controller holds node references and
   forwards gestures only — no business logic. It binds nodes to the ViewModel's
   observable properties.
3. **Wire the ViewModel.** A `@Singleton`-or-per-view class exposing JavaFX
   `Property`/`ObservableList` state and command methods. A ViewModel holds NO
   `javafx.scene` node references (no `Button`, `TableView`, `Scene`) — only properties,
   observable collections, and calls to `:api` ports / `:pipeline`, so it is unit-testable
   without a scene graph.
4. **Register the view.** Add a constant to the `ViewNames` enum (carrying its FXML path)
   and route through the `@Singleton` `Navigator`, which swaps the root content region.
   Screen states (e.g. Import → language-mismatch) are driven by view state, not separate
   FXML files.
5. **Guice-construct the controller.** Rely on
   `FXMLLoader.setControllerFactory(injector::getInstance)` so every controller gets its
   ViewModel and ports injected by constructor. Add `opens ua.bookloom.ui.view to
   javafx.fxml, com.google.guice;` if a new package holds controllers.
6. **Bind to the state mirror for live data.** Core progress/job state is pushed into the
   `@Singleton` state mirror via `publish*` methods (`publishSegmentAccepted`,
   `publishJobState`, `publishToast`) that wrap `Platform.runLater` internally. The
   pipeline calls plain methods off the FX thread; the mirror marshals every mutation onto
   the FX Application Thread. Views bind to the mirror's properties; they never poll.
7. **Map each widget to a real control** (see table). Use ControlsFX for `ToggleSwitch`,
   `SegmentedButton`, and `Notifications`; Ikonli for icons.
8. **Theme with tokens only.** Apply token-only CSS at the Scene level: **one set of token
   roles with two value blocks** (light + dark) swapped at `.root`, per the full published
   token catalogue in `docs/specification/01_Product/09_THEMING.md#token-catalog` (never
   invent a token); JavaFX 25 reads OS `prefers-color-scheme`. The accent is **fixed to
   Cognac** (FR-THEME-4, not user-selectable) and there is **no density/compact mode** in
   v1. AtlantaFX supplies the base control theme; app tokens override brand colors.
   Controls reference `-color-accent`, `-color-bg-surface`, … never hex.
9. **Add i18n.** All labels are bundle keys addressed through the **typed message-key
   registry** (no bare string literals at call sites); plural/gender-sensitive strings are
   **ICU4J `MessageFormat` patterns** in the bundles (UK needs one/few/many/other — DD-48);
   counts/sizes/durations/dates format via `NumberFormat`/`java.time` for the **injectable
   `Locale` provider**'s locale; no user text concatenated into layout; the Appearance tab
   selects app language independent of the translation target.
10. **Prove it with TestFX.** Add a TestFX + Monocle (headless) control-state assertion
    (P4) and, where practical, a screenshot matching the mockup screen/state/theme (P6).

## Control mapping (mockup -> JavaFX)

| Mockup element | Control |
|---|---|
| segmented pickers | `SegmentedButton` / `ToggleGroup` |
| side-by-side compare panes | two `TextArea` (no diff) |
| tables (glossary, flagged queue) | `TableView` (virtualized) |
| chapter / structure tree | `TreeView` |
| toggles | ControlsFX `ToggleSwitch` |
| tabbed settings | `TabPane` |
| dialogs | `Dialog` / `Alert` |
| toasts | ControlsFX `Notifications` (ok/info/warn/err) |
| icons | Ikonli |

## Palette (looked-up color tokens on `.root`)

- Charcoal `#3a4a52` — text, sidebar/title bar, dark base
- Slate `#b2babd` — borders, muted, toggle tracks
- Sand Dollar `#e7d6c0` — warm surfaces, selected rows, chips, draft
- Cognac `#a58075` — primary action / brand / accent (fixed in v1, FR-THEME-4)
- Status (desaturated): success sage `#5f8a6b` · warning ochre `#bd863a` ·
  danger terracotta `#b0574c` · info slate-blue `#4d6b78`

The brand hexes above are the light-block anchors; the normative per-role light AND dark
values (incl. surface, nav-*, status `-bg`/`-bd`, shadow, `focus`) are the token catalogue:
`docs/specification/01_Product/09_THEMING.md#token-catalog`.

## Reference index

- In-repo authorities: `docs/specification/mockups/ui-mockup.html` (visual truth),
  `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
  `docs/specification/01_Product/09_THEMING.md`,
  `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`,
  `docs/specification/02_Architecture/08_THREADING_CONCURRENCY.md`.

## Mandatory validation checklist

- [ ] Screen/state/dialog matches the named mockup element (P6), including empty states.
- [ ] Controller is thin (nodes + gestures only); ViewModel holds no scene-graph nodes.
- [ ] Controller is Guice-constructed via the controller factory; package `opens` set.
- [ ] Every widget uses the mapped control (SegmentedButton/TableView/TreeView/
      ToggleSwitch/TabPane/Dialog/Notifications/Ikonli).
- [ ] All colors are looked-up tokens; no hard-coded hex; light + dark both render.
- [ ] Live data flows through the state mirror; no scene-graph access off the FX thread.
- [ ] Labels use the typed message-key registry; plural/gender strings are ICU patterns
      (DD-48); no concatenated user strings.
- [ ] New view has a `ViewNames` constant and is reachable via the `Navigator`.
- [ ] TestFX (Monocle headless) P4 control-state assertion, plus P6 screenshot where
      practical; ArchUnit `fx-free-core` still green.

## Gotchas

- Long work (translation, verification) must run off the FX thread via `Task`/`Service`;
  results land on the UI only through the state mirror's `Platform.runLater` wrapper.
- Provider errors surface as an error-with-details dialog (expandable typed
  `AppError.details`) plus a toast — do not swallow them into a label.
- The add/edit-provider dialog's Test connection/models/inference trio maps to the
  three-stage verification in `docs/specification/02_Architecture/04_LLM_INTEGRATION.md`;
  run it against the DRAFT config, not the saved one.
- Screen states are view state, not separate FXML — reuse one FXML with a state property.
- A ViewModel that imports a `javafx.scene.*` node type defeats headless unit testing;
  keep it to `Property`/`ObservableList` and port calls.
