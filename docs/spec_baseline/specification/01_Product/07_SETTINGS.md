**Status:** Final **Owner:** architect **Audience:** architect, engineering (`:ui`, `:persistence`), QA **Last
Updated:** 2026-10-29 **Cross-references:** `docs/spec_baseline/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md`,
`docs/spec_baseline/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `docs/spec_baseline/specification/01_Product/09_THEMING.md`,
`docs/spec_baseline/specification/01_Product/08_UI_SCREENS_AND_STATES.md`, `docs/spec_baseline/specification/mockups/README.md` (Settings,
decisions 6-7, H1)

# Settings

This document specifies every setting, its default, and its validation range. Settings are to be persisted in the typed
KV store (DD-20, seam F7) and surfaced in the Settings screen's tabs. It realizes `FR-SETTINGS-*`. Every setting is
validated against its range and invalid input is rejected (FR-SETTINGS-07).

**Status of persistence.** Built (finalize group 3 and 7): in the window every setting below is kept in SQLite
(`bookloom.db`, ADR-0043) and survives a restart; the command line keeps nothing (ADR-0034). The detailed-log switch is
the one exception: logging starts before the database opens, so it is a marker file (`detailed-log.on`) in the data
folder. Rows marked *built* describe the code today.

## settings-tabs {#settings-tabs}

The Settings screen has six tabs (`TabPane`): **Providers, Models, Generation, Appearance, Automation, Storage & logs**
(`mockups/README.md`, Settings). Its subtitle reads "Your books stay private when you use a local model. BookLoom sends your text only to the AI
model you choose here." (never that the text always stays on this computer; ADR-0047). All six are built: Providers (form, checks, test dialog), Models, Generation, Appearance,
Automation and Storage & logs.

**How changes are saved (H1, planned).**

- **Providers** and **Models** save explicitly through a pinned form footer: "Unsaved changes" marker, Cancel, Save.
  Leaving them with changes (another provider, another tab, navigation, closing the window) asks: Keep editing ·
  Discard · Save (FR-SETTINGS-V1 applies on Save).
- **Generation, Appearance, Automation, Storage & logs** apply at once and say when: "Changes apply at once", "to the
  segments not translated yet", or "after BookLoom restarts".
- **Credentials are never saved**, only the name of an environment variable (FR-PROV-05;
  `03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored`). A key field shows only "set" / "not set".

## providers-tab {#providers-tab}

*Built (finalize 7.1a and 7.1b; the engine rows, 5.8 of `redesign-translation-pipeline`).* The tab runs the three tests
and the optional Advanced features check.

The tab is a list | form split that stays side by side at every width. The detail has a header (the provider's name,
"current" or **Use this one**, **Delete…** with a confirmation), a **summary card** ("Your AI model: Ollama · gemma4:e4b
— ready · Answers in about 1 second · 7 models available", with Test and Change model), and a **Connection details**
fold around the unchanged GoText-style form. The fold opens by itself when there is a problem, for a new provider, or
with Show technical details. Each provider keeps the model last saved or chosen on it (stored per provider beside the
current model), so a provider that stops being the current one, and **Use this one** later, comes back with its model.
Test results are in plain words with a **Details** link to the test dialog (request line,
body, status, time, model load, first token, speed, response).

The two tests that use the model, **Test inference** and **Test advanced features**, each have two buttons: the quiet
one runs the test and leaves its result sentence beside the button, and **Test with details** runs it and opens the test
dialog at once (the summary card's Test is the detailed one). Neither opens the generic progress dialog; the test still
counts as model work (one at a time, the title bar's chip, refused while a translation is translating: no request is sent, the line under the buttons and the summary card read "BookLoom is translating. Try again when the run ends or while it is paused."; a paused, stopped or ended run lets it run; Test connection and Test models are never refused; other model actions keep "Pause the translation first…") and keeps the
navigation locked while it runs. In the dialog the request line and the body appear as soon as the request is sent;
the Response column reads "Waiting for the model" and the Time figure counts up until the reply comes, then the verdict,
the reply and the figures fill in. While the test runs the dialog offers **Stop**; Close (or Escape) then asks "Close
while the test is running?" with **Stop and close** and **Close and keep running** (the result then shows beside the
button). JSON bodies and replies are shown indented (Jackson's own pretty printer, in `:llm`; any other text is shown
as it came); Copy takes the indented text and **Copy as curl** the body exactly as it was sent. Ollama's reported model
load time is its own figure, **Model load**, so a slow first call is explained.

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

**Server engine.** Under the address one line names the program found there: "Detected: Ollama 0.40.11 · native
connection", "Detected: llama-server b11669 · OpenAI-compatible", or "Not recognised: generic OpenAI-compatible
server". It is read from the shape of the server's replies on Save, on a test and on **Detect again** (beside the line;
a few GETs, never a model request, so it also works while a translation runs; with no answer the line keeps its text
and says the server does not answer). **Treat this server as…** (As detected · Ollama · LM Studio · llama-server ·
Unsloth · OpenRouter · generic OpenAI-compatible) names the engine when detection is wrong, for example behind a proxy;
detection never overwrites it, Detect again clears it, and it is not offered for an app that cannot translate a book
(GPT4All, AnythingLLM's documents path). When Ollama answers a provider saved as OpenAI-compatible, the form offers once
"This is Ollama. Use Ollama's own connection (recommended)? It keeps the window, overflow and sampling controls." —
**Use it** switches the kind to Ollama and drops `/v1` from the address, **Keep as is** only declines the offer, which
never returns (also after Detect again); both are draft edits kept on Save. A server on another port or another computer on the
network is added by hand: the person picks the provider type and the engine is read from the replies. The first-launch
look (ADR-0044) covers only the two default loopback addresses.

**Advanced features (optional, uses the model).** A fourth check, never started by the other three, sends a few short
requests with invented text and lists one verdict per check it ran: a recognised engine runs two (structured answers,
turning reasoning off), a generic server or OpenRouter four ("Structured answers: honoured", "Turning reasoning off:
works with reasoning_effort", "top_k/min_p: ignored", "Answer cap: max_tokens"). The verdicts are kept on the model.
Like Test inference it is not started while a translation is translating (same sentence, no request sent) and runs while the run is paused, stopped or ended. The test
dialog's request line is the route the engine was actually reached on (LM Studio's `/api/v0/chat/completions`). Its
dialog opens with one paragraph on what the test is for and that it costs a few short model calls, then one row per
check it ran: what was asked ("Sent a request that asks for a JSON answer in a fixed shape."), what came back ("The
answer was exactly the fixed shape.") and the verdict (honoured, ignored or refused), with one line per call under it
("Call 1: 1.2 s · 34.0 tok/s", or the status of a call the server answered with an error). The Body and Response
columns list every call, retries included, each under a heading such as "Structured answers, call 2", as it is sent.

| Setting                    | Type         | Default                  | Validation                                                    | Requirement             |
|----------------------------|--------------|--------------------------|-----------------------------------------------------------------|-------------------------|
| Treat this server as…      | enum, optional | As detected            | one of the six engines; not offered for an unusable app      | FR-PROV-03              |
| Ollama's own connection offer | flag      | open                     | Keep as is declines it for good                               | FR-PROV-03              |

**Engine help.** Each engine gets the help it needs, in the display language. **llama-server**: the empty address
field shows the hint `http://127.0.0.1:9931/v1` (no fixed port; the address ends in `/v1`). **LM Studio**: when the id
check stops a test (the models stage) or the first call of a run because LM Studio does not list the id, the result reads `LM Studio has no model
"{id}". It would answer with the model that is loaded. Pick a model from the list.` (a run pauses on that first call, ending with `modelNotFound`, reason `model-not-listed`); a typed `@variant` is removed from
the model id once LM Studio's list was read (a passing models check) or the provider is saved, not while typing, and one line under the
model entry says `"@{variant}" was removed from the model id: it would load a second copy with default settings.`
until the id is typed again; right after a run starts on an LM Studio model that is not loaded, a toast (information, never blocking) says "LM Studio will
load it with its own default settings; the window may differ." and the run goes on. **Unsloth**: under the address the
key hint "Create a key in Unsloth › Settings › API and put its environment-variable name here, for example
UNSLOTH_API_KEY." and "Unsloth's own chat tools (web search, code) are separate from BookLoom; BookLoom turns them off
on every request."; a refused sign-in reads "Unsloth needs an API key. Create one in Unsloth › Settings › API and put
its environment-variable name here." Each line follows the engine requests are shaped for (the person's choice, else
the detected engine), never the port or the name.

