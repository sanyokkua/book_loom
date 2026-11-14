## ADDED Requirements

### Requirement: Show the model facts as one block under the provider's model picker

The provider form SHALL show everything BookLoom says about the model its picker holds — the identity line, the quant sentence, the declared languages, the licence note, the thinking hint, the Advanced features verdicts, what the program reports and the date of the model facts — as one block of lines in one text style under the picker, in the form's field column. WHILE the picker holds no model, or the provider is not saved yet, the block SHALL show nothing.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#defects` (S-09, S-16). In plain words: the facts describe the model being chosen, so they belong where it is chosen. They used to stand on another tab as loose lines in three text styles with uneven gaps.

#### Scenario: The facts follow the picker

- **WHEN** the saved provider `Ollama` is open, its picker holds `gemma4:e4b`, and `gemma4:26b` is then picked
- **THEN** the block under the picker describes `gemma4:26b`, starting with its `Understood:` line

#### Scenario: One style

- **WHEN** the block shows the identity line, the quant sentence, the declared languages and the lines found out from the program
- **THEN** every line is in the hint text style, the lines are 4 pixels apart, and each wraps before the form's right edge

#### Scenario: Nothing to describe

- **WHEN** a new provider is being added and nothing is typed in its Model row
- **THEN** no line is shown under the picker and no request about a model is sent

### Requirement: Keep one content width on every Settings tab

The Settings tabs SHALL share one right edge: a tab with one card SHALL be 860 pixels wide at most, a tab with two cards side by side SHALL keep both inside the same outer width as the Providers tab, and the header's subtitle SHALL wrap only at that width.

**Source**: `research/ui-audit-settings.md#defects` (S-05, S-23). In plain words: the cards were 640, 760, 860 pixels or as wide as the window, so the content jumped at every change of tab, and the Ukrainian subtitle broke into two lines with 700 pixels free beside it.

#### Scenario: The right edge does not move

- **WHEN** Appearance, Automation and Models are opened one after another in a window 1440 pixels wide
- **THEN** the right edge of each tab's card is at the same x

#### Scenario: The Ukrainian subtitle is one line

- **WHEN** Settings is shown in Ukrainian in a window 1440 pixels wide
- **THEN** the subtitle is one line and the tab row stands at the same height as in English

### Requirement: Colour the provider summary card by what is known

The provider summary card SHALL be neutral (the surface colour with the divider edge) while its provider has no model, or has not passed a test in this session, SHALL be in the success tone only when the last test passed, and SHALL be in the danger tone when the last test failed.

**Source**: `research/ui-audit-settings.md#defects` (S-10). In plain words: green means "works" on every chip and banner. A card that is green for "no model chosen yet" says something BookLoom does not know.

#### Scenario: An untested provider is neutral

- **WHEN** `LM Studio` is selected, has no model chosen and has not been tested
- **THEN** its summary card has the surface background, not the success background

#### Scenario: A passed test turns it green

- **WHEN** Test is pressed for `Ollama` with `gemma4:26b` and every stage passes
- **THEN** the card takes the success tone and reads `ready ✓`

### Requirement: Keep the Generation tables and the test figures readable

The Generation quality table SHALL give its three answer columns at least as much width each as the row-name column and a divider line between rows; the Time limits rows SHALL start every value at one x on its key's baseline; and the provider test dialog's figures SHALL never break inside a word or between a number and its unit.

**Source**: `research/ui-audit-settings.md#defects` (S-02, S-03, S-07). In plain words: the answers are the content of these tables. They were squeezed into stacks of five to nine lines beside a half-empty name column, and "25.4 tok/s" broke after the number.

#### Scenario: The quality table at 1440 pixels

- **WHEN** Generation is shown in English in a window 1440 pixels wide
- **THEN** each answer column is at least as wide as the row-name column and no answer cell has more than three lines

#### Scenario: Time limits line up

- **WHEN** Generation is shown in Ukrainian
- **THEN** the four values of Time limits start at the same x, each on the baseline of its key

#### Scenario: A figure stays whole

- **WHEN** the Inference test's dialog shows `119 ms · uncached · 0 %` and `25.4 tok/s`
- **THEN** `uncached` is on one line and `25.4 tok/s` is on one line

### Requirement: Let new books take their quality from the AI model

The Automation tab's "Quality new books start at" SHALL offer "By the AI model (Fast on a small model, Balanced otherwise)" before Fast, Balanced and Max, SHALL have it chosen until the person chooses another, and a book imported while it is chosen SHALL get a brief whose quality is marked "by the model".

