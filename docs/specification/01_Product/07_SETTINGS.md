**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`, `:persistence`), QA **Last
Updated:** 2026-09-27 **Cross-references:** `docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `docs/specification/01_Product/09_THEMING.md`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`

# Settings

This document specifies every setting, its default, and its validation range. Settings are persisted in the typed KV
store (DD-20, seam F7) and surfaced in the Settings screen's tabs. It realizes `FR-SETTINGS-*`. Every setting is
validated against its range and invalid input is rejected (FR-SETTINGS-07).

## settings-tabs {#settings-tabs}

The Settings screen has six tabs (`TabPane`): Providers, Models, Generation, Appearance, Automation, Storage & logs.

## providers-tab {#providers-tab}

| Setting                    | Type         | Default                  | Validation                                                    | Requirement             |
|----------------------------|--------------|--------------------------|-----------------------------------------------------------------|-------------------------|
| Provider list              | list         | empty                    | at least one to run a translation                             | FR-PROV-01              |
| Current provider           | reference    | none                     | must reference an existing, verified provider                 | FR-PROV-04              |
| Provider endpoint          | string (URL) | per preset               | valid URL; scheme http/https                                  | FR-PROV-03              |
| Auth scheme                | enum         | none                     | none / bearer / custom-header                                 | FR-PROV-03              |
| Credential reference       | string       | none                     | env-var name or keychain key (resolution planned); never a secret value | FR-PROV-05              |
| Effective context (tokens) | integer      | 8192 (fixed in this build) | not editable in this build; a per-provider value with its own validation range arrives with the Generation settings (future work) | FR-PROV-03, FR-INFER-02 |

**This build sends a fixed effective context of 8192 tokens** to Ollama's `num_ctx`; an OpenAI-compatible server
receives no context-size field (FR-MODEL-07, DD-44). The **effective context (tokens)** field, populated from discovery
or entered manually and used as the ceiling the chunk budget is clamped against, is not built in this version — it
arrives with the Generation tab's per-provider resolution.

## models-tab {#models-tab}

| Setting                       | Type    | Default          | Validation                                                                                            | Requirement                           |
|-------------------------------|---------|------------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------|
| Translator model              | string  | first discovered | non-empty; from live discovery or manual model-ID entry                                               | FR-MODEL-01, FR-MODEL-02, FR-MODEL-03 |
| Judge/helper model            | string  | translator model | non-empty when judge enabled; discovery or manual entry                                               | FR-MODEL-03                           |
| Manual model-ID entry         | string  | —                | always available; the only source when the provider has no discovery (`supportsModelDiscovery`=false) | FR-MODEL-02, DD-38                    |
| Per-provider remembered model | derived | last used        | —                                                                                                       | FR-MODEL-04                           |

These provider + model choices are the **defaults for new projects**; each project keeps its own binding thereafter
(`04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`, DD-31).

## generation-tab {#generation-tab}

| Setting                            | Type             | Default              | Validation range                                                                             | Requirement                 |
|--------------------------------------|------------------|-----------------------|------------------------------------------------------------------------------------------------|-----------------------------|
| num_ctx                            | integer (tokens) | 8192 (fixed in this build) | not user-editable in this build; the 512 – 131072 range and the 32768 default apply once the Generation tab's per-provider resolution is built (future work) | FR-SETTINGS-03, FR-INFER-02 |
| Temperature                        | decimal          | 0.2                  | 0.0 – 2.0                                                                                    | FR-SETTINGS-03, DD-36       |
| Chunk budget (cap)                 | integer (tokens) | 1200                 | fixed at 1200 in this build; the 256 – 8192 editable range arrives with the Generation tab | FR-ALGO-02, FR-SETTINGS-03  |
| Preceding blocks                   | integer          | dial-driven (1/2/3)  | not user-editable in this build; set by the quality dial (Fast/Balanced/Max) alone            | FR-ALGO-08                  |
| Manual τ override (advanced)       | decimal          | not offered in this build | 0.0 – 1.0, once built                                                                    | FR-REVIEW-02, DD-45         |
| Manual τ_judge override (advanced) | decimal          | not offered in this build | 0.0 – 1.0, once built                                                                    | FR-REVIEW-02, DD-45         |
| Repair budget N                    | integer          | dial-driven (1/2/3)  | not user-editable in this build; set by the quality dial alone                               | FR-ALGO-C12                 |

**This build sends a fixed effective context of 8192 tokens** to Ollama's `num_ctx` for every run (FR-MODEL-07,
DD-44); an OpenAI-compatible server receives no context-size field. There is **no per-provider effective context in
this build** — the 32768 default and the provider-permitting validation range above apply once the Generation tab and
its per-provider resolution (Ollama `num_ctx`/`/api/show` → discovery → a manual field → default) are built, as
`#providers-tab` describes. **Temperature is kept low for fidelity.** The default 0.2 favours deterministic,
low-hallucination translation; per-phase guidance (draft ~0.2, judge/QA ~0.0–0.2, repair/reflect may go slightly
higher) is in `05_TRANSLATION_ALGORITHM.md#generation-parameters` and `12_PROMPT_CATALOG.md`. Users may raise it
knowingly; the retry dialog also offers a one-off "lower temperature for this retry" (DD-36).

**τ ownership (DD-45).** The **trust threshold τ is owned by the review-mode dial** (Unattended 0.60 / Assisted 0.75 /
Manual 0.85), not the quality dial. The **quality dial (Fast/Balanced/Max) owns only mechanics** — chunk budget,
preceding-block count, repair budget N, judge on/off, and backward-revision on/off (FR-ALGO-11) — and does **not** set
τ. **Neither a manual τ override nor a manual mechanics override is offered in this build**; the Manual τ/τ_judge
override rows above are the reference target for when Settings persistence and this advanced override are built.