**Privacy.** Under the summary card one line says where the text goes: "Your book stays private when you use a local
model." for a provider at `127.0.0.1`, `::1` or `localhost`, and for any other host "… With a provider outside this
computer, the text you translate is sent to {host}." (for example `openrouter.ai`). For LM Studio a second line says
"LM Studio keeps request text in its own server log unless you turn that off in LM Studio." Nothing is asked: there is
no consent dialog and no acknowledgement box for any provider.

**Cloud models.** In the Providers model list and its model field a model the provider runs on its own servers (an Ollama
cloud model, `ModelInfo.cloud`) carries the neutral label "cloud"; every model stays listed and choosing one asks
nothing. (The Models tab chooser lists each model as its id and provider, with the quant chip.)

The effective context is **not** a provider setting: it is resolved per model (see `#models-tab` and
`#generation-tab`).

## models-tab {#models-tab}

*Built (finalize 7.2)* (mockups/README.md, decision 6). The Models tab is a **list of models across
providers** (with ↻ Refresh) and a form for the picked one. **Each model keeps its own settings, all optional — off
means BookLoom picks the value per task.** The tab opens with **BookLoom's verdict** on the model: "BookLoom's verdict: unknown model / unknown fine-tune /
recommended / usable / experimental / not recommended", then " · " and its warnings when it has any (the licence note has
its own line). Under it, **Found out from the program** says how much the model can read at once and who sets the
window, by engine: an Ollama model reached natively adds how much is loaded now ("N loaded now", read from `/api/ps`);
Ollama through `/v1` says "Window: set in Ollama, not by BookLoom"; LM Studio "loaded in LM Studio: 8,192"; llama-server
"set at llama-server start: 16,384 per slot"; any other server shows the readable figure only, and BookLoom never calls
`/api/ps` for it. **Structured answers** are one row per call kind (Translate, Name reading), each "schema", "plain
JSON" or "not checked yet" (for example gpt-oss:20b: Translate schema, Name reading plain JSON); with nothing checked
the one line reads "structured answers: not checked yet — Test inference on the Providers tab checks it". While Use
context window is off, the field shows the loaded figure as a suggestion; where the engine sets the window (LM Studio,
llama-server, Ollama `/v1`) the field is read-only and says who sets it. For an Ollama model the answer-length field
choice is not offered, only a hint (an Ollama reached through an OpenAI-compatible provider also always receives
`max_tokens`, whatever the stored choice); after a stored "ignored" verdict from the Advanced features check, a note
under Limit answer length reads "The server ignored this limit on the last Advanced features check.", and the Advanced
fold is hidden for Ollama `/v1`. The chooser, and the Providers tab's model list, each carry a **Filter models by name**
field: a case-insensitive substring over the model id; with no hit the list reads `No model name contains "{text}".`;
Escape clears it, Down moves focus into the list, and a model already chosen stays chosen while the filter hides it.
Save is
an update that keeps a refusal recorded meanwhile; **Try again now** on a refused setting acts at once and leaves unsaved typing alone; changing
model or tab with unsaved changes asks "Save the settings of gemma4:e4b · Ollama?".