**Source**: `research/review-digest.md#f16--defaults-work-against-the-goal`, `research/review-digest.md#f2--almost-nothing-improves-a-poor-first-draft`, `design.md#d21--defaults-instead-of-questions`. In plain words: Balanced for every model made the first run on a small model a quarter longer for about 1 % of changed text. The default now follows the model, and a person who always wants one quality still sets it here once.

#### Scenario: A fresh installation follows the model

- **WHEN** Settings › Automation is opened on a fresh database
- **THEN** "By the AI model (Fast on a small model, Balanced otherwise)" is chosen

#### Scenario: A fixed choice is used as before

- **WHEN** the person chooses Max and imports a book
- **THEN** the new brief's quality is Max and is not marked "by the model"

## MODIFIED Requirements

### Requirement: Edit the settings of the current provider's model on the Models tab

The Models tab SHALL name the current provider's chosen model in a read-only title, `{model} · {provider}`, SHALL edit and keep that model's own optional settings under the line "Each model keeps its own settings. All are optional — off means BookLoom picks the value per task.", and SHALL offer no control that chooses, lists or filters models. WHILE no model is chosen, it SHALL show only "No AI model is chosen yet." and the link "Choose one in Providers".

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/review-digest.md#owner-decisions`. In plain words: a model is chosen in one place, the provider form. The temperature and the other settings on this tab always belong to the model chosen there, so the tab needs no second chooser, and one model may still refuse a setting that another accepts.

#### Scenario: Two models keep different settings

- **WHEN** `gemma4:e4b` of `Ollama` is the chosen model and is given the context window 16384, and `gpt-oss:20b` is then chosen and saved on the Providers tab
- **THEN** the Models tab is titled `gpt-oss:20b · Ollama` and shows no context window value, and after `gemma4:e4b` is chosen again it shows 16384

#### Scenario: No chooser

- **WHEN** the Models tab is opened with `gemma4:26b` of `Ollama` chosen and three providers configured
- **THEN** the tab shows the title `gemma4:26b · Ollama` and the sections from Temperature down, and no model list, no "Filter models by name" field, no Refresh button and no model of another provider

#### Scenario: No model chosen

- **WHEN** the Models tab is opened while the current provider has no model
- **THEN** it reads "No AI model is chosen yet." with the link "Choose one in Providers", shows no Temperature section and no Save footer, and the link opens the Providers tab

#### Scenario: The tab follows the provider form

- **WHEN** "Use this one" makes `LM Studio` with `google/gemma-4-e4b` the current provider and the Models tab is then opened
- **THEN** it is titled `google/gemma-4-e4b · LM Studio`

### Requirement: Show who a model is beside the provider's model picker

The provider form SHALL show under its model picker, for the model the picker holds, one identity line, `Understood: {model} · {quant} · {match}`, where the match part reads "exact match", "recognised as {family}, not this exact model" or "recognised as a fine-tune of {base} (behaviour of {base})", and the quant part reads `{label} (server reports {base})` when the name's quant label differs from the one the server reports.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: before changing anything, the person sees how BookLoom read the model's name and whether it knows this exact model, a close relative, or only its architecture. That decides how much to trust the advice shown below the line. It now stands in the provider form, the one place a model is chosen.

#### Scenario: An exact match

- **WHEN** the provider form's model picker holds `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` and the server reports the quant `Q4_K_M`
- **THEN** the identity line reads `Understood: GLM 4.7 Flash · UD-Q4_K_XL (server reports Q4_K_M) · exact match`

#### Scenario: A fine-tune is named with its base

- **WHEN** the chosen model is `hf.co/bartowski/NousResearch_Hermes-4-70B-GGUF:Q4_K_M`, a fine-tune with no row of its own
- **THEN** the identity line reads `Understood: Hermes 4 70B · Q4_K_M · recognised as a fine-tune of Llama 3.1 (behaviour of Llama 3.1)`

#### Scenario: Only the architecture is known

- **WHEN** the chosen model is an unknown build whose server reports the architecture `gemma3`
- **THEN** the match part reads `recognised as Gemma 3, not this exact model`

#### Scenario: Both quant labels agree

- **WHEN** the name's quant label and the server's label are both `Q8_0`
- **THEN** the quant part reads `Q8_0` with no "server reports" text

### Requirement: Say how good a model's quant is beside the provider's model picker

The provider form SHALL show under the identity line of its model picker the sentence for the model's quant tier, none for excellent, naming the open project's target language where the sentence has one, and SHALL NOT block or change any setting because of it. The tier itself is decided by the llm-provider capability.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a heavily compressed download translates worse, especially for names and word endings. The person is told plainly and still decides for themselves. It now stands in the provider form, the one place a model is chosen.

#### Scenario: A good quant

