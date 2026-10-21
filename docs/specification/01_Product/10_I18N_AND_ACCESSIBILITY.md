**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), UX, QA **Last Updated:** 2026-10-19
**Cross-references:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/09_THEMING.md`, `docs/specification/01_Product/07_SETTINGS.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`, `docs/specification/mockups/README.md` (global patterns
G1–G12, layout rules L1–L10, the Words table)

# Internationalization and Accessibility

This document specifies i18n and accessibility requirements for the UI, realizing `FR-UI-06` and `FR-UI-07`. Targets are
WCAG 2.1 AA.

Status convention: what is **built** runs today; what is marked **planned — UI finalization + SQLite** is the next change
(`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). Nothing is saved across restarts today (ADR-0034), so
the language choice is applied from the OS locale on every launch until persistence lands.

## internationalization {#internationalization}

This version ships exactly **two** UI languages (DD-34): **English** — the default and base bundle — and **Ukrainian**
(locale `uk`). All user-facing text is provided as `ResourceBundle`s, `messages_en` and `messages_uk`; the two bundles
are kept in parity so every displayed string exists in both. Adding further languages later is a matter of adding a
bundle and listing its locale, but no other language is in scope now.

On first start the application detects the OS locale through an **injectable `Locale` provider** (not a direct
`Locale.getDefault()` call at the use site): a Ukrainian OS locale selects `uk`, and anything else selects English. Once
settings persist (**planned — UI finalization + SQLite**), the chosen locale is stored and, on every subsequent launch,
the stored choice — not the OS locale — governs; until then the OS locale decides at each launch. Because the OS locale
is read through the injectable provider, first-start detection is **unit-testable**: a test supplies a fake provider
returning `uk`/`en`/other and asserts the resolved language without touching the real machine locale.

**Switching the language applies after BookLoom restarts.** Settings → Appearance shows "Applies after BookLoom
restarts." with a **Restart now** action; the running views are not rebuilt live, because a live switch would rebuild
every view (see `mockups/README.md`, Settings › Appearance). The language chooser is **planned — UI finalization +
SQLite**; today the language follows the OS locale.

### user vocabulary and Ukrainian writing rules {#words}

