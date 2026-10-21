**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`, `:persistence`), QA **Last
Updated:** 2026-10-19 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `docs/specification/01_Product/09_THEMING.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/specification/mockups/README.md` (Settings,
decisions 6-7, H1)

# Settings

This document specifies every setting, its default, and its validation range. Settings are to be persisted in the typed
KV store (DD-20, seam F7) and surfaced in the Settings screen's tabs. It realizes `FR-SETTINGS-*`. Every setting is
validated against its range and invalid input is rejected (FR-SETTINGS-07).

**Status of persistence.** Nothing is saved in this build: the provider list is the two read-only presets and every
other value is a constant or a launch flag, so everything resets on restart (ADR-0034). Saving the settings below is
**planned — UI finalization + SQLite**
(`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). Rows marked *planned* describe that change; rows
marked *built* describe the code today.

## settings-tabs {#settings-tabs}

The Settings screen has six tabs (`TabPane`): **Providers, Models, Generation, Appearance, Automation, Storage & logs**
(`mockups/README.md`, Settings). Its subtitle reads "Your books stay on this computer. BookLoom sends your text only to the AI
model you choose here." *Built today:* Providers (read-only presets with the three tests), Appearance (theme);
the Models, Generation, Automation and Storage & logs tabs are *planned — UI finalization + SQLite*.

**How changes are saved (H1, planned).**

- **Providers** and **Models** save explicitly through a pinned form footer: "Unsaved changes" marker, Cancel, Save.
  Leaving them with changes (another provider, another tab, navigation, closing the window) asks: Keep editing ·
  Discard · Save (FR-SETTINGS-V1 applies on Save).
- **Generation, Appearance, Automation, Storage & logs** apply at once and say when: "Changes apply at once", "to the
  segments not translated yet", or "after BookLoom restarts".
- **Credentials are never saved**, only the name of an environment variable (FR-PROV-05;
  `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored`). A key field shows only "set" / "not set".

## providers-tab {#providers-tab}

*Planned — UI finalization + SQLite.* Today the list is two read-only presets and the tab runs the three tests.