- **WHEN** the tier of `UD-Q4_K_XL` is good
- **THEN** the line under the identity line reads "Very close to full quality. Fine for translating a whole book."

#### Scenario: An acceptable quant names the target language

- **WHEN** the tier of `Q4_K_M` on `phi4-mini` is acceptable and the open project translates into Ukrainian
- **THEN** the line reads "Slightly reduced precision. Usually fine, but names and word endings may drift, especially for Ukrainian; a larger version translates more reliably."

#### Scenario: A three-bit file on a large model

- **WHEN** the tier of `Q3_K_M` on a 32B dense model is acceptable with a caution
- **THEN** the line reads "Usable at this size, but 3-bit files lose more than 4-bit ones; check a few paragraphs first."

#### Scenario: A poor quant

- **WHEN** the tier of `Q4_0` on `phi4-mini` is poor
- **THEN** the line reads "Noticeably reduced quality, especially for Ukrainian. Expect more mistakes and more segments to review. Use it only if nothing larger fits."

#### Scenario: An unusable quant still lets the model be chosen

- **WHEN** the tier of `Qwen_Qwen3.5-9B-Q2_K_L.gguf` is unusable
- **THEN** the line reads "This version is too compressed for translation. Choose a larger version or another model." and the model can still be chosen and saved

#### Scenario: A removed format, an unmeasured format and an excellent quant

- **WHEN** the tier is not supported (`Q4_0_4_4`), not measured (`Q1_0`) or excellent (`Q8_0`)
- **THEN** the lines read "This old format is no longer supported. Download the Q4_0 or Q4_K_M file from the same repository.", "BookLoom has no quality data for this format. Treat it like a 4-bit version." and nothing, respectively

### Requirement: Say which languages a model declares beside the provider's model picker

The provider form SHALL show under its model picker, for the model the picker holds, a neutral line saying whether its maker lists languages — "Declared languages: {count} (includes {codes})", "Declared languages: English only", "Declared languages: not stated by the maker" or "Declared for its base model {base}: {codes}" — and WHERE the open project's language pair is one BookLoom tested the model on, SHALL add "· BookLoom tested it for {source} → {target}".

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: the maker's list is what the maker says, not what was measured, so the line says "declared" and adds BookLoom's own test only where there is one, for the pair it covered. The state and the tested pairs are decided by the llm-provider capability ("Say whether a model's maker declares a language", "Count a language pair as tested only by BookLoom's own runs"). It now stands in the provider form, the one place a model is chosen.

#### Scenario: A model with a list

- **WHEN** the chosen model lists 24 languages including Ukrainian and Russian
- **THEN** the line reads "Declared languages: 24 (includes uk, ru)"

#### Scenario: English only and not stated

- **WHEN** the chosen model is `phi4:14b`, which lists English only, and then `gemma4:12b`, whose maker gives no list
- **THEN** the lines read "Declared languages: English only" and "Declared languages: not stated by the maker"

#### Scenario: A fine-tune takes its base model's list

- **WHEN** the chosen model is a Hermes 4 14B download whose own metadata says nothing and its base model Qwen3 lists Ukrainian and Russian
- **THEN** the line reads "Declared for its base model Qwen3: uk, ru"

#### Scenario: BookLoom's own test is added

- **WHEN** `gemma4:e4b` is chosen and the open project translates English into Ukrainian
- **THEN** the line reads "Declared languages: not stated by the maker · BookLoom tested it for English → Ukrainian"
- **AND** for a Russian-to-Ukrainian project the line reads "Declared languages: not stated by the maker" with no tested part

### Requirement: Date the model facts under the provider's model facts

The provider form SHALL show, as the last line under its model picker, "Model facts as of {date}", the date of the model knowledge shipped with the application.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: the advice on this tab comes from tables shipped with the program. A person must be able to tell how old they are. It now stands in the provider form, the one place a model is chosen.

#### Scenario: The date is shown

- **WHEN** the provider form is shown and the shipped model knowledge is dated 2026-10-28
- **THEN** the last line under the model picker reads "Model facts as of 2026-10-28"

### Requirement: Show the thinking hint for a download that cannot turn thinking off

WHERE an Ollama download belongs to a model family whose library model has a thinking switch but the download has none (its `/api/show` shows no renderer and no thinking capability), the provider form, under its model picker, and Start SHALL show the hint "This download cannot turn thinking off in {engine}, so it is slower. The library model {libraryTag} or a Modelfile with RENDERER and PARSER fixes it." and SHALL NOT block.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: some downloads think at length and cannot be told to stop, only because Ollama serves them without the library model's switch. The person is told why the run is slow and how to get a model that behaves. A model that always thinks by design, such as gpt-oss or a reasoning build, gets no such hint, because nothing fixes it; its Thinking row says so instead. It now stands in the provider form, the one place a model is chosen.

