# UI architecture

This page describes `:ui`, the JavaFX presentation module: how it is organised, which screens exist, how the pipeline's work reaches the scene graph safely, how theming and translations work, and how the UI is tested.

`:ui` depends only on `:api`, `:util` and `:pipeline` (plus JavaFX); `:app` builds the window around it. No other module sees JavaFX.

## Package structure

Packages under `ua.bookloom.ui`:

| Package | Holds | Examples |
|---|---|---|
| `ua.bookloom.ui` | The shell chrome built in code, navigation, executors' qualifiers, startup helpers | `AppShellView`, `NavColumn`, `Navigator`, `ViewNames`, `GuiceControllerFactory`, `UiModule`, `WindowLaunch`, `SessionInfo`, `DiagnosticLog`, `KeepAwake` |
| `.screen` | One view plus a thin controller per screen, and the view parts they are built from | `ImportController`, `BookBriefController`, `NamesStyleController`, `TranslatingController`, `ExportController`, `SettingsController`, `ProjectsController`, `ReviewDeskCard` |
| `.control` | Shared, reusable controls | `StepFooter`, `Banner`, `StatTile`, `SearchableCombo`, `TwoColumns`, `Wrapped`, `Tips`, `Motion`, `SmoothScroll` |
| `.dialog` | Confirmation and input cards (each dialog with its prompt seam) | `ConfirmDialog`, `OpenAnotherBookDialog`, `NarratorDialog`, `ExportCompleteDialog`, `ModelPickDialog`, `LeaveDialog` |
| `.state` | The observable state mirror, run session and view models | `StateMirror`, `RunSession`, `TranslationRunner`, `RunStarter`, `CurrentProject`, `BookBriefViewModel`, `ReviewViewModel`, `FxThread` |
| `.i18n` | Message bundles access and locale selection | `Messages`, `MessageKey`, `LocaleProvider`, `OsLocaleProvider`, `ErrorWords` |
| `.theme` | Theme mode and the light/dark switch | `ThemeController`, `ThemeMode`, `ThemeBlock`, `ColorSchemeProvider`, `StyleClasses` |
| `.notify` | Error dialogs and toasts | `ErrorPresenter`, `ErrorDialog`, `ToastStack`, `Severity` |

Resources are in `modules/ui/src/main/resources/ua/bookloom/ui/`: `theme.css`, `i18n/messages_en.properties`, `i18n/messages_uk.properties`, and the screen FXML files in `screen/`.

Wiring conventions:

- Views are FXML plus a controller created by Guice (`GuiceControllerFactory`, the `FXMLLoader` controller factory). Controllers use constructor injection and do not `new` their collaborators. The shell itself is built in code.
- Every screen is a constant of `ViewNames`: `PROJECTS`, `IMPORT`, `BOOK_BRIEF`, `NAMES_STYLE`, `TRANSLATING`, `EXPORT`, `SETTINGS`. A constant carries its message keys, group, step number, FXML resource and icon, so navigation is generated from the enum and no code addresses a screen by string.
- `UiModule` verifies that the executors and ports `:ui` needs are bound by the composition root, so a missing binding fails at startup, not at first click.

## Screens

Numbered workflow steps, shown with a provider and model footer:

| Step | Screen | What it does |
|---|---|---|
| 1 | Import | Opens a book on the stored project and shows its format, declared language, cover, structure tree and verdict. The book's content is matched against stored projects, so a known book is never imported twice. |
| 2 | Book Brief | Languages (any language the JDK can name, not only the offered list), who narrates, name policy, quality dial, the "Also translate" switches. Read-only while a translation is at work; languages lock once a segment is decided. |
| 3 | Names & style | The glossary (one entry per name with its forms), the Recurring terms card, Scan, Review, Translate and Stop, CSV import and export, an autonomous names pass with Undo all. |
| 4 | Translating | Summary strip, live card with Simple and Detailed rows and each call's prompt and reply, the activity log, provider-error banners (Skip segment, Retry now), Proofread stage chips, and the review desk (accept, edit, revert, skip, apply a proposal, Changed by proofread, Undo proofread, Try again with the context rebuilt from the current glossary and settings). |
| 5 | Export | Save as, side files, tiles from the project and the stored last export, banners for a translating, partial or empty book, and the export-complete dialog. |