The tab is a list | form split that stays side by side at every width. The detail has a header (the provider's name,
"current" or **Use this one**, **Delete…** with a confirmation), a **summary card** ("Your AI model: Ollama · gemma4:e4b
— ready · Answers in about 1 second · 7 models available", with Test and Change model), and a **Connection details**
fold around the unchanged GoText-style form. The fold opens by itself when there is a problem, for a new provider, or
with Show technical details. Test results are in plain words with a **Details** link to the test dialog (request line,
body, status, time, first token, speed, response).

| Setting                    | Type         | Default                  | Validation                                                    | Requirement             |
|----------------------------|--------------|--------------------------|-----------------------------------------------------------------|-------------------------|
| Provider list              | list         | two presets (Ollama, LM Studio-style OpenAI-compatible) | at least one to run a translation; add, edit, delete | FR-PROV-01              |
| Current provider           | reference    | none                     | must reference an existing, verified provider                 | FR-PROV-04              |
| Name                       | string       | per preset               | non-empty                                                     | FR-PROV-03              |
| Kind                       | enum         | per preset               | Ollama native / OpenAI-compatible                             | FR-PROV-03              |
| Provider endpoint (Address) | string (URL) | per preset              | valid URL; scheme http/https; a warning when an OpenAI-compatible URL lacks `/v1` | FR-PROV-03 |
| Models endpoint / Inference endpoint | string (path) | provider default | optional overrides; the inference one is disabled for Ollama | FR-PROV-03 |
| Connect and request timeouts | integer (seconds) | per preset      | positive                                                      | FR-PROV-03              |
| Sign-in (auth scheme)      | enum         | none                     | none / bearer token / API-key header (with its header name)   | FR-PROV-03              |
| Credential reference       | string       | none                     | **name of an environment variable**; never a secret value; shown "set" / "not set"; checked before a call; OS keychain is a later option | FR-PROV-05 |
| Custom headers             | list (name, value) | empty              | values may be `${env:NAME}` references, never literal secrets | FR-PROV-03              |
| Custom models              | list         | empty                    | extra model ids offered in the model list                     | FR-MODEL-02             |

The effective context is **not** a provider setting: it is resolved per model (see `#models-tab` and
`#generation-tab`).

## models-tab {#models-tab}

*Planned — UI finalization + SQLite* (mockups/README.md, decision 6). The Models tab is a **list of models across
providers** (with ↻ Refresh) and a form for the picked one. **Each model keeps its own settings, all optional — off
means BookLoom picks the value per task.** The tab also shows what was found out from the program: how much the model
can read at once (and how much is loaded now) and whether it gives structured answers.

| Setting                       | Type    | Default          | Validation                                                                                            | Requirement                           |
|-------------------------------|---------|------------------|-------------------------------------------------------------------------------------------------------|---------------------------------------|
| Model (per provider)          | string  | first discovered | non-empty; from live discovery or manual model-ID entry                                               | FR-MODEL-01, FR-MODEL-02, FR-MODEL-03 |
| Manual model-ID entry         | string  | —                | always available; the only source when the provider has no discovery (`supportsModelDiscovery`=false) | FR-MODEL-02, DD-38                    |
| Temperature                   | choice + decimal | Let BookLoom choose (per-task defaults of `ChatRequests`) | Let BookLoom choose · My value (0.0 – 2.0, one value for every request) · Don't send (omitted from the request, for models that refuse it); planned — mockups/README.md S6 | FR-SETTINGS-03, DD-36                 |
| Use context window            | integer (tokens) | off; filled in from what is loaded (`ModelCapabilities.contextLength`), shown as "detected" | positive; sent as `num_ctx` on Ollama | FR-SETTINGS-03, FR-INFER-02 |
| Limit answer length           | integer (tokens) | off (computed per request by `OutputLimit`) | positive when on                                                         | FR-SETTINGS-03                        |
| Answer-length field (advanced) | enum   | `max_tokens`     | `max_tokens` / `max_completion_tokens`; disabled for Ollama                                           | FR-SETTINGS-03                        |
| Per-provider remembered model | derived | last used        | —                                                                                                     | FR-MODEL-04                           |

There is **no separate judge or helper model**: the reviewer and every other call use the project's one chosen model.
These provider + model choices are the **defaults for new projects**; each project keeps its own binding thereafter
(`04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`, DD-31).

## generation-tab {#generation-tab}

*Planned — UI finalization + SQLite.* The tab applies to segments not translated yet and carries only knobs the code
has: no judge model and no τ override.

| Setting                  | Type          | Default                | Validation / meaning                                                                 | Requirement  |
|--------------------------|---------------|------------------------|---------------------------------------------------------------------------------------|--------------|
| What the quality choice does | read-only grid | —                  | the Fast / Balanced / Max rows of `DialParameters` (see below)                        | FR-ALGO-C12  |
| Thinking before answering | enum         | Off                    | Off / Low / On, read at each run start (`RequestDefaultsSource`); `BOOKLOOM_REASONING`, when set, replaces it              | FR-SETTINGS-03 |
| Sampling                 | enum          | per `SamplingMode`     | Program defaults / Tuned by BookLoom, read at each run start; `BOOKLOOM_SAMPLING`, when set to a known mode, replaces it          | FR-SETTINGS-03 |
| Time limits              | read-only     | `RequestTimeouts`      | fixed per call kind                                                                   | FR-SETTINGS-03 |

**Context window (built).** Every call is sized against a fixed effective context of **16,384 tokens**
(`TokenBudget.EFFECTIVE_CONTEXT`), sent to Ollama's `num_ctx`; an OpenAI-compatible server receives no context-size
field (FR-MODEL-07, DD-44). The detected window is used only for budgeting. A per-model override that replaces it is the
Models tab's **Use context window** (planned).

**Temperature** is a constant per call kind today (`ChatRequests`), low for fidelity: draft ~0.2, QA ~0.0–0.2, repair
slightly higher (`05_TRANSLATION_ALGORITHM.md#generation-parameters`, `12_PROMPT_CATALOG.md`). The Models tab's **Use
temperature** choice (planned, S6) keeps those defaults (Let BookLoom choose), replaces them with one value (My value), or leaves the parameter out entirely (Don't send) for models that refuse it — some newer OpenAI and Anthropic models do; a model that refuses any optional setting is handled by the refused-settings rule (mockups/README.md, "Refused settings (S6)"), which drops it, repeats the request once and remembers it per provider + model; the retry dialog also offers a one-off "lower
temperature for this retry" (DD-36).

**What the quality dial sets (built, `DialParameters`).** The dial (Fast/Balanced/Max) owns only mechanics, and is not
user-editable per knob:

| Dial     | Preceding targets | Repair rounds N | Reviewer reads per chunk | Backward revision | Model-written summary | Chunk cap (segments) |
|----------|-------------------|-----------------|--------------------------|-------------------|-----------------------|----------------------|
| Fast     | 1                 | 1               | 0 (no reviewer)          | off               | off                   | 8                    |
| Balanced | 2                 | 2               | 1                        | off               | off                   | 8                    |
| Max      | 3                 | 3               | 2                        | on                | on                    | 2                    |

Manual review mode caps a chunk at 1 segment. The chunk's token ceiling is `TokenBudget.MAX_CHUNK_TOKENS` = **2000**
source tokens (`05_TRANSLATION_ALGORITHM.md#token-budget`).

**τ ownership (DD-45).** The **trust threshold τ is owned by the review-mode dial** (Unattended 0.60 / Assisted 0.75 /
Manual 0.85), not the quality dial. **No manual τ or τ_judge override is offered**, now or in the planned Generation
tab.

**Repair budget N = QA re-entry rounds.** N is the number of times a segment that fails QA is fed back for a directed
repair before it is flagged (FR-ALGO-C12), set by the quality dial alone (the table above). **N = 0 means
flag-on-first-failure**; a review retry runs with N = 0 (`DialParameters.withoutRepairRounds`).

**Token-budget constants (fixed).** Token counts use a deterministic characters ÷ K heuristic (no shipped tokenizer):
the per-script **K table** and a **safety margin of `0.15`** are documented constants
(`05_TRANSLATION_ALGORITHM.md#chunking`), not user settings. The **rolling-summary window K** is likewise a constant,
**default 20**. These are recorded here for traceability; they are not editable settings.

## appearance-tab {#appearance-tab}

| Setting                 | Type    | Default                  | Validation                                                                              | Requirement           | Status |
|-------------------------|---------|--------------------------|-----------------------------------------------------------------------------------------|-----------------------|--------|
| Theme                   | enum    | system                   | light / dark / system (JavaFX 26 reads OS `prefers-color-scheme`)                       | FR-SETTINGS-04, DD-22 | built (not saved) |
| Language of the app (UI) | enum   | OS locale on first start | English / Ukrainian; persisted as `ui.language`; **applies after BookLoom restarts** ("Applies after BookLoom restarts." with a **Restart now** action) | FR-UI-06, DD-34 | planned |
| Show technical details  | boolean | off                      | on / off; every Simple / Detailed view (live panel, progress dialogs, review desk) opens in Detailed, the Settings folds open, and the technical lines show (rule codes in the review desk, debug lines in the log, the technical line on Import) (`mockups/README.md`, G12) | FR-SETTINGS-04 | planned |

**Theme control is tri-state here** (light / dark / system). The **toolbar theme control is a 2-state light↔dark
quick-toggle only**; the tri-state selector including `system` lives in this tab
(`08_UI_SCREENS_AND_STATES.md#shell-and-navigation`).

**The language switch needs a restart** because a live switch would rebuild every view. Until the setting is persisted,
the interface language follows the operating system's locale (Ukrainian OS → Ukrainian, otherwise English).

**There is no Accent row and no Reduce motion row.** The accent is fixed (Cognac only, `09_THEMING.md` FR-THEME-4) and
is not shown as a setting; reduced motion follows the operating system (or `BOOKLOOM_REDUCE_MOTION`). **Density is
deferred**: no spacing/density tokens exist in the token catalogue (`09_THEMING.md#token-catalog`).

## automation-tab {#automation-tab}

*Planned — UI finalization + SQLite* (these rows apply at once). This is the authoritative Automation list. There is
**no "desktop notification on finish" toggle and no "single instance only" toggle**: there is no OS-notification
feature (`11_NOTIFICATIONS_AND_ERRORS.md`), and single-instance is **always on** and not user-configurable (the
single-instance lock in the lifecycle spec).

| Setting                        | Type    | Default    | Validation                                          | Requirement  |
|--------------------------------|---------|------------|------------------------------------------------------|--------------|
| When to stop and ask you (default review mode) | enum | Never — translate unattended | Never — translate unattended / When something needs review / After every segment (`ReviewMode` UNATTENDED / ASSISTED / MANUAL). *Built today:* the value comes from `BOOKLOOM_REVIEW_MODE` (or the system property `bookloom.review.mode`), resolved once at launch | FR-REVIEW-01 |
| Default quality                | enum    | Balanced   | Fast / Balanced / Max                               | FR-BRIEF-08  |
| Open the review desk when a translation finishes with segments to review | boolean | on | on / off          | FR-REVIEW-01 |
| Run the last check before saving by default (final consistency pass) | boolean | off | on / off     | FR-EXPORT-06 |
| Keep the computer awake        | boolean | on         | on / off; the lid note: closing a laptop lid still puts it to sleep, and translating goes on by itself after sleep | FR-RESUME-03 |
| Reopen the last project (resume on launch) | boolean | on | on / off                                 | FR-RESUME-03 |
| Foreign-passage policy default | enum    | keep-as-is | keep-as-is / translate / translate+note (3 options) | FR-BRIEF-04  |

## storage-and-logs-tab {#storage-and-logs-tab}

*Planned — UI finalization + SQLite* (paths are shown short with the full path as a tooltip).

| Setting              | Type                     | Default             | Validation                  | Requirement    |
|----------------------|--------------------------|---------------------|------------------------------|----------------|
| Your projects: saved in, size | path and size (read-only display) | per-OS app data dir | must exist and be writable; "saved as it goes" | FR-PERSIST-03 |
| Open folder / Make a backup… | action            | —                   | backup copies the database   | FR-PERSIST-03  |
| Logs: folder         | path (read-only display) | per-OS log dir      | must exist and be writable; the last 10 launches are kept | DD-23 |
| Detailed diagnostic log | boolean               | off in an installed app, on in a development run | on / off (`BOOKLOOM_TRACE_FILE`) | FR-SETTINGS-06 |
| Open log folder / Save a diagnostic bundle… | action | —                | —                            | FR-SETTINGS-06 |

*Built today:* the log folder and the files in it exist, but nothing in the UI shows them. The log level is not a
setting on this tab: `BOOKLOOM_LOG_LEVEL`, or else the system property `bookloom.log.level`, sets it for one run
(`.claude/rules/logging.md`; default INFO for an installed app, TRACE for a development run). `TRACE` is the only level
that writes book text, prompts and model replies to the log
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored`).

## validation-behaviour {#validation-behaviour}

| ID             | Requirement                                                                                                             |
|----------------|-----------------------------------------------------------------------------------------------------------------------|
| FR-SETTINGS-V1 | Any setting entered outside its range is rejected with an inline validation message; the prior valid value is retained. |
| FR-SETTINGS-V2 | Settings persist to the typed KV store and reload on next launch (*planned — UI finalization + SQLite*; secrets are stored as references only). |
| FR-SETTINGS-V3 | A UI-language change takes effect only after an application restart (see `10_I18N_AND_ACCESSIBILITY.md`).               |