#### Scenario: The hint on the Models tab

- **WHEN** the provider form's model picker holds an Ollama download of Qwen3.5 9B that cannot turn thinking off
- **THEN** it shows "This download cannot turn thinking off in Ollama, so it is slower. The library model qwen3.5:9b or a Modelfile with RENDERER and PARSER fixes it."

#### Scenario: The same hint at Start

- **WHEN** Start is about to run on the same download
- **THEN** the same hint is shown as a plain line under the estimate and Start stays available

#### Scenario: A model that can switch thinking off shows no hint

- **WHEN** the library model `qwen3.5:9b` is chosen
- **THEN** no such hint is shown

#### Scenario: A model that always thinks by design shows no hint

- **WHEN** `gpt-oss:20b` on Ollama, or `mistralai/ministral-3-14b-reasoning` on LM Studio, is chosen
- **THEN** no such hint is shown

### Requirement: Show the licence note of a model with a restricted licence

WHERE a model's licence allows only noncommercial use, the provider form, under its model picker, and Start SHALL show the line "{model} is licensed for noncommercial use only (PolyForm Strict 1.0.0)." and SHALL NOT block.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a person who translates a book to publish it should hear about a licence limit before the run, not after. It now stands in the provider form, the one place a model is chosen.

#### Scenario: The note on the Models tab

- **WHEN** `hf.co/bartowski/thomsonreuters_Thomson-1.0-Small-GGUF` is chosen
- **THEN** the lines under the model picker show "Thomson 1.0 Small is licensed for noncommercial use only (PolyForm Strict 1.0.0)."

#### Scenario: The same note at Start

- **WHEN** Start is about to run on that model
- **THEN** the same line is shown under the estimate and Start stays available

#### Scenario: An open licence shows nothing

- **WHEN** `gemma4:e4b` is chosen
- **THEN** no licence line is shown

### Requirement: Report what the program says about a model, engine by engine

The provider form SHALL show under its model picker, as found out from the program, how much the model can read at once, who sets that window on this engine, how much is loaded now where the program says so, and for each kind of call whether structured answers come as a schema, as plain JSON or not at all, each from a test or from a refusal. It SHALL NOT store what it read from a server.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: before changing a number, a person sees what the model itself reports about its limits and who controls them. The structured-answer row follows what BookLoom learned for each kind of call, since a server may take a schema for one and refuse it for another. It now stands in the provider form, the one place a model is chosen.

#### Scenario: Reported limits are shown

- **WHEN** `gemma4:e4b` reports a context length of 131072 and 16384 loaded
- **THEN** the lines under the model picker show both figures under "Found out from the program"

#### Scenario: Ollama through the OpenAI-compatible route says who sets the window

- **WHEN** the model belongs to an Ollama provider reached through `/v1`
- **THEN** the Context row reads "Window: set in Ollama, not by BookLoom"

#### Scenario: LM Studio shows the loaded figure

- **WHEN** `google/gemma-4-e4b` is loaded in LM Studio with a window of 8192
- **THEN** the Context row reads "loaded in LM Studio: 8,192"

#### Scenario: llama-server shows its start setting

- **WHEN** the model is served by llama-server started with 16384 tokens per slot
- **THEN** the Context row reads "set at llama-server start: 16,384 per slot"

#### Scenario: A generic server gets no loaded figure

- **WHEN** the model belongs to a generic OpenAI-compatible server
- **THEN** the lines under the model picker show only how much it can read at once and no loaded figure, and no `/api/ps` request is sent

#### Scenario: Structured answers are shown per kind of call

- **WHEN** a test found that `gpt-oss:20b` honours a schema, and Name reading was refused a schema and now uses plain JSON
- **THEN** the lines under the model picker show Translate as a schema and Name reading as plain JSON

#### Scenario: Nothing is known yet

- **WHEN** no test of `gemma4:e4b` has run and nothing was refused since the application started
- **THEN** the lines under the model picker read "structured answers: not checked yet" and name the Inference test that checks it

### Requirement: Run the optional Advanced features check for a model

The Providers test SHALL offer the optional stage "Advanced features (optional, uses the model)" for the selected model, never started by the other stages, and SHALL show one verdict line per check it ran, also under the provider form's model picker while it holds that model.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a server of unknown make may or may not honour structured answers, a thinking switch, extra samplers or an answer cap. A few short, invented-text calls tell, and the answer is remembered. They are optional because they use the model. Which checks are sent and how the verdicts are stored is the llm-provider capability's "Check what a server honours only when the person asks".