| Setting                       | Type    | Default          | Validation                                                                                            | Requirement                           |
|-------------------------------|---------|------------------|-------------------------------------------------------------------------------------------------------|---------------------------------------|
| Model (per provider)          | string  | first discovered | non-empty; from live discovery or manual model-ID entry                                               | FR-MODEL-01, FR-MODEL-02, FR-MODEL-03 |
| Manual model-ID entry         | string  | —                | always available; the only source when the provider has no discovery (`supportsModelDiscovery`=false) | FR-MODEL-02, DD-38                    |
| Temperature                   | choice + decimal | Let BookLoom choose (per-task defaults of `ChatRequests`) | Let BookLoom choose · My value (0.0 – 2.0, one value for every request) · Don't send (omitted from the request, for models that refuse it); mockups/README.md S6 | FR-SETTINGS-03, DD-36                 |
| Thinking                      | choice  | Recommended (off) | says what thinking control the model offers (from its profile) and that BookLoom sends thinking off for draft, repair, proofread, names and brief calls; Recommended (off) · Allow on proofreading and names · Always, stored as `ModelSettings.thinkingOverride` through `update`; a model with no control, or one that always thinks, says so and offers no choice | design D20 |
| Sampling                      | choice + numbers | Profile default | names what BookLoom sends: its temperature per call kind and no sampler of its own beyond it, with neutral values for a server whose defaults are not neutral (LM Studio, llama-server, Unsloth); Profile default · Custom (temperature 0 – 2, `top_k` a whole number above 0, `top_p` above 0 and at most 1, `min_p` 0 – 1; an empty field keeps the profile's value, and a typed temperature is the Temperature row's My value, one stored value), stored as `ModelSettings.samplingOverride` through `update`; no choice leaves sampling to the server | design D20 |
| Use context window            | integer (tokens) | off; filled in from what is loaded (`ModelCapabilities.contextLength`), shown as "detected" | 512 – 1,048,576; the one size every call to the model uses (`ModelWindow`); sent as `num_ctx` on Ollama's native API only | FR-SETTINGS-03, FR-INFER-02 |
| Limit answer length           | integer (tokens) | off (computed per request by `OutputLimit`) | positive when on                                                         | FR-SETTINGS-03                        |
| Answer-length field (advanced) | enum   | `max_tokens`     | `max_tokens` / `max_completion_tokens`; disabled for Ollama                                           | FR-SETTINGS-03                        |
| Measured seconds (read-only) | `ModelSettings.secondsPerCall` (`MeasuredSpeed`: seconds and `TEST` or `RUN`) | none | stored on the model, never typed: the inference test writes the seconds per segment of a draft (`TEST`); the end of every names pass and run writes the mean wall time of its successful calls per call kind (`RUN`; `DRAFT` per drafted segment, other kinds per call). The names-pass estimate and the Book Brief's time line read them: "measured on this model" only when a run measured every kind the plan makes, else a range marked "estimate" with its source, and no time when nothing was measured | FR-BRIEF-08 |
| Per-provider remembered model | derived | last used        | —                                                                                                     | FR-MODEL-04                           |