Main screens use plain user words, not pipeline vocabulary; the table of words to avoid and their replacements is the
**Words** section of `mockups/README.md` and is binding for both bundles. Highlights: the unit of text stays
**"segment"** (explained once, in the tooltip of every segment count: "A segment is a paragraph, heading or caption — one
piece of the book's text."); chunks, calls, tokens, "provider" in sentences, "flagged" ("to review") and similar terms
appear only in Detailed views, Advanced folds, and when **Show technical details** is on (G12). Ukrainian follows the
same rules: **one idea per sentence, an ICU plural for every count, and no gendered past tense addressed to "you"**.

### plural and gender handling (ICU) {#icu-messages}

Plural- and gender-sensitive strings use **ICU4J `MessageFormat`** (ICU4J is already a dependency; DD-48), not Java's
positional `MessageFormat` or manual `if (n == 1)` branching. Bundle values for such keys are **ICU message patterns**
(`{count, plural, one {…} few {…} many {…} other {…}}`, `{gender, select, …}`), so Ukrainian pluralization — which needs
the **one / few / many / other** categories — is expressed once in the bundle rather than in code. English uses its own
subset (one/other). Every count shown to the user (segments, items to review, terms, "Skip these 4 segments") goes
through such a pattern. This keeps counters, item counts, and gendered notices grammatically correct in both locales
without string concatenation.

All keys are addressed through a **typed message-key registry** — generated/enumerated constants, never bare string
literals at call sites — so that "a key a controller/FXML references but no bundle defines" (and the reverse) is a
finite, **enumerable** set the i18n/parity test walks (`04_Build_and_Release/06_TESTING_STRATEGY.md#i18n-tests`).

| ID        | Requirement                                                                                                                                                                                                                                     |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-I18N-1 | All user-facing text is sourced from `ResourceBundle`s (`messages_en`, `messages_uk`); no hard-coded display strings in controllers or FXML.                                                                                                    |
| FR-I18N-2 | The two UI languages are English (default) and Ukrainian (`uk`); the language is user-selectable in Settings → Appearance (FR-SETTINGS-04) — **planned — UI finalization + SQLite**; today it follows the OS locale.                            |
| FR-I18N-3 | Changing the UI language **applies after BookLoom restarts**: the setting says so ("Applies after BookLoom restarts.") and offers **Restart now**; views are not rebuilt live (FR-SETTINGS-V3) — **planned — UI finalization + SQLite**.         |
| FR-I18N-4 | On first start the default UI language follows the OS locale — Ukrainian OS locale → `uk`, otherwise English (the base bundle) — resolved via an **injectable `Locale` provider** so the rule is unit-testable without the real machine locale. |
| FR-I18N-5 | Layouts accommodate variable text length without truncation of essential labels. Ukrainian labels grow about 26 % over English, so every layout fit test (title bar, toolbar, dialog footers, segmented controls in narrow panes) runs under both `en` and `uk` (`mockups/README.md`, L10; FR-I18N-10). |
| FR-I18N-6 | Locale-sensitive formatting uses the selected locale for the enumerated **locale-formatted fields** (below), via `NumberFormat` and `java.time`/`DateTimeFormatter`, never hand-built strings.                                                  |
| FR-I18N-7 | The selected language is persisted in the typed KV settings store under the key `ui.language` and restored on next launch (`06_DATA_MODEL_SQLITE.md#settings`) — **planned — UI finalization + SQLite**.                                         |
| FR-I18N-8 | Plural- and gender-sensitive strings are rendered with **ICU4J `MessageFormat`**; their bundle values are ICU patterns covering the Ukrainian **one/few/many/other** plural categories (and `select` for gender) (DD-48).                       |
| FR-I18N-9 | UI strings are addressed through a **typed message-key registry** (no bare string literals at call sites), making referenced-but-undefined and defined-but-unused keys enumerable.                                                              |
| FR-I18N-10 | A `LayoutFitTest` checks the fit at 960×640 and 1920×1080 in `en` and `uk`, and holds the former title-bar fit test; the toolbar and dialog checks are built, the rest of the screens follow with their groups (JavaFX's headless screen is 1000 pixels high, so 1920×1080 runs 1920×1000).                  |
| FR-I18N-11 | Main-screen wording follows the Words table (`#words`): plain user vocabulary, "segment" kept, ICU plurals for every count, and in Ukrainian one idea per sentence and no gendered past tense for "you".                                      |

Note: UI language is independent of the source/target translation languages set in the Book Brief.

### locale-formatted fields {#locale-formatted-fields}

The following runtime-formatted values are locale-formatted for the selected UI locale (never assembled by string
concatenation):

| Field class       | Examples                                                               | Formatter                                                                           |
|-------------------|------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Counters / counts | "Review (3)", translated/to-review/left counts, glossary term counts   | `NumberFormat.getIntegerInstance(locale)` + ICU plural for the surrounding sentence |
| Sizes             | file sizes, token budgets shown to the user                            | `NumberFormat` (grouping) with a localized unit label from the bundle               |
| Durations         | time left, elapsed time on the Translating screen                      | localized from `java.time.Duration` parts + bundle unit labels                      |
| Dates / times     | project last-modified, export timestamp                                | `DateTimeFormatter.ofLocalizedDate/Time(...).withLocale(locale)`                    |
| Rates             | tokens/second (tok/s) in the title bar, with a plain tooltip           | `NumberFormat` + bundle unit label                                                  |

## accessibility {#accessibility}

| ID        | Requirement                                                                                                                                                                                                                                                                          | WCAG reference              |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| FR-A11Y-1 | Every interactive control is reachable and operable by keyboard alone, in a logical tab order.                                                                                                                                                                                       | 2.1.1 Keyboard              |
| FR-A11Y-2 | Keyboard focus is always visible with a clear focus indicator (a slate-blue ring outside the control).                                                                                                                                                                               | 2.4.7 Focus Visible         |
| FR-A11Y-3 | Text and essential UI elements meet AA contrast ratios in both light and dark themes. **Links** are always underlined and use `--info-fg` (at least 4.5:1 on every surface in both themes). The **selected segment** of a segmented control carries a 2 px `--text-strong` bar besides its fill (the fill alone is 1.24:1). **Scrollbars** are visible, 12 px wide, never self-hiding overlay bars, with a thumb that passes 3:1 (`mockups/README.md`, G3, G11). | 1.4.3 Contrast, 1.4.11 Non-text Contrast |
| FR-A11Y-4 | Interactive targets meet a **minimum target size of 24×24 px** (WCAG 2.5.8 AA); this is the single pinned threshold the conformance assertion checks.                                                                                                                                | 2.5.8 Target Size (Minimum) |
| FR-A11Y-5 | Controls expose accessible names/roles for screen readers via JavaFX accessibility properties.                                                                                                                                                                                       | 4.1.2 Name, Role, Value     |
| FR-A11Y-6 | Status and error information is conveyed by more than colour alone: every state is an **icon plus a word**, and one colour map is used everywhere (grey waiting · blue working · green done · amber needs you or slow · red failed or unreachable · sand kept as is; `mockups/README.md`, G11). | 1.4.1 Use of Color          |
| FR-A11Y-7 | Dialogs manage focus (initial focus set, focus trapped in the card with Tab wrapping, focus restored on close) and follow one key policy: **Esc** closes the dialog (on a progress dialog it hides long work, and asks first before losing an export), **Enter** — or Ctrl/⌘+Enter inside a text area — runs the footer's main button (`mockups/README.md`, G1). | 2.4.3 Focus Order           |
| FR-A11Y-8 | Critical information is never carried by a toast or banner alone (it is always also available in-context/in a dialog). While a dialog or visible work is open, its feedback is a line in its own footer rather than a toast. Screen-reader **announcement** of toasts/banners to assistive technology is a documented manual/late-phase check, not a CI-gated claim (see `#verification`). | 4.1.3 Status Messages       |
| FR-A11Y-9 | Short transitions are skipped when motion is reduced. There is **no Reduce motion setting in the UI**: reduced motion follows the operating system's preference, or an explicit `BOOKLOOM_REDUCE_MOTION` environment variable (else the `bookloom.reduceMotion` system property; `1`/`true`/`yes` on, `0`/`false`/`no` off), which wins over the OS (`Motion.isReduced()`, `modules/ui` `control/Motion.java`). No motion is the only carrier of a change. | 2.3.3 Animation from Interactions |
| FR-A11Y-10 | The review desk (a dialog) is keyboard-operable end to end: Ctrl/⌘+Enter runs its one main button (Accept & next, or Save my edit & next once edited) and Ctrl+Alt+↓/↑ moves between segments; Alt+↑/↓ is left to the editor on macOS (`mockups/README.md`, Dialog · Review, F1) — **planned — UI finalization + SQLite**. | 2.1.1 Keyboard              |

### primary controls per screen {#primary-controls}

"Primary controls" — the controls the accessibility assertions (`FR-A11Y-V*`) check per screen — are enumerated so the
checks are concrete rather than open-ended. The workflow has five numbered steps with Import holding the book's
structure; the review desk, the progress dialogs and the tests are dialogs on the modal host (G1), not screens.

| Screen / dialog | Primary controls                                                                                    |
|-----------------|-----------------------------------------------------------------------------------------------------|
| Projects (planned) | project list rows, New translation button                                                        |
| Import          | drop zone / file picker, the book's structure browser, Continue, and the state's banner action     |
| Book Brief      | target language picker, narrator choices, Back / Continue (the languages are locked once translating starts) |
| Names & style   | Glossary and Recurring terms tables with their Scan · Review · Translate bar, lock toggle, + Add, Back / Start translation |
| Translating     | Pause / Resume, Stop, Review (n), and the live panel's step list                                    |
| Export          | Save to path + Browse, "Also export" side files, Export book, Open folder                           |
| Review desk (dialog) | filter and ‹ › list, Original / Translation editors, the main button, More ▾                  |
| Settings        | tab bar and, per tab, its focusable fields; the pinned Save / Cancel footer on Providers and Models |

## verification {#verification}

| ID         | Requirement                                                                                                                                                                                                                                                    |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-A11Y-V1 | Contrast is verified on every conformance screen in both light and dark as a **gating** `:ui` test (`ContrastTest`; `09_THEMING.md#contrast`, FR-THEME-11), including link, status-word, selected-bar and scrollbar-thumb colours. |
| FR-A11Y-V2 | Keyboard reachability and focus behaviour are **advisory (non-gating)** TestFX assertions over the enumerated primary controls of each screen (`#primary-controls`): every primary control is tab-reachable in a logical order and shows a visible focus ring. |
| FR-A11Y-V3 | Accessible names are **advisory (non-gating)** checks for the primary controls of each screen (`#primary-controls`): each exposes a non-empty accessible name/role.                                                                                            |
| FR-A11Y-V4 | Target size (24×24 px, FR-A11Y-4) is an **advisory (non-gating)** geometry assertion over the primary controls.                                                                                                                                                |
| FR-A11Y-V5 | Screen-reader **announcement** behaviour (FR-A11Y-8) is verified as a **manual/late-phase** check with a real assistive technology, not asserted in CI.                                                                                                        |
| FR-A11Y-V6 | Text fit (FR-I18N-5, FR-I18N-10) is asserted by `LayoutFitTest` at 960×640 and 1920×1080 in `en` and `uk` — **planned — UI finalization + SQLite**.                                                                                                            |