Outside the numbered steps:

- **Projects** lists, opens, continues, renames and removes stored projects. Only one translation runs per application run: importing or switching while a run can continue asks first.
- **Settings** is a tab pane: Providers (detected engine; tests the form as typed in three stages), Models (name filter, per-engine rows, overrides), Generation (shows what each quality setting does), Automation, Appearance (theme, interface language, applied after a restart) and Storage and logs.

The title bar shows the run and a connection chip. The shell is built for 1024 by 700 with a 960 by 640 minimum; the title bar gives way by priority at narrow widths. Screens must hold on macOS, Windows and Linux without a bundled font: wrapping labels use `Wrapped.wholeLines`, controls that must stay whole set `setMinWidth(Region.USE_PREF_SIZE)`, and rows that can run out of room wrap instead of squeezing.

## The state mirror and the FX thread rule

The pipeline runs on background threads; JavaFX controls are not thread-safe. The bridge between them is deliberately narrow:

- `StateMirror` is a `@Singleton` holding the observable state of the current run: run state, figures, failure, report, activity log (capped at 500 entries), a live section (current call, rows) and a review section. Its properties are read-only and its list is unmodifiable.
- Its `publish*` methods are the only mutators. Each wraps its mutation in `Platform.runLater`, so it is callable from any thread and publications apply in the order made. A second bridge would make the thread rule unenforceable.
- `TranslationRunner` starts a run on the `@BackgroundExecutor` daemon pool, subscribes a listener to the `TranslationJob` (`JobListener`), and the listener translates each sealed `JobEvent` (segment started, drafted or decided, model call started or finished, paused, finished) into `publish*` calls. Controllers observe the mirror's properties and never read the engine directly.
- Pause, resume, stop and skip are calls from the UI into the `TranslationJob`; their effect comes back as events and so as mirror updates.
- `FxThread.run` runs a callback at once on the FX thread, otherwise later on it, for outcomes of other background work (provider tests, project actions).
- High-frequency updates (live progress, ticks) are batched or driven by a clock rather than one `runLater` per event, and no log call sits inside layout, cell `updateItem` or a frequently-firing listener.
- Long work (jobs, provider verification, file I/O) never runs on the FX thread; blocking I/O fan-out uses the virtual-thread `@IoExecutor`.

## Theming

- **Tokens only.** `theme.css` defines looked-up colours on `.root` (surface, border, text, primary, navigation, status backgrounds and edges, shadow and others), and every selector references tokens. There are no hard-coded colours in component rules and no inline styling: the ArchUnit rule `NO_INLINE_STYLE_IN_UI` fails the build if a `:ui` class calls `setStyle` on any JavaFX type.
- **One token set, two value blocks.** Light and dark are two blocks of the same roles. `ThemeController` holds the mode (`LIGHT`, `DARK` or `SYSTEM` from `ThemeSetting`, stored as `ui.theme`) and applies the matching block by toggling the dark style class on the scene root. `ColorSchemeProvider` reads the OS colour scheme for the initial choice and for following it.
- **Brand palette.** Charcoal, Slate, Sand Dollar and Cognac, with desaturated status colours; status shown as words uses a dedicated text role because the fill hue is too light as text. The token catalogue is in the [theming spec](../spec_baseline/specification/01_Product/09_THEMING.md).
- **WCAG AA in both themes.** Text meets 4.5:1 against the surface it is painted on; icons and control edges meet 3:1. `ContrastTest` computes this from the real token values.
- **Reduced motion.** Every transition lives in `Motion`, and all of them stop when `BOOKLOOM_REDUCE_MOTION` (or `-Dbookloom.reduceMotion`) says so or the operating system asks for it. Motion never carries information alone. The test tasks set the property, so tests read final states at once.