#### Scenario: Verdicts are listed

- **WHEN** the Advanced features stage runs for `qwen3.5-9b` on a generic server
- **THEN** the lines read "Structured answers: honoured", "Turning reasoning off: works with `reasoning_effort`", "top_k/min_p: ignored" and "Answer cap: `max_tokens`"

#### Scenario: The stage is optional

- **WHEN** Test connection, Test models and Test inference are used without the Advanced features stage
- **THEN** no Advanced features call is sent

#### Scenario: Verdicts survive a restart

- **WHEN** the stage ran for `qwen3.5-9b` and the application is restarted
- **THEN** the lines under the model picker still show the verdicts while it holds `qwen3.5-9b`

#### Scenario: A run holds the model

- **WHEN** a translation is running and the stage is started
- **THEN** the stage is refused before any call is sent, the same way as Test inference, and runs once the run is paused

### Requirement: Show a quant line at Start for a model that needs one

WHEN a translation is about to start, the application SHALL show under the estimate one line with the sentence of the model's quant tier when the tier is poor, unusable or not supported, or acceptable on a small model of at most 5B, and SHALL NOT show it where BookLoom measured that model at that quant; the line SHALL NOT block Start.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a small model at four bits drifts on names and endings, so the person is told once before spending hours. Where BookLoom has itself measured the model at that quant, the warning would only be noise.

#### Scenario: A small model at four bits

- **WHEN** a Russian to Ukrainian project is about to start on `llama3.2:3b` (`Q4_K_M`)
- **THEN** a line under the estimate reads "Slightly reduced precision. Usually fine, but names and word endings may drift, especially for Ukrainian; a larger version translates more reliably."

#### Scenario: A poor quant

- **WHEN** Start is about to run on `phi4-mini` at `Q4_0`
- **THEN** the line carries the poor-quality sentence

#### Scenario: A medium model at four bits shows no line

- **WHEN** Start is about to run on a 12B model at `Q4_K_M`
- **THEN** no quant line is shown

#### Scenario: A measured model shows no line

- **WHEN** Start is about to run on `gemma4:e4b-mlx`, whose `nvfp4` quant BookLoom measured
- **THEN** no quant line is shown, while the chip and the sentence under the provider's model picker stay

### Requirement: Show the quant chip beside every model in every model picker

The application SHALL show beside each model in every model picker (the Providers model list and the AI-model choice before Start) a chip with the quant token of the name, or the quant the server's facts report when the name has none, and its tier, such as `Q4_K_M · acceptable`; it SHALL show no chip while no quant is known, and the chip SHALL keep its full width and never be cut short.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a person choosing among six downloads of one model sees at once which one is the compressed one. A chip that is cut to "Q4_K_…" would defeat the purpose.

#### Scenario: A good chip

- **WHEN** the Providers model list holds `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL`
- **THEN** its row carries the chip `UD-Q4_K_XL · good`

#### Scenario: An acceptable chip and an unusable chip

- **WHEN** the Providers model list holds `phi4-mini` (`Q4_K_M`, 3.8B) and `Qwen_Qwen3.5-9B-Q2_K_L.gguf`
- **THEN** the rows carry `Q4_K_M · acceptable` and `Q2_K_L · unusable`

#### Scenario: The chip stays whole in a narrow window

- **WHEN** the window is narrowed so the model name needs the whole row
- **THEN** the name is cut or wrapped and the chip `Q4_K_M · acceptable` is shown in full

#### Scenario: The same chip before Start

- **WHEN** the AI-model choice before Start lists `gemma4:e4b-mlx`, whose name holds no quant token, after its facts were read and the server reported `nvfp4`
- **THEN** its row carries the chip `nvfp4 · acceptable`

#### Scenario: No chip before the facts are read

- **WHEN** the Providers model list shows `gemma4:e4b-mlx` and Ollama's model list gives it an empty quant, and the model has not been selected, tested or detected again
- **THEN** its row carries no chip, and selecting it reads its facts and shows `nvfp4 · acceptable`

### Requirement: Narrow every model picker by the typed model name

The application SHALL show a text field with the placeholder "Filter models by name" above the list of every model picker (the Providers model list and the AI-model choice before Start), SHALL narrow the list as the person types to the models whose id contains the typed text ignoring letter case, and WHEN no id matches SHALL show `No model name contains "{text}".`

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a server with sixty downloads is hard to scan. Typing `e4b` should leave the e4b models.

#### Scenario: Typing narrows the list

- **WHEN** the Providers model list holds `gemma4:e4b`, `gemma4:26b` and `qwen3.5:9b` and `e4b` is typed
- **THEN** only `gemma4:e4b` is listed

