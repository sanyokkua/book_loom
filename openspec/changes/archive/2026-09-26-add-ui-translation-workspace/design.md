# Design

## Context

See `proposal.md` — Why. What matters here is the shape of what already exists.

`:ui` holds `AppShellView` (a static factory returning a `StackPane` around one `Label`), `Theme.stylesheet()`,
an empty `UiModule`, and a `theme.css` whose header says outright that it exists to prove the cascade and that
the theming change extends it rather than adding a second file. `:app`'s `BookLoomApplication` builds the
injector in `init()`, runs two-phase lifecycle, then in `start(Stage)` wraps `AppShellView.create()` in a
`Scene` and attaches `Theme.stylesheet()` at scene level. `AppModule` already provides two executors:
`@Named("background")` (a fixed daemon pool) and `@Named("io")` (virtual threads per task) — today; D3 replaces
both string names with qualifier annotations.

Three facts about the engine drive most decisions below.

**`TranslationJob.run()` is synchronous and blocking.** It translates and exports on its calling thread and
returns `Result<JobReport>` only when the run is terminal. `JobControl.awaitPause()` parks that same thread on a
`Condition.await()`; `resume()` and `cancel()` signal it from another thread. There is no executor inside
`:pipeline`.

**`JobListener` fires on the job's own thread, synchronously, with no queue.** `JobEvent` is sealed over
`StageStarted`, `SegmentDecided`, `Paused`, `Resumed` and `Finished`, each carrying
`JobProgress(stage, section, sections, accepted, flagged, pending)`.

**Export is not a callable operation.** It is `JobStage.EXPORT` inside `run()`, performed by the package-private
`BookExporter`. `TranslationRequest(source, destination, targetLanguage, sourceLanguage, overwrite)` is where
the destination is decided, before the run starts.

Layering is enforced, not trusted: ArchUnit's `fx-free-core`, `dependency-direction` (`ui → api, util, pipeline`
only) and `ports-not-concretes` all run in `:app`'s `archTest` source set on every `check`.

## Goals / Non-Goals

**Goals**

- One surface over the existing engine, adding no engine behaviour.
- A shell whose structure is stable enough that later screens are additions rather than rewrites — the
  `ViewNames`/`Navigator`/state-mirror seam the architecture already names (seam F8).
- Every screen buildable and assertable with no display attached.
- The two themes and the two languages complete from the first screen, because both are far cheaper to establish
  than to retrofit.