## Internationalisation

- Two bundles: `messages_en.properties` (the default) and `messages_uk.properties`. They must have identical key sets.
- Messages are ICU `MessageFormat` (`com.ibm.icu.text.MessageFormat`), not `java.text`, because only ICU has `plural` with Ukrainian's one, few, many and other categories. A pattern that does not parse is displayed unformatted with one WARN.
- Every message is addressed through the typed `MessageKey` enum, never a string literal; `MessageKeyRegistryTest` checks every key is defined in the bundles and that none is unused, and `FxmlMessageKeyTest` checks the keys the FXML files reference.
- `Messages` reads the locale once at first use, so a screen is never half in each language. `OsLocaleProvider` (behind the injectable `LocaleProvider`) selects Ukrainian when the operating system is Ukrainian and English otherwise. The Appearance tab stores an explicit choice as `ui.language`, applied after a restart.
- The 34-language list offered for translation is a convenience; any language the JDK can name works as a source or target. It is separate from the two interface languages.
- Wording rules: privacy is "private when used with local models"; error words come from machine codes through selecting messages (`ErrorWords`) so the UI never shows a raw exception message.

## Accessibility and affordances

- Every operable control (button, toggle, field, combo, column header) explains itself on hover. `TooltipCoverageTest` walks the real scene of every conformance screen and state and fails by name for a control without a tip.
- Focus order and keyboard operation have their own tests (`FocusTraversalTest`). Accessibility assertions are advisory, not a merge gate.

## Error presentation

Failures reach the UI as `AppError` values and are shown by `ErrorPresenter`: a toast for soft outcomes, a dialog with expandable technical details for hard failures, a Retry action when `retryable`, an info state for `cancelled`. Details come only from `SafeDetails`.

## How UI tests work

- **Headless TestFX.** UI tests run on JavaFX 26's built-in headless platform (`glass.platform=Headless`, set in the shared test conventions), so no display is needed. `FxTestBase` provides a faster TestFX lifecycle than `ApplicationTest`.
- **Wired means behaviour.** Property to node: drive a view model property or a state-mirror `publish*` and assert the node updates. Node to command: fire the control and assert the viewmodel command or port was invoked on a recording fake. Bindings are never inspected.
- **Conformance.** `ConformanceTestBase` and `ConformanceCases` check each screen and state against the mockup (`docs/spec_baseline/specification/mockups/ui-mockup.html`) structurally and by looked-up colour tokens. App code never references the mockup: no demo bars, badges or "Mockup:" buttons ship.
- **Fit tests.** `FitMatrix`, `FitProbe` and `DialogFitTest` check text fits at the supported sizes. Text is measured with the platform's font, so before pushing a change to a screen, dialog or UI word, run `scripts/ci-linux-ui.sh` (see [quality and CI](../development/quality-and-ci.md)).
- **i18n tests** (`modules/ui/src/test/java/ua/bookloom/ui/i18n/`): bundle parity, ICU pattern validity, plural coverage, locale selection.
- **Visual checks.** The tagged `visual` set (pinned environment) is local-only and excluded from `check`.
- Run one module's tests with `./gradlew :ui:fastTest` or the focused loop in [testing](../development/testing.md).

## See also

- [Overview](overview.md), [Document engine](document-engine.md), [Decisions index](decisions.md)
- [Testing](../development/testing.md), [Quality and CI](../development/quality-and-ci.md)
- [UI architecture spec](../spec_baseline/specification/02_Architecture/07_UI_ARCHITECTURE_JAVAFX.md), [screens and states](../spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md), [theming](../spec_baseline/specification/01_Product/09_THEMING.md), [i18n and accessibility](../spec_baseline/specification/01_Product/10_I18N_AND_ACCESSIBILITY.md)
- [Threading spec](../spec_baseline/specification/02_Architecture/08_THREADING_CONCURRENCY.md)
