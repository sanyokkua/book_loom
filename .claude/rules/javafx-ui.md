---
paths:
  - "modules/ui/**"
  - "modules/app/**"
---

# JavaFX UI

Scope: `:ui` and `:app` (`**/ui/src/main/java/**`, FXML/CSS under `**/ui/src/main/resources/**`). Spec: `docs/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`. Visual acceptance: `docs/specification/mockups/ui-mockup.html`.

## MUST

- **MUST** build views as FXML + a controller instantiated through a **Guice controller factory** (`FXMLLoader.setControllerFactory(injector::getInstance)`). Controllers use constructor injection; no `new` of collaborators in a controller. — Rationale: FXML stays declarative and controllers stay testable/injectable.
  **Carve-out (D12 of `add-ui-translation-workspace`):** the shell chrome — title bar, navigation column, content host, modal host, toast host (`AppShellView`) — and the dialog cards shown in its modal host (the About card now, the error-with-details dialog in task 5.2) are built in code. The chrome's navigation entries are generated from `ViewNames`, and a dialog card is short static content assembled with the shell and shown in the shell's own host; an FXML file declaring an empty container, or a controller for a card with no state of its own, is worse than no file. Every screen stays FXML + a controller through the Guice controller factory; the carve-out covers no screen.
- **MUST** address every screen through a `ViewNames` enum (one constant per view), not string paths scattered in code. Navigation resolves FXML + title from the enum. — Rationale: one typo-proof registry of screens.
- **MUST** hold shared application state in a single `@Singleton` observable **state mirror**; the pipeline/services publish updates only via its `publish*` methods, each of which wraps mutation in `Platform.runLater`. — Rationale: one FX-thread-safe seam between headless work and the scene graph.
- **MUST** run all long/blocking work (translation jobs, provider verification, I/O) on an injected daemon executor, off the FX Application Thread, and let its result reach the scene graph only through the state mirror. — Rationale: the FXAT must never block; see `threading-concurrency.md`.
  **Carve-out (D12 of `add-ui-translation-workspace`):** a JavaFX `Task`/`Service` is used where the work reports failure by *throwing*; a plain submission returning a typed `Result` is used where it does not (`TranslationJob.run()`, provider verification, opening a book), and then the outcome is logged where the `Result` is read, the one place it exists. The reason is that `Task.setOnFailed` never fires for a call that returns its failure, so a `Task` around it would always succeed and the failure would still have to be read off the `Result`. Off the FX thread, on the injected executor, and through the mirror are not relaxed.
- **MUST NOT** read or mutate the scene graph (nodes, properties, `ObservableList`s bound to controls) off the FX Application Thread. Bridge every update through `Platform.runLater`/the state mirror. — Rationale: JavaFX controls are not thread-safe; off-thread mutation corrupts or crashes the UI.
- **MUST** style with **token-only CSS applied at the `Scene` level** (looked-up colors on `.root`); never `node.setStyle("...")` inline. — Rationale: theming stays centralized and swappable; see `theming-tokens.md`.
- **MUST** bind visual acceptance of every screen/state/dialog to `docs/specification/mockups/ui-mockup.html` (P6 pattern) and back it with a headless TestFX assertion where practical. — Rationale: the mockup is the binding UI source of truth.

## SHOULD

- **SHOULD** map each mockup widget to its real JavaFX control (segmented picker → `SegmentedButton`/`ToggleGroup`, side-by-side → two `TextArea`, tables → `TableView`, chapter tree → `TreeView`, toggle → `ToggleSwitch`, tabs → `TabPane`, dialog → `Dialog`/`Alert`, toast → native token-styled nodes, icon → Ikonli). — Rationale: consistency with the mockup and the control catalogue.
- **SHOULD** keep controllers thin — bind to a viewmodel/state-mirror property, delegate logic to `:pipeline`/`:api` ports. — Rationale: presentation logic stays out of business modules and stays FX-free downstream.
- **SHOULD** batch high-frequency `Platform.runLater` updates (e.g. progress) rather than one call per segment. — Rationale: avoids flooding the FXAT event queue.

## Reject if

- A controller is constructed with `new` or injected by field/setter instead of via the Guice controller factory.
- Any scene-graph read/write happens off the FX Application Thread, or long work runs on the FXAT.
- A view is referenced by a hard-coded FXML path instead of `ViewNames`.
- Styling uses inline `node.setStyle(...)` or a hard-coded color instead of Scene-level tokens.
- Shared state is mutated outside the `@Singleton` state mirror's `publish*`/`Platform.runLater` seam.
- A screen/state/dialog ships with no reference to the mockup and no TestFX visual check.