**Recommendation under the model field.** Where a model is chosen (the model field and the model-pick dialog) one line
reads "This computer has {GB} GB: {model} is recommended. Pull it with `ollama pull {model}`." The tier follows the
computer's memory, and the line appears for Ollama providers only (the Ollama dialect, or an engine detected as Ollama
behind /v1), never under LM Studio or another server, where `ollama pull` is wrong: under 24 GB gemma4:e4b, 24 GB or more gemma4:26b-a4b, and from 36 GB a second line "Optional, best
quality, slower, not measured here: gemma4:31b (`ollama pull gemma4:31b`)." The command is text; BookLoom never runs it.

**Model advice lines (built, redesign 9.7).** Under the verdict line, wrapped plain lines, each shown only while it has
something to say; none is a dialog and none changes a setting. In order: *identity* ("Understood: {model} · {quant} ·
exact match / recognised as {family}, not this exact model / recognised as a fine-tune of {base} (behaviour of {base}) /
not recognised", with "(server reports {quant})" when the name and the server disagree); the *quant* sentence for the
tier, naming the project's target language (none for excellent); the *languages* line ("Declared languages: {n}
(includes uk, ru)", "English only", "not stated by the maker", or "Declared for its base model {base}: …"; where the maker
lists languages it then says what is known about the target: "· no target language chosen yet" while no project names
one, else "· {target} is declared", or "· {target} is not declared" only for a complete list, and nothing more where the
list cannot tell; or, where a run exists, "· BookLoom tested it for {source} → {target}" instead); the *licence* note for a noncommercial-only model; and the
*thinking* hint for an Ollama download that cannot turn thinking off. The foot of the tab reads "Model facts as of
{date}", the `data-version` of the bundled knowledge. At Start the same advice becomes at most one language line, a quant
line only for a poor, unusable or removed quant (or an acceptable one on a model of 5B or less, unless BookLoom measured
it), a licence line and a thinking line; Start stays enabled.

