**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`), UX, QA **Last Updated:** 2026-07-18
**Cross-references:** `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`,
`docs/specification/01_Product/09_THEMING.md`, `docs/specification/01_Product/07_SETTINGS.md`,
`docs/specification/02_Architecture/06_DATA_MODEL_SQLITE.md`

# Internationalization and Accessibility

This document specifies i18n and accessibility requirements for the UI, realizing `FR-UI-06` and `FR-UI-07`. Targets are
WCAG 2.1 AA.

## internationalization {#internationalization}

This version ships exactly **two** UI languages (DD-34): **English** — the default and base bundle — and **Ukrainian**
(locale `uk`). All user-facing text is provided as `ResourceBundle`s, `messages_en` and `messages_uk`; the two bundles
are kept in parity so every displayed string exists in both. Adding further languages later is a matter of adding a
bundle and listing its locale, but no other language is in scope now.

On first start the application detects the OS locale through an **injectable `Locale` provider** (not a direct
`Locale.getDefault()` call at the use site): a Ukrainian OS locale selects `uk`, and anything else selects English. The
chosen locale is then persisted and, on every subsequent launch, the stored choice — not the OS locale — governs.
Because the OS locale is read through the injectable provider, first-start detection is **unit-testable**: a test
supplies a fake provider returning `uk`/`en`/other and asserts the resolved language without touching the real machine
locale.

### plural and gender handling (ICU) {#icu-messages}

Plural- and gender-sensitive strings use **ICU4J `MessageFormat`** (ICU4J is already a dependency; DD-48), not Java's
positional `MessageFormat` or manual `if (n == 1)` branching. Bundle values for such keys are **ICU message patterns**
(`{count, plural, one {…} few {…} many {…} other {…}}`, `{gender, select, …}`), so Ukrainian pluralization — which needs
the **one / few / many / other** categories — is expressed once in the bundle rather than in code. English uses its own
subset (one/other). This keeps counters, item counts, and gendered notices grammatically correct in both locales without
string concatenation.

All keys are addressed through a **typed message-key registry** — generated/enumerated constants, never bare string
literals at call sites — so that "a key a controller/FXML references but no bundle defines" (and the reverse) is a
finite, **enumerable** set the i18n/parity test walks (`04_Build_and_Release/06_TESTING_STRATEGY.md#i18n-tests`).

| ID        | Requirement                                                                                                                                                                                                                                     |
|-----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-I18N-1 | All user-facing text is sourced from `ResourceBundle`s (`messages_en`, `messages_uk`); no hard-coded display strings in controllers or FXML.                                                                                                    |
| FR-I18N-2 | The two UI languages are English (default) and Ukrainian (`uk`); the language is user-selectable in Settings → Appearance (FR-SETTINGS-04).                                                                                                     |
| FR-I18N-3 | Changing the UI language applies on change; a restart to fully re-render is acceptable, and the user is informed when one is required (FR-SETTINGS-V3).                                                                                         |
| FR-I18N-4 | On first start the default UI language follows the OS locale — Ukrainian OS locale → `uk`, otherwise English (the base bundle) — resolved via an **injectable `Locale` provider** so the rule is unit-testable without the real machine locale. |
| FR-I18N-5 | Layouts accommodate variable text length across the English and Ukrainian locales without truncation of essential labels.                                                                                                                       |
| FR-I18N-6 | Locale-sensitive formatting uses the selected locale for the enumerated **locale-formatted fields** (below), via `NumberFormat` and `java.time`/`DateTimeFormatter`, never hand-built strings.                                                  |
| FR-I18N-7 | The selected language is persisted in the typed KV settings store under the key `ui.language` and restored on next launch (`06_DATA_MODEL_SQLITE.md#settings`).                                                                                 |
| FR-I18N-8 | Plural- and gender-sensitive strings are rendered with **ICU4J `MessageFormat`**; their bundle values are ICU patterns covering the Ukrainian **one/few/many/other** plural categories (and `select` for gender) (DD-48).                       |
| FR-I18N-9 | UI strings are addressed through a **typed message-key registry** (no bare string literals at call sites), making referenced-but-undefined and defined-but-unused keys enumerable.                                                              |

Note: UI language is independent of the source/target translation languages set in the Book Brief.

### locale-formatted fields {#locale-formatted-fields}

The following runtime-formatted values are locale-formatted for the selected UI locale (never assembled by string
concatenation):