#### Scenario: Letter case is ignored

- **WHEN** `GEMMA4` is typed over the same three models
- **THEN** `gemma4:e4b` and `gemma4:26b` are listed and `qwen3.5:9b` is not

#### Scenario: Nothing matches

- **WHEN** `llama` is typed over the same three models
- **THEN** the list is empty and the text `No model name contains "llama".` is shown

#### Scenario: Clearing the text restores the list

- **WHEN** the text `e4b` is deleted
- **THEN** all three models are listed again

#### Scenario: The filter works before Start too

- **WHEN** the AI-model choice before Start holds `mistral-small3.2:24b` and `llama3.1:8b` and `mistral` is typed
- **THEN** only `mistral-small3.2:24b` is listed

### Requirement: Let the keyboard and the selection work with the model-name filter

WHEN the person presses Esc in the model-name filter, the application SHALL clear the text and show the whole list; WHEN the person presses the Down key in it, focus SHALL move into the list; and the chosen model SHALL stay chosen while it still matches the typed text.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a person at the keyboard can filter, step into the list and pick, and a half-typed filter never silently drops the choice already made.

#### Scenario: Esc clears the text

- **WHEN** `e4b` is typed in the filter of the Providers model list and Esc is pressed
- **THEN** the field is empty and the whole list is shown

#### Scenario: Down moves into the list

- **WHEN** `gemma` is typed and the Down key is pressed
- **THEN** focus is on the first listed model

#### Scenario: The selection survives a matching filter

- **WHEN** `gemma4:e4b` is chosen and `gemma` is typed
- **THEN** `gemma4:e4b` is still chosen and shown selected

#### Scenario: A filter that hides the chosen model does not change the choice

- **WHEN** `gemma4:e4b` is chosen and `qwen` is typed
- **THEN** the list shows only Qwen models and the chosen model is still `gemma4:e4b`

### Requirement: Save Providers and Models through a pinned footer and guard leaving

The Providers and Models tabs SHALL change nothing until saved, SHALL show a pinned footer with "● Unsaved changes", Cancel and Save while they hold changes, and WHEN the person leaves with changes SHALL ask Keep editing, Discard or Save.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`. In plain words: a provider's address typed halfway must not become live, and moving to another tab must not silently lose or silently apply what the person typed.

#### Scenario: A change shows the footer

- **WHEN** the request timeout of `Ollama` is changed from 180 to 240 seconds
- **THEN** the footer shows "● Unsaved changes", Cancel and Save, and the stored timeout is still 180

#### Scenario: Leaving asks what to do

- **WHEN** the person opens the Appearance tab with unsaved changes on Providers
- **THEN** the question offers Keep editing, Discard and Save, and the Appearance tab opens only after Discard or Save

#### Scenario: Switching provider asks naming it

- **WHEN** `Ollama` has unsaved changes and the `LM Studio` row is chosen
- **THEN** the question "Save the changes to Ollama?" is asked with Keep editing, Discard and Save
- **AND** `LM Studio` opens only after Discard or Save

#### Scenario: Switching model asks naming it

- **WHEN** the Models tab holds unsaved changes to `gemma4:e4b` of `Ollama` and the Providers tab is opened
- **THEN** the question reads "Save the settings of gemma4:e4b · Ollama?", and the model cannot be changed before it is answered, because a model is chosen only on the Providers tab

#### Scenario: Leaving Models asks

- **WHEN** the Models tab holds unsaved changes and the Providers tab is opened
- **THEN** the question is asked and the Providers tab opens only after Discard or Save

#### Scenario: Save stores the change

- **WHEN** Save is chosen
- **THEN** the stored timeout is 240 and the footer disappears

### Requirement: Choose a model from the list, or name one the list does not hold

The application SHALL offer the models the provider being edited reports, SHALL accept a model identifier typed by hand, and SHALL also offer the provider's custom models. WHEN the list is opened without typing, it SHALL hold every offered model with the chosen one marked, and the picker SHALL be a row of the form's field column labelled "Model".

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/ui-audit-settings.md#cross-tab-inconsistencies`, `research/ui-audit-settings.md#defects` (S-09), `research/combo-box-behaviour.md#a-what-the-control-does-today-and-the-two-defects`. In plain words: some servers list what they hold and some do not, and a server that holds nothing is a valid state rather than a broken one. Typing the identifier has to stay possible in all three cases. The chosen model is discarded on a provider change because a model identifier means nothing on a different server. The open list once showed one model while the card said seven were available, because the list was narrowed by the chosen model's own name.

#### Scenario: A listed model can be chosen