There is **no separate judge or helper model**: every call (prepare, translate, repair, proofread) uses the project's one chosen model.
These provider + model choices are the **defaults for new projects**; each project keeps its own binding thereafter
(`04_LLM_PROVIDERS_AND_MODELS.md#per-project-binding`, DD-31).

## generation-tab {#generation-tab}

*Built.* The tab holds no setting: it shows what Fast, Balanced and Max do, "The same for every model.", and the fixed
time limits. The tab carries only what the code has: no judge model and no τ override. Left: the quality grid; right:
Time limits. Thinking and sampling are set per model on the Models tab (redesign tasks 6.7 and 6.9).

| Setting                  | Type          | Default                | Validation / meaning                                                                 | Requirement  |
|--------------------------|---------------|------------------------|---------------------------------------------------------------------------------------|--------------|
| What the quality choice does | read-only grid of labels | —       | the Fast / Balanced / Max rows, read through `DialProfiles` from the pipeline's own `DialParameters` (see below) | FR-ALGO-C12  |
| Time limits              | read-only     | `CallLimits`           | fixed: a check 90 s; names, brief, file name and summary 120 s (longer for a model that always thinks); other calls 0.5 s per expected token, at least the provider's request timeout, at most 600 s; silence while streaming 60 s; up to 3 tries (a slow answer is asked again once, shorter); the HTTP client reads the same constants | FR-SETTINGS-03 |

**Context window (built).** Every call to a model, before, during or after a run, is sized against one window,
`ModelWindow.of` (`:api`): the model's **Use context window** when set, else the smallest of the loaded or detected
window, the profile's hard cap and **16,384** (`ModelWindow.DEFAULT_WINDOW`). The loaded window counts only where the
engine fixes it at load or server start (LM Studio, llama-server); the facts are read at Start. Only Ollama's native
API is sent the window, as `num_ctx` with `"truncate":false`; an OpenAI-compatible server receives no context-size
field (FR-MODEL-07, DD-44). A saved window applies from the next chunk.