**Repair budget N = QA re-entry rounds.** N is the number of times a segment that fails QA is fed back for a directed
repair before it is flagged (FR-ALGO-C12), set by the quality dial alone in this build (1 / 2 / 3 on Fast / Balanced /
Max). **N = 0 means flag-on-first-failure** — no repair attempt is made; a segment that fails its first QA pass is
flagged immediately.

**Chunk budget is a fixed constant in this build.** `chunkBudget = min(8192 − reservedHeadroom, 1200)`
(`05_TRANSLATION_ALGORITHM.md#token-budget`), where reserved headroom accounts for the system prompt, preceding
context, and target room. The user-editable cap over a per-provider effective context arrives with the Generation tab.

**Token-budget constants (fixed in v1).** Token counts use a deterministic characters ÷ K heuristic (no shipped
tokenizer): the per-script **K table** and a **safety margin of `0.15`** are documented constants
(`05_TRANSLATION_ALGORITHM.md#chunking`), not user settings. The **rolling-summary window K** used by the context memory
is likewise a constant, **default 20**. These are recorded here for traceability; they are not exposed as editable
settings.

## appearance-tab {#appearance-tab}

| Setting       | Type              | Default                  | Validation                                                                              | Requirement           |
|---------------|-------------------|--------------------------|-----------------------------------------------------------------------------------------|-----------------------|
| Theme         | enum              | system                   | light / dark / system (JavaFX 26 reads OS `prefers-color-scheme`)                       | FR-SETTINGS-04, DD-22 |
| Accent        | fixed (read-only) | Cognac                   | not selectable in v1 (Cognac only, FR-THEME-4)                                          | FR-SETTINGS-04        |
| Language (UI) | enum              | OS locale on first start | English / Ukrainian; persisted as `ui.language`; applied on change (restart acceptable). **Deferred:** not offered in this build — see below | FR-UI-06, DD-34       |

**Theme control is tri-state here** (light / dark / system). The **toolbar theme control is a 2-state light↔dark
quick-toggle only**; the tri-state selector including `system` lives in this tab
(`08_UI_SCREENS_AND_STATES.md#shell-and-navigation`).

**The Language (UI) row is deferred.** The Appearance screen has no language control and nothing persists `ui.language`,
because persisting it needs local storage, which does not exist yet. Until then the interface language follows the
operating system's locale (Ukrainian OS → Ukrainian, otherwise English); the row stays here as the target behaviour.

**Accent is fixed to Cognac** in v1 and shown read-only; it is not a selectable enum (see `09_THEMING.md` FR-THEME-4).
**Density is deferred to a later version** and is not offered in v1 Appearance — no spacing/density tokens exist in the
token catalogue (`09_THEMING.md#token-catalog`), so a compact mode would have nothing to switch.

## automation-tab {#automation-tab}

This is the authoritative Automation list. There is **no "desktop notification on finish" toggle and no "single instance
only" toggle** in v1: there is no OS-notification feature (`11_NOTIFICATIONS_AND_ERRORS.md`), and single-instance is
**always on** and not user-configurable (enforced by the single-instance lock in the lifecycle spec).

| Setting                        | Type    | Default    | Validation                                          | Requirement  |
|--------------------------------|---------|------------|------------------------------------------------------|--------------|
| Default review mode            | enum    | Unattended | Unattended / Assisted / Manual; in this build the value comes from `BOOKLOOM_REVIEW_MODE` (or the system property `bookloom.review.mode`), resolved once at launch, until Settings persist the choice | FR-REVIEW-01 |
| Default quality dial           | enum    | Balanced   | Fast / Balanced / Max                               | FR-BRIEF-08  |
| Foreign-passage policy default | enum    | keep-as-is | keep-as-is / translate / translate+note (3 options) | FR-BRIEF-04  |
| Final consistency pass default | boolean | off        | on / off                                            | FR-EXPORT-06 |
| Resume-on-launch               | boolean | on         | on / off                                            | FR-RESUME-03 |

## storage-and-logs-tab {#storage-and-logs-tab}

| Setting              | Type                     | Default             | Validation                  | Requirement    |
|----------------------|--------------------------|---------------------|------------------------------|----------------|
| Data directory       | path (read-only display) | per-OS app data dir | must exist and be writable  | FR-PERSIST-03  |
| Log directory        | path (read-only display) | per-OS log dir      | must exist and be writable  | DD-23          |
| Log level            | enum                     | INFO (DEBUG in a development run) | ERROR / WARN / INFO / DEBUG / TRACE | FR-SETTINGS-06 |
| Open data/log folder | action                   | —                   | —                           | FR-SETTINGS-06 |

`BOOKLOOM_LOG_LEVEL`, or else the system property `bookloom.log.level`, overrides the log level for one run, and is the
only way to set it until this tab exists. `TRACE` is the only level that writes book text, prompts and model replies to
the log (`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored`).

## validation-behaviour {#validation-behaviour}

| ID             | Requirement                                                                                                             |
|----------------|-----------------------------------------------------------------------------------------------------------------------|
| FR-SETTINGS-V1 | Any setting entered outside its range is rejected with an inline validation message; the prior valid value is retained. |
| FR-SETTINGS-V2 | Settings persist to the typed KV store and reload on next launch.                                                       |
| FR-SETTINGS-V3 | A UI-language change takes effect only after an application restart (see `10_I18N_AND_ACCESSIBILITY.md`).               |