- **WHEN** the provider reports `gemma3:12b` and `qwen3:8b`
- **THEN** both are offered, and choosing `gemma3:12b` makes it the chosen model

#### Scenario: An unlisted model can be typed

- **WHEN** the provider reports no models and `mistral-small:24b` is typed
- **THEN** `mistral-small:24b` is the chosen model

#### Scenario: An unreadable listing says so

- **WHEN** the provider's listing cannot be read
- **THEN** the application reports that no list was obtained and keeps the typed entry available

#### Scenario: Changing provider clears the model

- **WHEN** `gemma3:12b` is chosen on the Ollama provider and the LM Studio provider is then edited
- **THEN** no model is chosen and the offered list is empty until it is fetched again

#### Scenario: A model note follows the chosen model

- **WHEN** the Ollama provider is edited and `gemma4:26b-mlx` is typed
- **THEN** a note under the chooser says it is recommended
- **AND** typing `gemma4:e2b-mlx` instead shows a warning, and an unknown model shows no note

#### Scenario: LM Studio's limits are stated

- **WHEN** the LM Studio provider is edited
- **THEN** a note says it is slower, does not stream its replies and sends more control codes, even before a model is chosen

#### Scenario: A listing in flight reports itself

- **WHEN** the model list has been asked for and has not yet been answered
- **THEN** the screen reports the listing as in progress
- **AND** the typed entry stays available throughout, because it needs no list

#### Scenario: The chooser lives on the providers card

- **WHEN** the settings screen is opened on the Providers area
- **THEN** the model chooser is the Model field of the Connection details fold
- **AND** Change model on the summary card opens the fold on that field

#### Scenario: The whole list opens on the chosen model

- **WHEN** the provider reports 7 models, `gemma4:26b` is the chosen model and the list is opened by its arrow or by the Down key
- **THEN** all 7 models are listed and `gemma4:26b` is marked, not `gemma4:26b` alone

#### Scenario: The picker is a row of the form

- **WHEN** Connection details is open at a window 1440 pixels wide
- **THEN** the label "Model" stands in the label column, and the name filter, the picker and the lines about the model start at the same left edge as the Name and Address fields

### Requirement: Recommend a model by the computer's memory where models are chosen

WHERE the provider is Ollama's own connection or a server detected as Ollama, the application SHALL show, in the note under the provider form's model picker and on the model-pick card, the advice to choose the largest model the computer can run, with the statement that which model is best depends on the languages, and SHALL name one model as a suggestion — `gemma4:26b` from 24 GB of memory, `gemma4:e4b` under 24 GB — with its `ollama pull` command as text; it SHALL never run the command, SHALL suggest no model larger than `gemma4:26b`, and for any other provider it SHALL show no such note.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`, `research/review-digest.md#owner-decisions`, `research/review-digest.md#f1--the-model-sets-the-quality-ceiling`, `research/review-digest.md#model-table`, `design.md#d21--defaults-instead-of-questions`. In plain words: the model sets the quality ceiling: the same request scored about 2.2 of 5 on the small model and about 2.8 on gemma4:26b. So the advice is about size — take the largest model this computer can run. Which large model is best depends on the languages of the book: gemma4 did well for Ukrainian, and another family may do better for another language. BookLoom has measurements for Ukrainian only, so it builds no table of models by language and names gemma4:26b as a suggestion, not as the one right model. The larger 31b model is not suggested: it is too slow to be tested and was never measured. The pull command is Ollama's, so under LM Studio or another server it would tell the person to run something wrong.

#### Scenario: A 16 GB computer

- **WHEN** the provider form's model picker is shown on a computer with 16 GB of memory
- **THEN** the note advises the largest model this computer can run, says that the best one depends on the languages, names `gemma4:e4b` as a suggestion that fits with the text `ollama pull gemma4:e4b`, and says that `gemma4:26b` did well for Ukrainian and needs 24 GB

#### Scenario: A 32 GB computer

- **WHEN** the provider form's model picker is shown on a computer with 32 GB of memory
- **THEN** the note advises the largest model this computer can run, says that the best one depends on the languages, and names `gemma4:26b` as a suggestion that did well for Ukrainian, with the `ollama pull` command, as text, holding the exact tag the suggestion holds for it

#### Scenario: A 48 GB computer

- **WHEN** the provider form's model picker is shown on a computer with 48 GB of memory
- **THEN** the note is the one of a 32 GB computer: `gemma4:26b` is the suggestion and no larger model is named

#### Scenario: Nothing is pulled

- **WHEN** the pull command is shown
- **THEN** BookLoom runs nothing and starts no download

#### Scenario: No pull line for LM Studio