| Field class       | Examples                                                               | Formatter                                                                           |
|-------------------|------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Counters / counts | "Flagged (3)", accepted/flagged/remaining counts, glossary term counts | `NumberFormat.getIntegerInstance(locale)` + ICU plural for the surrounding sentence |
| Sizes             | file sizes, token budgets shown to the user                            | `NumberFormat` (grouping) with a localized unit label from the bundle               |
| Durations         | ETA, elapsed time on the Translating dashboard                         | localized from `java.time.Duration` parts + bundle unit labels                      |
| Dates / times     | project last-modified, export timestamp                                | `DateTimeFormatter.ofLocalizedDate/Time(...).withLocale(locale)`                    |
| Rates             | tokens/second (tok/s)                                                  | `NumberFormat` + bundle unit label                                                  |

## accessibility {#accessibility}

| ID        | Requirement                                                                                                                                                                                                                                                                          | WCAG reference              |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| FR-A11Y-1 | Every interactive control is reachable and operable by keyboard alone, in a logical tab order.                                                                                                                                                                                       | 2.1.1 Keyboard              |
| FR-A11Y-2 | Keyboard focus is always visible with a clear focus indicator.                                                                                                                                                                                                                       | 2.4.7 Focus Visible         |
| FR-A11Y-3 | Text and essential UI elements meet AA contrast ratios in both light and dark themes.                                                                                                                                                                                                | 1.4.3 Contrast              |
| FR-A11Y-4 | Interactive targets meet a **minimum target size of 24×24 px** (WCAG 2.5.8 AA); this is the single pinned threshold the conformance assertion checks.                                                                                                                                | 2.5.8 Target Size (Minimum) |
| FR-A11Y-5 | Controls expose accessible names/roles for screen readers via JavaFX accessibility properties.                                                                                                                                                                                       | 4.1.2 Name, Role, Value     |
| FR-A11Y-6 | Status and error information is conveyed by more than colour alone (icon/text plus the status token).                                                                                                                                                                                | 1.4.1 Use of Color          |
| FR-A11Y-7 | Dialogs manage focus (initial focus set, focus trapped while modal, focus restored on close).                                                                                                                                                                                        | 2.4.3 Focus Order           |
| FR-A11Y-8 | Critical information is never carried by a toast or banner alone (it is always also available in-context/in a dialog). Screen-reader **announcement** of toasts/banners to assistive technology is a documented manual/late-phase check, not a CI-gated claim (see `#verification`). | 4.1.3 Status Messages       |

### primary controls per screen {#primary-controls}

"Primary controls" — the controls the accessibility assertions (`FR-A11Y-V*`) check per screen — are enumerated so the
checks are concrete rather than open-ended:

| Screen        | Primary controls                                                                                    |
|---------------|-----------------------------------------------------------------------------------------------------|
| Projects      | project list rows, New/Import project button                                                        |
| Import        | drop zone / file picker, Continue, and the state's banner action                                    |
| Book Brief    | source/target language pickers, faithful↔natural slider, quality dial, the "Also translate" toggles |
| Structure     | chapter `TreeView`                                                                                  |
| Names & Style | glossary table, lock toggle, Add-term button                                                        |
| Translating   | Pause/Resume, Stop, and (in `stopped`) the Resume action                                            |
| Review        | flagged list, target `TextArea`, Save edit / Accept / Revert / Retry / Retry-with-note / Skip       |
| Export        | save-path field + picker, "Also export" toggles, Export button                                      |
| Settings      | tab bar and, per tab, its focusable fields                                                          |

## verification {#verification}

| ID         | Requirement                                                                                                                                                                                                                                                    |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| FR-A11Y-V1 | Contrast is verified against the theme tokens in both light and dark, as an **advisory (non-gating)** assertion (see `09_THEMING.md#token-catalog`).                                                                                                           |
| FR-A11Y-V2 | Keyboard reachability and focus behaviour are **advisory (non-gating)** TestFX assertions over the enumerated primary controls of each screen (`#primary-controls`): every primary control is tab-reachable in a logical order and shows a visible focus ring. |
| FR-A11Y-V3 | Accessible names are **advisory (non-gating)** checks for the primary controls of each screen (`#primary-controls`): each exposes a non-empty accessible name/role.                                                                                            |
| FR-A11Y-V4 | Target size (24×24 px, FR-A11Y-4) is an **advisory (non-gating)** geometry assertion over the primary controls.                                                                                                                                                |
| FR-A11Y-V5 | Screen-reader **announcement** behaviour (FR-A11Y-8) is verified as a **manual/late-phase** check with a real assistive technology, not asserted in CI.                                                                                                        |