**Temperature** is a constant per call kind today (`ChatRequests`), low for fidelity: draft ~0.2, QA ~0.0–0.2, repair
slightly higher (`05_TRANSLATION_ALGORITHM.md#generation-parameters`, `12_PROMPT_CATALOG.md`). The Models tab's **Use
temperature** choice (S6) keeps those defaults (Let BookLoom choose), replaces them with one value (My value), or leaves the parameter out entirely (Don't send) for models that refuse it — some newer OpenAI and Anthropic models do; a model that refuses any optional setting is handled by the refused-settings rule (mockups/README.md, "Refused settings (S6)"), which drops it, repeats the request once and remembers it per provider + model; the retry dialog also offers a one-off "lower
temperature for this retry" (DD-36).

**What the quality dial sets (built, `DialParameters`).** The dial (Fast/Balanced/Max) owns only mechanics, and is not
user-editable per knob:

| Dial     | Fixing call (`RepairMode`) | Proofread stage (`proofreadCoverage`)      | Extra model calls                                        |
|----------|----------------------------|--------------------------------------------|----------------------------------------------------------|
| Fast     | marks-only re-draft        | code only: marks for review, no model calls | none                                                     |
| Balanced | one repair                 | the marked and doubtful windows            | about one call per marked passage                        |
| Max      | one repair                 | the marked windows, then the whole book    | as Balanced, plus one call per batch of paragraphs   |

The Generation tab's table shows these three rows (Fixing calls, Proofread, Extra model calls). There is no
reviewer pass and no backward revision (removed in redesign group 14).

Chapter summaries are not a dial setting: the Book Brief's switch decides them, the same at every dial.

The dial no longer sets a preceding count: the window's 2 or 3 previous pairs follow the model's tier.

The dial sets no segment cap on a chunk, and Manual review mode does not either: a chunk closes on the current-part
budget of the model profile's tier, **600** source tokens on a small model and **1,000** on a large one
(`05_TRANSLATION_ALGORITHM.md#token-budget`), and the drafting batch size is the model profile's (Settings › Models).

**τ ownership (DD-45).** The **trust threshold τ is owned by the review-mode dial** (Unattended 0.60 / Assisted 0.75 /
Manual 0.85), not the quality dial. **No manual τ override is offered**, now or in the planned Generation
tab.

**One repair, no rounds.** A segment whose draft keeps a BLOCK or REPAIR finding gets one repair call (FR-ALGO-C12),
set by the quality dial alone (the table above); Fast has no fixing call. A review retry runs with no repair
(`LoopSettings.repairs` false).

**Token-budget constants (fixed).** Token counts use a deterministic characters ÷ K heuristic (no shipped tokenizer):
the per-script **K table** and a **safety margin of `0.15`** are documented constants
(`05_TRANSLATION_ALGORITHM.md#chunking`), not user settings. These are recorded here for traceability; they are not editable settings.

## appearance-tab {#appearance-tab}

| Setting                 | Type    | Default                  | Validation                                                                              | Requirement           | Status |
|-------------------------|---------|--------------------------|-----------------------------------------------------------------------------------------|-----------------------|--------|
| Theme                   | enum    | system                   | light / dark / "Match the computer" (JavaFX 26 reads OS `prefers-color-scheme`); stored as `ui.theme` the moment it is chosen, from the tab or the title-bar toggle | FR-SETTINGS-04, DD-22 | built |
| Language of the app (UI) | enum   | OS locale on first start | Match the computer (naming its language) / English / Ukrainian; persisted as `ui.language`, and choosing "Match the computer" removes it; **applies after BookLoom restarts** ("Applies after BookLoom restarts."). **Restart now** shows only while the choice differs from the language on screen, asks the quit question first when work is under way, and says so when no new copy could start. The new copy starts only after this one has closed its files: command built, window exits, shutdown closes the database, the lock is released, then the child starts | FR-UI-06, DD-34 | built |
| Show technical details  | boolean | off                      | on / off; every Simple / Detailed view (live panel, progress dialogs, review desk) opens in Detailed, the Settings folds open, and the technical lines show (rule codes in the review desk, debug lines in the log, the technical line on Import) (`mockups/README.md`, G12); stored as `ui.technicalDetails` | FR-SETTINGS-04 | built |

**Theme control is tri-state here** (light / dark / system). The **toolbar theme control is a 2-state light↔dark
quick-toggle only**; the tri-state selector including `system` lives in this tab
(`08_UI_SCREENS_AND_STATES.md#shell-and-navigation`).

**The language switch needs a restart** because a live switch would rebuild every view. A stored `ui.language` is read
first; with none stored the interface language follows the operating system's locale (Ukrainian OS → Ukrainian,
otherwise English). The tab says "✓ Changes apply at once." for the theme and the switch, and the language line says
when it takes effect.

**There is no Accent row and no Reduce motion row.** The accent is fixed (Cognac only, `09_THEMING.md` FR-THEME-4) and
is not shown as a setting; reduced motion follows the operating system (or `BOOKLOOM_REDUCE_MOTION`). **Density is
deferred**: no spacing/density tokens exist in the token catalogue (`09_THEMING.md#token-catalog`).

## automation-tab {#automation-tab}

*Built (finalize 7.4)* (these rows apply at once, and the review mode reaches the next run that starts). This is the authoritative Automation list. There is
**no "desktop notification on finish" toggle and no "single instance only" toggle**: there is no OS-notification
feature (`11_NOTIFICATIONS_AND_ERRORS.md`), and single-instance is **always on** and not user-configurable (the
single-instance lock in the lifecycle spec).