**Non-Goals** (beyond the proposal's list)

- No viewmodel abstraction beyond what a screen needs. There is no base class, no generic form framework, no
  event bus.
- No streaming of in-flight source and target text into the dashboard: the engine does not report it.
- No throughput or time estimate: `07_UI_ARCHITECTURE_JAVAFX.md#jobprogress` describes the dashboard deriving
  both from elapsed time, and that clause is corrected rather than implemented, because a made-up estimate is
  worse than none.

## Decisions

### D1 — Packages, and which of the inventory's eight are created now

`01_MODULE_INVENTORY.md#module-ui` plans eight `:ui` packages. Six are created; two are not.

| Package | Holds | Now? |
|---|---|---|
| `ua.bookloom.ui` | `UiModule`, `Theme`, `AppShellView`, `ViewNames`, `Navigator`, the two executor qualifiers | extended |
| `ua.bookloom.ui.screen` | one controller per screen | created |
| `ua.bookloom.ui.state` | the state mirror and the viewmodels | created |
| `ua.bookloom.ui.theme` | theme selection and the OS colour-scheme reader | created |
| `ua.bookloom.ui.i18n` | bundle access, the typed key registry, the locale provider | created |
| `ua.bookloom.ui.notify` | the error dialog and the toast surface | created |
| `ua.bookloom.ui.view` | generic FXML controller infrastructure | **not** created |
| `ua.bookloom.ui.dialog` | the nine mockup dialogs | **not** created |

`ui.view` is not created because there is nothing generic to put in it: the controller factory is three lines in
`UiModule`, and a package holding one helper is structure for its own sake. `ui.dialog` is not created because
this change surfaces exactly two dialogs — about, and error-with-details — and both belong with the shell and
`ui.notify` respectively; the other seven arrive with the features that raise them. Both packages are added by
the change that first needs them, and `01_MODULE_INVENTORY.md` is updated to say six of eight exist.

`ui.notify` is the one of the six that has requirements of its own rather than being a place to put code: see
D11.

**`Theme` stays where it is.** `ua.bookloom.ui.theme` holds the controller and the colour-scheme seam; the
existing `Theme` class does not move into it. Its Javadoc records why: the stylesheet is resolved as a resource
of `ua.bookloom.ui` under JPMS encapsulation, so relocating the class relocates the lookup. The new package
extends it rather than replacing it.

*Alternative rejected:* create all eight empty with `package-info.java`. That is eight files asserting a plan
rather than describing code, and `records-first`/NullAway have nothing to check in them.

### D2 — FXML for layout, code for the shell

Screens are FXML plus a thin controller, loaded through
`FXMLLoader.setControllerFactory(injector::getInstance)` as `javafx-ui.md` requires. The shell chrome —
title bar, navigation column, content host, modal host, toast host — is built in code instead.

The shell is not a form; it is a small number of regions with a `ViewNames`-driven list, and its navigation
entries are generated from the enum rather than written out. Expressing that in FXML means either hand-writing
nine navigation entries that must stay in step with the enum, or building them in the controller anyway and
leaving an FXML file that declares an empty container. The screens are the opposite case: static layouts where
FXML is genuinely more readable than the equivalent builder code.

*Alternative rejected:* code-built screens throughout. It would remove the controller-factory requirement
entirely, but the rule mandates FXML for views, and the screens' layouts are exactly what FXML is good at.

### D3 — One state mirror, one runner, and the run result as the truth

`ua.bookloom.ui.state` gets a `@Singleton` mirror exposing JavaFX properties and one bounded observable list for
the activity log, with `publish*` methods that each wrap `Platform.runLater`. Nothing else may mutate it. Its
properties are exposed read-only to controllers.

A `TranslationRunner`, also `@Singleton`, owns a run: it submits `job.run()` to the injected background
executor, subscribes a `JobListener` before submitting, and holds the `Future`. `pause()`, `resume()` and
`cancel()` are called from the FX thread onto the job, which is safe — `JobControl` is lock-guarded and those
three methods do not block.

**The background executor is injected through a `@BackgroundExecutor` qualifier annotation declared in `:ui`
(`ua.bookloom.ui`), not through `@Named("background")`.** The string constant is `AppModule.BACKGROUND_EXECUTOR`
in `:app`, and `dependency-direction` forbids `ui → app`, so `:ui` would have to repeat the literal
`"background"` — a binding key crossing a module edge as an unchecked string, which is exactly the
parallel-constant shape `java-coding-style.md` rejects. That rule separately requires a custom `@Qualifier` where
two bindings of one type exist, and there are two (`background` and `io`). So `:ui` declares `@BackgroundExecutor`
and `@IoExecutor`, `AppModule` annotates its two `@Provides` methods with them instead of `@Named`, and
`AppModule.BACKGROUND_EXECUTOR`/`IO_EXECUTOR` are removed.

`@IoExecutor` is declared although nothing in this change injects it. That is not speculation: converting one of
two same-typed bindings to a qualifier while leaving the other on `@Named` produces a module where the
disambiguation rule holds for one binding and not its twin, and the two are provided side by side in the same
file. Both convert, or neither does.

**`:ui` is the right home, and `:util` is the wrong one.** `:app` already `requires ua.bookloom.ui` (it builds
the shell from it) and `:ui` already `requires com.google.guice`, so the annotations cost nothing at all: no new
dependency, no `module-info` change, no lockfile churn. `:util` looked like the tidier home and is not: its build
file declares `implementation(project(":api"))` and nothing else, its `module-info.java` states in prose that it
is *"deliberately without `com.google.guice`"* because `ua.bookloom.util.paths` runs before the injector exists
(DD-39, ADR-0015), and it carries a real committed lockfile that is outside the JavaFX locking carve-out. Putting
an injection annotation there means contradicting a documented architectural charter, adding a dependency and
regenerating lock state — to place a qualifier used by exactly one module, which is `:ui`.

*Alternative rejected:* repeat the literal `"background"` in `:ui` with a comment. It compiles, and it fails at
injector-build time — not compile time — the first time somebody renames the constant.

*Alternative rejected:* declare the qualifiers in `:util` as originally designed. See the paragraph above; the
cost is a module charter, a dependency and a lockfile, and the benefit is a location no current caller needs.

**The `Result<JobReport>` the future yields is the authoritative terminal outcome; events are progress only.**
This is a direct consequence of `docs/next_features.md` §14: a run refused before it starts — destination
present without overwrite, or a source that will not open — returns its error and never emits `Finished`. A
screen driven by events alone hangs on the commonest possible mistake. Reading the return value also removes any
need to reconcile two sources of truth about how a run ended.

*Alternative rejected:* fix `:pipeline` to emit `Finished` on a refused start. It is a small change and probably
the right one eventually, but it is engine behaviour, this change does not touch engine behaviour, and the
viewmodel would still have to read the return value to distinguish `COMPLETED` from `CANCELLED` from `FAILED`.

### D4 — Coalesce per-segment events

`SegmentDecided` fires once per segment; a real book is thousands of them, and a large EPUB in the corpus is
over five thousand. One `Platform.runLater` per segment floods the FX event queue, which is what
`threading-concurrency.md` warns about.

The runner keeps the latest `JobProgress` in an `AtomicReference` and publishes **at most once every 100 ms**
rather than per event, so the counters and the progress bar update smoothly at a bounded cost. 100 ms is ten
updates a second — faster than a person reads a changing number, slower than the FX queue notices. The activity
log is different: it is a record of decisions, so entries are accumulated and appended in one publish on the same
100 ms cadence, with the list **bounded to its last 500 entries**, the oldest dropped first. 500 is a few screens
of scrollback: enough to see what the run has been deciding lately, small enough that the list is not a second
copy of the book held in memory. A terminal event always publishes immediately, cadence or not.

Both numbers are fixed here rather than left to the implementation because they are what task 6.1's acceptance
assertion counts: a run emitting 5 000 `SegmentDecided` events in one second produces **at most 11** progress
publishes (ten cadence ticks plus the terminal one), not "far fewer than 5 000", which no test can express.

### D5 — Where the destination is chosen, and what the export screen becomes

The destination path and the overwrite flag live on the book-brief screen, beside source and target language,
because all four are components of `TranslationRequest` and all four must exist before `newJob` is called. The
default is computed by the same rule the command line uses — source name, target language inserted before the
suffix, written beside the source.

The export screen therefore reports rather than triggers: format, written path, accepted and flagged counts, and
an action that shows the written file in the system file manager.

This deviates from `08_UI_SCREENS_AND_STATES.md#screen-export`, which draws a save-path field and an "Export
book" button on that screen. The mockup is binding on visual and interaction detail, but the README scopes that:
where mockup and functional specification diverge on *behaviour*, the functional specification wins — and here
the behaviour is `FR-EXPORT-01`/DD-30's own model, in which export is the run's final stage. Honouring the
drawing would mean building a second write path into the document port, which is engine work. The clause is
corrected in this change, as `spec-authoring.md` requires of a clause the code legitimately outgrew.

*Alternative rejected:* keep the field on the export screen and let the translating screen read it. Navigation is
not a forced wizard, so a person can start a run without ever having opened the last screen, and the failure mode
is a refused start with a confusing message.

### D6 — One additive `:api` port for model discovery

`ProviderClient.listModels()` is built, specified and unreachable: it lives in `ua.bookloom.llm.provider`, and
`ProviderVerifierImpl` is its only caller. `:ui` cannot reach it — `dependency-direction` forbids `ui → llm`,
and `ports-not-concretes` forbids reaching a concrete class across a module edge even if it could.

So `:api` gains **`ua.bookloom.api.llm.ModelCatalog`**, one interface with one method:

```java
Result<List<ModelInfo>> listModels(String providerId);
```

`:llm` implements it as `ModelCatalogService` in `ua.bookloom.llm`, resolving the config through the existing
`ProviderConfigs.find(id)` — answering `ErrorCode.validation` and sending nothing when it is empty — and
otherwise delegating to the client the existing `ProviderClientFactory` already builds and caches. `ModelInfo`
already exists in `:api`. `LlmModule` binds the interface to the implementation.

Nothing about discovery changes: same endpoints, same parsing, same error codes. **Listing is not inference, so
it does not pass through `InferenceGate`** — the gate serialises model calls, and `ProviderClient.listModels` is
an HTTP `GET` of a catalogue. A person opening a model list while a run is translating gets their list rather
than a `busy`; routing it through the gate would make the settings screen freeze behind the book.

*Alternative rejected:* have `ProviderVerifier` return the discovered list in its report. It avoids a new type,
but it conflates "check this configuration" with "tell me what is available", and a screen filling a dropdown
would have to run a full three-stage verification — including an inference call — to populate a list.

### D7 — ControlsFX and Ikonli; no AtlantaFX

ControlsFX (BSD-3-Clause) supplies `ToggleSwitch` and `SegmentedButton`, each named by
`08_UI_SCREENS_AND_STATES.md#control-mapping-summary`. Most of their uses in this change are *disabled* controls
in the greyed regions of Book Brief and Export — which is precisely why they are worth adding: a disabled toggle
switch reads as a feature that is coming, and a disabled checkbox reads as a different application. Ikonli
(Apache-2.0) supplies the navigation and button icons. Both licences are already accepted by
`config/license/allowed-licenses.json` without a new entry.

AtlantaFX is declined although `07_UI_ARCHITECTURE_JAVAFX.md#theming` names it. `theme.css` is required by
FR-THEME-5 to be the single stylesheet declaring the role set, and the palette-token conformance assertion works
by looking a role up on `.root`. A second base theme that also styles `.root` and every control introduces a
second source of colour and a resolution order to reason about, for a benefit — prettier stock controls — that
the token catalogue is already supposed to deliver. The clause is corrected to record the decision rather than
left contradicting the code.

### D8 — Typed message keys as an enum, bundles beside them

`ua.bookloom.ui.i18n` holds an enum of message keys, a `Messages` service resolving a key and its arguments
through ICU `MessageFormat`, and an injectable `LocaleProvider` whose default reads `Locale.getDefault()` and
whose test double does not. Bundles are `messages_en.properties` (the base) and `messages_uk.properties`.

An enum rather than string constants because it makes the registry↔bundle bijection a test over
`values()` rather than a convention, and because a missing key becomes a compile error at the call site rather
than a `MissingResourceException` in front of a user. FXML references keys through the loader's bundle, so the
same catalogue serves both declarative and code-built text.

The Ukrainian catalogue is a working draft written against ICU's `one/few/many/other` categories, not a
professional translation; `complete-ukrainian-localization` is the change that makes it one. Shipping it as a
draft is still better than shipping English alone, because the structural work — every counted message carrying
four forms — is what is expensive to retrofit, not the wording.

### D9 — Testing

Every UI test runs on JavaFX 26's built-in headless platform (ADR-0019), already configured by
`bookloom.test-conventions` and already proven by `HeadlessToolkitCanaryTest`.

| Tier | What it proves | Where |
|---|---|---|
| Palette conformance | the 41 colour roles resolve, in both blocks, to the catalogued hex; the three elevation classes carry their drop shadow; no hex literal appears outside a value block | `:ui` |
| Widget | a control's state under given inputs | `:ui` |
| Screen/state | each named state of each screen renders its stated parts | `:ui` |
| Mockup conformance | structure and role references match the reference rendering — one case per screen, parameterized over the seven, plus the About and error dialogs, which `javafx-ui.md` binds on the same terms as a screen | `:ui` |
| Viewmodel unit | commands call the port; published state updates the property | `:ui`, no scene graph |
| i18n | key-set parity, ICU pattern validity, Ukrainian plural completeness, registry bijection, and every `%key` in every FXML file resolving to a registry key | `:ui` |
| Provider seam | model discovery through the new port, both dialects, at the HTTP wire | `:llm`, WireMock |
| End-to-end | a generated book through the viewmodels and the runner, against the `pseudo` model | `:app` |
| Arch | the eight boundary rules stay green with the new dependencies, plus a ninth banning `setStyle(` anywhere in `:ui` | `:app` `archTest` |
| Packaged image | the JPMS graph resolves — every `requires` and every `opens` this change adds | `scripts/` |

"Wired" is asserted behaviourally per `testing.md`: drive the mirror and assert the node changed; fire the
control and assert a recording fake port was called. Never by inspecting a binding. `:ui` stays outside the
coverage gate, as its build file already records.

Established patterns to copy rather than reinvent: `HeadlessToolkitCanaryTest` and `ThemeTest` for the TestFX
`ApplicationTest` shape; `TranslateCommandTestFakes` (`RecordingProviderConfigs`, `ScriptedProviderVerifier`,
`RecordingChatModelFactory`) and `TranslationJobTestSupport` for hand-written recording fakes; `OllamaClientTest`
and `OpenAiCompatibleClientTest` for the WireMock-per-dialect shape, which every provider test sets up for itself
— there is no shared harness today and this change does not add one.

Four obligations are stated as scenarios in the specs and would otherwise be checked by nothing, so each gets a
named mechanism rather than a hope. Three of them are also **scheduled with the code they govern rather than at
the end**, because a check that arrives after nine tasks of unchecked work is a cleanup task wearing a gate's
clothes:

- **"No colour value appears outside a value block"** — the palette test scans `theme.css` for a hex or `rgba(`
  literal outside the two `.root` blocks and fails on any hit. *Lands with the stylesheet.*
- **"No element has a style applied to it individually from code"** — an ArchUnit rule in `:app`'s `archTest`
  set, `no-inline-style-in-ui`, rejecting any call to `setStyle` from a class in `:ui`. A grep would pass a
  release and fail nobody. *Lands with the stylesheet, before any screen exists to violate it.*
- **"Every key referenced is defined"** as it applies to FXML — D8 routes FXML text through the loader's bundle,
  which means the FXML files contain raw `%key` strings that no compiler checks. The i18n suite extracts every
  `%key` from every FXML resource in the module and asserts each one is a registry constant, and the reverse for
  keys only FXML uses. Without it, the "typed keys" claim holds for Java call sites and silently does not hold
  for the half of the text that lives in FXML. *Lands with the first FXML files.*
- **Every `opens` this change adds** — six tasks each add one, and nothing on the `check` graph can see them.
  `modules/app/build.gradle.kts` says so in its own comment: `:app:run` launches on the classpath, where a
  missing `requires` or an unopened package cannot fail. The packaging scripts already exist
  (`scripts/package-macos.sh`, `scripts/launch-smoke.sh`) and CI already runs them on three platforms; the gate
  task runs them once locally so the answer arrives before the merge rather than after it.

### D10 — The three elevation roles are an effect, not a colour

`09_THEMING.md#token-catalog` lists 32 roles and `#status-colours` 12 more, 44 in all, and the mockup's two
`data-theme` blocks declare exactly those 44 names. Three of the 44 — `shadow-sm`, `shadow`, `shadow-lg` — hold a
CSS box-shadow triple (`0 3px 10px rgba(58,74,82,.12)`), which is an offset, a blur and a colour rather than a
colour.

**A JavaFX looked-up colour cannot carry that value, and does not say so.** Verified on this project's JavaFX
26.0.2 runtime under the headless platform: a `.root` declaring `-color-shadow: 0 3px 10px rgba(58,74,82,.12)`
alongside `-color-plain: #a58075`, with a region styled `-fx-background-color: -color-shadow`, renders with
`getBackground() == null` — the value is silently dropped — while the sibling region referring to `-color-plain`
resolves to `0xa58075ff`. There is no error, no warning and no fallback; the element simply has no background.

So the three elevation roles are expressed as `-fx-effect: dropshadow(three-pass-box, <colour>, <radius>, 0, 0,
<y-offset>)` on three named style classes (`.elevation-sm`, `.elevation`, `.elevation-lg`), declared once per
value block exactly as the colour roles are, and an element that needs elevation takes the style class instead of
referring to a role. The same probe confirms the positive half: that declaration yields a real
`javafx.scene.effect.DropShadow` on the node.

The consequence for the acceptance test matters more than the mechanism. "Every catalogued role resolves on
`.root`" is then true of **41** roles and meaningless for three, so `theming`'s "Express the three elevation
roles as an effect rather than a colour" carves them out explicitly and gives them their own obligation —
rather than leaving task 1.1 with an assertion that cannot pass and an implementer inventing a shadow strategy
inside the one task every later task styles against.

*Alternative rejected:* decompose each elevation role into three sub-tokens (`shadow-color`, `shadow-radius`,
`shadow-y`) so the catalogue stays colours-only. That turns 3 published role names into 9 unpublished ones, which
breaks the "44 names, identical to the reference rendering" check that the completeness requirement is built on,
and buys nothing: the style class still has to exist to carry the effect.

### D11 — `ui.notify` holds the error dialog and the toast surface

`01_MODULE_INVENTORY.md#module-ui` assigns "toasts, banners, error dialog surface" to `ua.bookloom.ui.notify`,
and every screen in this change has a failure path that has to go somewhere. The package holds two things:

- a `@Singleton ErrorPresenter` that takes an `AppError` and surfaces it — title and message shown, `details`
  behind a collapsed expander, a retry action offered when `retryable` is true, and `cause` never read at all;
- a `Toasts` interface with a `@Singleton ToastStack` implementation — native token-styled JavaFX nodes
  stacked in the shell's `#shell-toast-host`, with one method per severity and each severity bound to its status
  role, raised on the five occasions `notifications` names. Toast API takes `MessageKey` + args so every word
  comes from the catalogue.

**Why native nodes, not ControlsFX `Notifications`:** ControlsFX 11.2.5 `Notifications` opens a separate OS popup
window and inserts its own stylesheet (hard-coded greys) at index 0 of the shell scene's stylesheets, which defeats
token-only theming and the light/dark swap and cannot be placed in the shell's toast host. The interface/implementation
split (`Toasts`/`ToastStack`) allows later viewmodel tests (9.1, 9.2) to use hand-written recording fakes without
Mockito. The approach would be falsified only by a requirement for toasts outside the application window (e.g. OS
notifications — currently a stated non-goal).

`ua.bookloom.app.bootstrap.StartupFailureDialog` is the precedent for the first of these and worth reading before
writing it: it is the repo's existing `AppError`-to-a-person surface, and it already makes the load-bearing
choice — it shows `title()` and `message()` and never touches `details()` or `cause()`. It is not reusable as it
stands, deliberately: it runs before the injector and before logging, so it builds a raw `Stage`, blocks on a
latch and calls `System.exit` when dismissed. `ErrorPresenter` takes its discipline and none of its mechanism.

Neither is a dialog *catalogue*: `ui.dialog` still does not exist (D1), because these two are the only surfaces
this change raises.

The routing from a typed code to one of those surfaces is **not** in `ui.notify` — it is stated in
`notifications`' "Decide the surface from the failure's own code" and implemented in each screen's viewmodel,
which is the only place that knows whether a failure ended a run, refused a start, or answered a model list. A
central router would have to be told that anyway, and would add a second thing to keep in step.

This is what makes two `app-shell` obligations checkable rather than vacuous: "shows no error dialog" and "shows
no message of error severity" are assertions about `ErrorPresenter` and `Toasts` not being called, and a recording
fake of each is what a stopped-run test asserts against.

`ErrorPresenter.present(AppError)` offers dismiss only; the retry action exists only through
`present(AppError, Runnable)`, because a Retry button with nothing to run would be a control that does nothing. The
specification's "a retryable failure offers to retry" therefore holds for every caller that supplies something to
retry. Today none can be affected: the two codes routed to the dialog, `internal` and `busy`, are not retryable.

### D12 — Two `.claude/rules` MUSTs are amended, with the reason recorded

Both rules are generic and this change's specification is specific, so per the owner's decision the rules give —
edited in this change rather than quietly contradicted (`AGENTS.md#how-work-is-planned`).

**`javafx-ui.md`: "build views as FXML + a controller".** D2 builds the shell chrome in code. The rule gains a
narrow carve-out naming the shell chrome alone — title bar, navigation column, content host, modal host, toast
host — on the stated ground that its navigation entries are generated from `ViewNames` and an FXML file
declaring an empty container is worse than no file. Screens stay FXML plus a controller through the Guice
controller factory, unchanged. `07_UI_ARCHITECTURE_JAVAFX.md#mvvm` gets the same note.

**`javafx-ui.md` and `threading-concurrency.md`: "long work in a JavaFX `Task`/`Service`".** D3 submits
`job.run()` to the injected executor and holds a `Future`. A `Task` is the wrong wrapper here for a specific
reason: `TranslationJob.run()` is synchronous and returns `Result<JobReport>` — failure is a *return value*, not
a thrown exception — so `Task.setOnFailed`, which `logging.md` names as the canonical place to log a task
failure, never fires. Wrapping it would produce a `Task` that always succeeds and a failure path that still has
to be read off the returned `Result`, which is two mechanisms where one will do.

Both rules therefore gain: long work runs on an injected daemon executor; a JavaFX `Task`/`Service` is used where
the work reports failure by throwing, and a plain submission returning a typed `Result` is used where it does
not — and in that case the outcome is logged where the `Result` is read, which is the one place it exists.
Provider verification and opening a book, which both return a `Result` too, follow the same rule. What is not
relaxed: the work still runs off the FX thread on the injected executor, and every result still reaches the
scene graph through the state mirror.

**`theming-tokens.md`: "every catalogued role is a looked-up colour with a light and a dark hex".** D10 makes
three of the 44 roles drop-shadow effects on style classes, which are neither looked-up colours nor hexes. The
rule gains the same carve-out the `theming` requirement does, naming those three roles and the reason (a
looked-up colour holding a box-shadow triple is silently dropped and the element renders with no background).
This amendment is carried in the task that writes the stylesheet rather than in the final documentation task:
that task's own Read list points at this rule, so leaving it unamended means the first task of the change
produces output its own instructions reject.

### D13 — What is shared with the command line, and what only looks like it is

`TranslateCommand` already drives this pipeline end to end, and the window is meant to reach the same result. The
temptation is to extract a shared "run a translation" service. Read against the actual code, exactly one thing
qualifies.

**Extracted: the destination-naming rule.** `TranslateCommand.destination(...)` is a private static method that
strips the format's real suffix through `BookFormat.matchedSuffix` and inserts the target language before it. It
looks like a one-liner and is not: for `Kobzar.fb2.zip` the suffix is two extensions, and a second implementation
gets that wrong silently, on the format least likely to be checked by hand. It moves to `ua.bookloom.util.paths`,
`TranslateCommand` calls it, and the book-brief screen calls the same code. `:util` already depends on `:api` for
`BookFormat`, and `:ui` and `:app` both already require `:util`, so this costs nothing — and unlike D3's rejected
plan it adds no framework to `:util`, only a path helper, which is what that module is for.

**Not extracted: provider and model selection.** `ModelSelection` is `record ModelSelection(String providerId,
String modelId)`. The screen holds both strings already and constructs one in a line. `TranslateCommand`'s
`selectModel`/`providerConfig`/`presetConfig`/`customConfig`/`applyOverrides` exist to turn command-line flags —
`--base-url`, `--timeout`, a custom endpoint — into a registered `ProviderConfig`, and this build's settings
screen offers neither a custom endpoint nor an override. There is no shared logic under the similarity, only two
callers of the same `:api` ports.

**Not extracted: the run's outcome mapping.** `TranslateCommand.report(...)` turns a `Result<JobReport>` into an
**exit code**; `notifications`' assignment turns an `ErrorCode` into a **surface**. Same input, different
questions, and neither answer is derivable from the other. Extracting a common mapper would mean inventing a
third vocabulary that both then translate out of.

The screens' tasks name `TranslateCommand`'s methods in their Read lists as the worked example to follow. Reading
code is how the two stay consistent here; sharing it is how they would stay consistent only until the first
requirement diverged.

*Alternative rejected:* extract a `TranslationService` that both the CLI and the UI call. The honest content of
that class is `engine.newJob(request, model).run()` plus a listener the CLI does not subscribe and a `PrintStream`
the UI has no use for. It would be an abstraction built for symmetry between two callers that genuinely differ.

## Risks / Trade-offs

- **The change is larger than the house norm** → It is ten task groups on task branches, each merged into the
  feature branch on its own, so the work is reviewable in slices even though it ships as one unit. The proposal
  states the deviation instead of disguising it.
- **Pausing parks the executor thread** → With a fixed pool, a paused run holds one thread indefinitely. Only one
  run exists at a time in this change, so the pool cannot be exhausted; a second concurrent run would need a
  dedicated thread per job, and that is noted for the change that introduces one.
- **A person can start a run with a model the server does not have** → The verification is offered, not forced.
  The run then fails with a typed provider error and the dashboard's provider-error state offers a route to the
  settings, which is the designed recovery.
- **Nothing is remembered between launches** → Chosen every launch: provider, model, target language. This is the
  most visible cost of deferring persistence, and it is specified rather than merely accepted, so the gap is
  legible instead of appearing as a bug.
- **A long run has no time estimate** → The engine reports counts, not rates. The proportion complete and the
  remaining count are shown; inventing an estimate from elapsed time would be a guess presented as information.
- **Two `.claude/rules` MUSTs are amended rather than followed** → Both amendments are narrow, both carry their
  reason in the rule text, and both are listed in the proposal's corrected-documents block so the edit is
  reviewed as part of the change rather than discovered later. The risk is that "the rule gave way" becomes a
  precedent; the mitigation is that each carve-out names the exact shape it covers (the shell chrome; work that
  reports failure by returning rather than throwing) and leaves everything else intact.
- **The elevation roles diverge from the published catalogue's shape** → Three of 44 roles stop being looked-up
  colours and become style classes. The compensation is that they are the only three that never worked as
  colours, the divergence is stated in the requirement rather than discovered in a stylesheet, and the light/dark
  obligation still applies to them unchanged.
- **Two new dependencies on the `:ui` classpath** → Both are permissively licensed, both are additive, and
  `:ui` reaches no core module, so neither can leak into the FX-free core. `fx-free-core` and
  `dependency-direction` catch it if that ever stops being true.
- **The Ukrainian catalogue ships as a draft** → Its structure is correct and tested; its wording is not
  reviewed. An untranslated-looking phrase is visible and cheap to fix; a missing plural category is neither.

## Migration Plan

None. Nothing is persisted, no schema exists, no public interface changes, and the command line keeps its exact
behaviour. Rollback is reverting the feature branch.