- **WHEN** the model-pick card or the provider form's model picker is shown for the provider `lmstudio` on a computer with 16 GB of memory
- **THEN** no model is suggested and no `ollama pull` text is shown

### Requirement: Choose how the temperature is sent for a model

The Models tab SHALL offer for the current provider's model Let BookLoom choose (the default), My value (one value for every request) and Don't send (the temperature is left out entirely).

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`. In plain words: most models want the tuned values, some people want one fixed value, and a few models refuse the setting altogether, so the choice is explicit and Don't send exists for them. The tab has no chooser any more, so the choice is made for the model the current provider uses.

#### Scenario: My value sets one temperature

- **WHEN** My value is chosen for `gemma4:e4b` with `0.5`
- **THEN** the Models tab shows `0.5` as the value that every request to that model will carry

#### Scenario: Don't send is selectable

- **WHEN** Don't send is chosen for `gpt-oss:20b`
- **THEN** the Models tab shows that no temperature will be sent to that model

### Requirement: Set a model's context window and answer length

The Models tab SHALL offer for the current provider's model a Use context window number (filled in from what is loaded) from 512 to 1,048,576 tokens, which is the one size every call to that model uses and is read-only where BookLoom cannot set the window on that engine, a Limit answer length switch no larger than the window, and under Advanced the field that carries the answer length, `max_completion_tokens` or `max_tokens`, which SHALL NOT apply to Ollama.

**Source**: `design.md#d27--the-models-tab-edits-the-current-model-the-facts-move-to-the-provider-form`. In plain words: a person can widen the window a model reads or cap how long it may answer, and for OpenAI-style servers choose the field name the server expects. Ollama has no such choice, so the field is not offered for it. Where the server owns the window, the number is shown but cannot be edited, so it never pretends to be a setting. The tab has no chooser any more, so these are the limits of the model the current provider uses.

#### Scenario: A context window outside its range

- **WHEN** Use context window is on with `256`, or with `2000000`
- **THEN** Save is held and the tab asks for a context window from 512 to 1,048,576 tokens

#### Scenario: An answer cap larger than the window

- **WHEN** Use context window is `8192` and Limit answer length is `8193`
- **THEN** Save is held and the tab says the answer length limit cannot be larger than the context window

#### Scenario: The context window is filled in from the program

- **WHEN** `gemma4:e4b` has 16384 tokens loaded and the tab is opened
- **THEN** Use context window shows `16384`

#### Scenario: The window is read-only where BookLoom cannot set it

- **WHEN** the model belongs to LM Studio, which loads the window itself
- **THEN** the context window number is shown, cannot be edited, and the Context row says LM Studio sets it

#### Scenario: The field choice is absent for Ollama

- **WHEN** the model belongs to an Ollama provider, whether reached by its own connection or through `/v1`
- **THEN** the Advanced answer-length field choice is not offered

#### Scenario: The field choice is offered for OpenAI-compatible servers

- **WHEN** the model belongs to a generic OpenAI-compatible provider
- **THEN** Advanced offers `max_completion_tokens` and `max_tokens`

#### Scenario: A server that ignores the cap is named

- **WHEN** the Advanced features stage found that the server answered past an answer cap of 16 tokens
- **THEN** a note under the answer-length field says the server ignored the cap in the last Advanced features check

#### Scenario: The loaded figure is a suggestion while the window is off

- **WHEN** nothing is stored for `gpt-oss:20b`, 16384 tokens are loaded and Use context window is off
- **THEN** the number field shows `16384` disabled, the draft is unchanged and the footer shows no unsaved mark

## REMOVED Requirements

### Requirement: Say which providers did not list their models on the Models tab

**Reason**: The Models tab no longer lists models, so it reads no provider's model list and has no line under a chooser to explain a short list.

**Migration**: The provider form already says when its own provider's list could not be read ("An unreadable listing says so" under "Choose a model from the list, or name one the list does not hold"). Nothing is stored for this line.

## RENAMED Requirements

- FROM: `### Requirement: Describe each model on the Models tab and keep its own settings`
- TO: `### Requirement: Edit the settings of the current provider's model on the Models tab`
- FROM: `### Requirement: Show who a model is on the Models tab's identity line`
- TO: `### Requirement: Show who a model is beside the provider's model picker`
- FROM: `### Requirement: Say how good a model's quant is on the Models tab`
- TO: `### Requirement: Say how good a model's quant is beside the provider's model picker`
- FROM: `### Requirement: Say which languages a model declares on the Models tab`
- TO: `### Requirement: Say which languages a model declares beside the provider's model picker`
- FROM: `### Requirement: Date the model facts at the foot of the Models tab`
- TO: `### Requirement: Date the model facts under the provider's model facts`