| Setting                        | Type    | Default    | Validation                                          | Requirement  |
|--------------------------------|---------|------------|------------------------------------------------------|--------------|
| When to stop and ask you (default review mode) | enum | Never — translate unattended | Never — translate unattended / When something needs review / After every segment (`ReviewMode` UNATTENDED / ASSISTED / MANUAL); stored as `run.reviewMode` and read when a run starts. `BOOKLOOM_REVIEW_MODE` (or the system property `bookloom.review.mode`, launch values `unattended` / `assisted` / `manual`), when set, decides instead and the tab names it. The long phrases are this tab's; every other screen says Unattended / Assisted / Manual | FR-REVIEW-01 |
| Default quality                | enum    | Balanced   | Fast / Balanced / Max; a book takes it when it is first stored as a project, then keeps its own in the Book Brief | FR-BRIEF-08  |
| Open the review desk when a translation finishes with segments to review | boolean | on | on / off          | FR-REVIEW-01 |
| Keep the computer awake        | boolean | on         | on / off, applied at once: off releases the hold of a run already going; the lid note: closing a laptop lid still puts it to sleep, and translating goes on by itself after sleep | FR-RESUME-03 |
| Reopen the last project (resume on launch) | boolean | on | on / off                                 | FR-RESUME-03 |

## storage-and-logs-tab {#storage-and-logs-tab}

*Built (finalize 7.4)* (paths are shown short with the full path as a tooltip).

| Setting              | Type                     | Default             | Validation                  | Requirement    |
|----------------------|--------------------------|---------------------|------------------------------|----------------|
| Your projects: saved in, size | path and size (read-only display) | per-OS app data dir | the size is the database with its write-ahead file, with the project count; "saved as it goes"; "In memory only" when nothing is kept | FR-PERSIST-03 |
| Open data folder / Make a backup… | action       | —                   | Open data folder opens the folder of `bookloom.db` (About opens only the log folder); the backup is a `VACUUM INTO` copy to a new file the person picks, holds no key, and never replaces an existing file (`validation`) | FR-PERSIST-03  |
| Delete all data…     | action                   | —                   | asks first (Cancel default, Make a backup first); stops a running or paused translation, deletes only `bookloom.db` and its `-wal`/`-shm` files, then restarts on a fresh database with the first-launch defaults; book files, exports and logs stay; a failure keeps the data and names the data folder | FR-PERSIST-03 |
| Logs: folder         | path (read-only display) | per-OS log dir      | must exist and be writable; the last 10 launches are kept | DD-23 |
| Detailed diagnostic log | boolean               | off in an installed app, on in a development run | on / off, applies after BookLoom restarts (the marker file `detailed-log.on`); `BOOKLOOM_TRACE_FILE` / `bookloom.trace.file`, when set, decides and the tab names it; a development run writes it whatever the switch says and the tab says so | FR-SETTINGS-06 |
| Open log folder / Save a diagnostic bundle… | action | —                | —                            | FR-SETTINGS-06 |

The log level is a launch variable, not a setting: it has no row on this tab. `BOOKLOOM_LOG_LEVEL`, or else the system property `bookloom.log.level`, sets it for one run
(`.claude/rules/logging.md`; default INFO for an installed app, TRACE for a development run). `TRACE` is the only level
that writes book text, prompts and model replies to the log
(`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#secrets-never-stored`).

## validation-behaviour {#validation-behaviour}

| ID             | Requirement                                                                                                             |
|----------------|-----------------------------------------------------------------------------------------------------------------------|
| FR-SETTINGS-V1 | Any setting entered outside its range is rejected with an inline validation message; the prior valid value is retained. |
| FR-SETTINGS-V2 | Settings persist to the typed KV store and reload on next launch (built, ADR-0043; secrets are stored as references only). |
| FR-SETTINGS-V3 | A UI-language change takes effect only after an application restart (see `10_I18N_AND_ACCESSIBILITY.md`).               |
