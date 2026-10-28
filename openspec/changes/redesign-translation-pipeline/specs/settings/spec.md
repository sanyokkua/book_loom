## ADDED Requirements

### Requirement: Show who a model is on the Models tab's identity line

The Models tab SHALL show for the chosen model one identity line, `Understood: {model} · {quant} · {match}`, where the match part reads "exact match", "recognised as {family}, not this exact model" or "recognised as a fine-tune of {base} (behaviour of {base})", and the quant part reads `{label} (server reports {base})` when the name's quant label differs from the one the server reports.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d19--model-identity-reading-model-names`. In plain words: before changing anything, the person sees how BookLoom read the model's name and whether it knows this exact model, a close relative, or only its architecture. That decides how much to trust the advice shown below the line.

#### Scenario: An exact match

- **WHEN** the Models tab is opened on `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` and the server reports the quant `Q4_K_M`
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

### Requirement: Say how good a model's quant is on the Models tab

The Models tab SHALL show under the identity line the sentence for the model's quant tier, none for excellent, naming the open project's target language where the sentence has one, and SHALL NOT block or change any setting because of it. The tier itself is decided by the llm-provider capability.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: a heavily compressed download translates worse, especially for names and word endings. The person is told plainly and still decides for themselves.

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

### Requirement: Show the quant chip beside every model in every model picker

The application SHALL show beside each model in every model picker (the Providers model list, the Models chooser and the AI-model choice before Start) a chip with the quant token of the name, or the quant the server's facts report when the name has none, and its tier, such as `Q4_K_M · acceptable`; it SHALL show no chip while no quant is known, and the chip SHALL keep its full width and never be cut short.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: a person choosing among six downloads of one model sees at once which one is the compressed one. A chip that is cut to "Q4_K_…" would defeat the purpose.

#### Scenario: A good chip

- **WHEN** the Providers model list holds `hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL`
- **THEN** its row carries the chip `UD-Q4_K_XL · good`

#### Scenario: An acceptable chip and an unusable chip

- **WHEN** the Models chooser holds `phi4-mini` (`Q4_K_M`, 3.8B) and `Qwen_Qwen3.5-9B-Q2_K_L.gguf`
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

### Requirement: Mark cloud models with a neutral label in every model picker

The application SHALL show the neutral label "cloud" beside each model of the Ollama cloud in every model picker, SHALL leave every model listed, and SHALL NOT ask for consent.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, the owner's decision that cloud models are shown with a neutral label, nothing hidden and nothing asked. In plain words: a person can see which models run on someone else's computer, without being warned off or made to click through a dialog.

#### Scenario: A cloud model is labelled

- **WHEN** the Ollama model list holds `gpt-oss:120b-cloud` and `gemma4:e4b`
- **THEN** the row of `gpt-oss:120b-cloud` carries the label `cloud` and the row of `gemma4:e4b` does not

#### Scenario: Choosing a cloud model asks nothing

- **WHEN** `gpt-oss:120b-cloud` is chosen and saved
- **THEN** no consent dialog and no acknowledgement checkbox is shown

### Requirement: Narrow every model picker by the typed model name

The application SHALL show a text field with the placeholder "Filter models by name" above the list of every model picker (the Providers model list, the Models chooser and the AI-model choice before Start), SHALL narrow the list as the person types to the models whose id contains the typed text ignoring letter case, and WHEN no id matches SHALL show `No model name contains "{text}".`

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`, the owner's decision that every model picker has a name filter. In plain words: a server with sixty downloads is hard to scan. Typing `e4b` should leave the e4b models.

#### Scenario: Typing narrows the list

- **WHEN** the Models chooser holds `gemma4:e4b`, `gemma4:26b` and `qwen3.5:9b` and `e4b` is typed
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

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: a person at the keyboard can filter, step into the list and pick, and a half-typed filter never silently drops the choice already made.

#### Scenario: Esc clears the text

- **WHEN** `e4b` is typed in the filter of the Models chooser and Esc is pressed
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

### Requirement: Say which languages a model declares on the Models tab

The Models tab SHALL show for the chosen model a neutral line saying whether its maker lists languages — "Declared languages: {count} (includes {codes})", "Declared languages: English only", "Declared languages: not stated by the maker" or "Declared for its base model {base}: {codes}" — and WHERE the open project's language pair is one BookLoom tested the model on, SHALL add "· BookLoom tested it for {source} → {target}".

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`. In plain words: the maker's list is what the maker says, not what was measured, so the line says "declared" and adds BookLoom's own test only where there is one, for the pair it covered. The state and the tested pairs are decided by the llm-provider capability ("Say whether a model's maker declares a language", "Count a language pair as tested only by BookLoom's own runs").

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

### Requirement: Warn under the AI-model choice and at Start when the model does not declare the book's languages

WHERE the project's source and target languages are known, the application SHALL show under the AI-model choice and at Start one line for the side in the worse state, naming both languages when they tie: a warning when the maker lists languages without it, a quieter information line when the maker says nothing or only a base model lists it, and no line when the model's own row declares it or BookLoom tested it for that pair; the line SHALL NOT block Start.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d22--quant-and-language-advice`, the owner's decisions that only BookLoom's own tests count as tested and that a warning is shown only for a real gap. In plain words: a Llama model that lists eight languages, none of them Russian or Ukrainian, deserves a warning. A Gemma model whose maker says nothing deserves a quiet hint, and a pair BookLoom measured deserves silence.

#### Scenario: Both languages are missing from the maker's list

- **WHEN** a Russian to Ukrainian project has `llama3.1:8b` chosen
- **THEN** a warning reads "Llama 3.1 does not list Russian and Ukrainian among its languages. Translation may have problems." and Start stays available

#### Scenario: The maker says nothing

- **WHEN** an English to Ukrainian project has `gemma4:12b` chosen, which BookLoom has not tested
- **THEN** a quieter information line reads "The maker of Gemma 4 does not say which languages it supports. Check a few paragraphs first."

#### Scenario: BookLoom tested the pair

- **WHEN** an English to Ukrainian project has `gemma4:e4b` chosen
- **THEN** no language line is shown

#### Scenario: A pair BookLoom did not test keeps its line

- **WHEN** a Russian to Ukrainian project has `gemma4:e4b` chosen
- **THEN** the information line "The maker of Gemma 4 does not say which languages it supports. Check a few paragraphs first." is shown

#### Scenario: Only the source side is missing

- **WHEN** a Latin to Ukrainian project has `mistral-small3.2:24b` chosen, which lists Ukrainian but not Latin
- **THEN** the warning names Latin only: "Mistral Small 3.2 does not list Latin among its languages. Translation may have problems."

#### Scenario: A base model's list is named

- **WHEN** a Russian to Ukrainian project has a Hermes 4 14B download chosen and its base model Qwen3 lists both languages
- **THEN** an information line reads "Hermes 4 does not list its languages; its base model Qwen3 lists Russian and Ukrainian."

### Requirement: Show a quant line at Start for a model that needs one

WHEN a translation is about to start, the application SHALL show under the estimate one line with the sentence of the model's quant tier when the tier is poor, unusable or not supported, or acceptable on a small model of at most 5B, and SHALL NOT show it where BookLoom measured that model at that quant; the line SHALL NOT block Start.

**Source**: `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-book-brief`, `design.md#d22--quant-and-language-advice`. In plain words: a small model at four bits drifts on names and endings, so the person is told once before spending hours. Where BookLoom has itself measured the model at that quant, the warning would only be noise.

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
- **THEN** no quant line is shown, while the chip and the Models tab sentence stay

### Requirement: Show the thinking hint for a download that cannot turn thinking off

WHERE an Ollama download belongs to a model family whose library model has a thinking switch but the download has none (its `/api/show` shows no renderer and no thinking capability), the Models tab and Start SHALL show the hint "This download cannot turn thinking off in {engine}, so it is slower. The library model {libraryTag} or a Modelfile with RENDERER and PARSER fixes it." and SHALL NOT block.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: some downloads think at length and cannot be told to stop, only because Ollama serves them without the library model's switch. The person is told why the run is slow and how to get a model that behaves. A model that always thinks by design, such as gpt-oss or a reasoning build, gets no such hint, because nothing fixes it; its Thinking row says so instead.

#### Scenario: The hint on the Models tab

- **WHEN** the Models tab shows an Ollama download of Qwen3.5 9B that cannot turn thinking off
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

WHERE a model's licence allows only noncommercial use, the Models tab and Start SHALL show the line "{model} is licensed for noncommercial use only (PolyForm Strict 1.0.0)." and SHALL NOT block.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a person who translates a book to publish it should hear about a licence limit before the run, not after.

#### Scenario: The note on the Models tab

- **WHEN** `hf.co/bartowski/thomsonreuters_Thomson-1.0-Small-GGUF` is chosen
- **THEN** the Models tab shows "Thomson 1.0 Small is licensed for noncommercial use only (PolyForm Strict 1.0.0)."

#### Scenario: The same note at Start

- **WHEN** Start is about to run on that model
- **THEN** the same line is shown under the estimate and Start stays available

#### Scenario: An open licence shows nothing

- **WHEN** `gemma4:e4b` is chosen
- **THEN** no licence line is shown

### Requirement: Date the model facts at the foot of the Models tab

The Models tab SHALL show at its foot "Model facts as of {date}", the date of the model knowledge shipped with the application.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: the advice on this tab comes from tables shipped with the program. A person must be able to tell how old they are.

#### Scenario: The date is shown

- **WHEN** the Models tab is opened and the shipped model knowledge is dated 2026-10-28
- **THEN** its foot reads "Model facts as of 2026-10-28"

### Requirement: Recommend a model by the computer's memory where models are chosen

The application SHALL mark as recommended, in the note under the model chooser and at Start, `gemma4:e4b` when the computer has under 24 GB of memory and `gemma4:26b-a4b` from 24 GB, SHALL offer `gemma4:31b` as an opt-in "best quality, slower, not measured here" from 36 GB, and SHALL offer the `ollama pull` command as text and never run it.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#candidate-models`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a bigger model that does not fit in memory is slower than a smaller one that does. The computer's memory picks the default, and the person pulls the model themselves.

#### Scenario: A 16 GB computer

- **WHEN** the model chooser is shown on a computer with 16 GB of memory
- **THEN** the note names `gemma4:e4b` as recommended and shows the text `ollama pull gemma4:e4b`

#### Scenario: A 32 GB computer

- **WHEN** the model chooser is shown on a computer with 32 GB of memory
- **THEN** the note names `gemma4:26b-a4b` as recommended

#### Scenario: A 48 GB computer

- **WHEN** the model chooser is shown on a computer with 48 GB of memory
- **THEN** `gemma4:26b-a4b` is recommended and `gemma4:31b` is offered as "best quality, slower, not measured here"

#### Scenario: Nothing is pulled

- **WHEN** the pull command is shown
- **THEN** BookLoom runs nothing and starts no download

### Requirement: Show what a model offers for thinking and let the person override it

The Models tab SHALL show for the chosen model a Thinking row that says what thinking control the model offers and that BookLoom sends thinking off on every call, and SHALL offer "Recommended (off)", "Allow on proofreading and names" and "Always"; WHERE the model has no thinking control, or always thinks, the row SHALL say so, name the level BookLoom sends if any, and offer no choice.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that thinking and sampling are set per model on the Models tab. In plain words: thinking helps some tasks and slows all of them, and it differs from model to model. So the choice lives with the model, and the safe default is off.

#### Scenario: A model with a switch

- **WHEN** the Models tab is opened on `gemma4:e4b-mlx`, which offers thinking on and off
- **THEN** the Thinking row says it can be turned on and off, says BookLoom sends off on every call, and has "Recommended (off)" selected

#### Scenario: The override is stored

- **WHEN** "Allow on proofreading and names" is chosen for `gemma4:e4b-mlx` and Save is used
- **THEN** the choice is read again after a restart and applies from the next request to that model

#### Scenario: Another model keeps its own choice

- **WHEN** "Always" is stored for `gpt-oss:20b` and `gemma4:e4b-mlx` is opened
- **THEN** the Thinking row of `gemma4:e4b-mlx` still shows "Recommended (off)"

#### Scenario: A model with no thinking control

- **WHEN** the Models tab is opened on `qwen2.5:1.5b`, whose capabilities list no thinking
- **THEN** the Thinking row says the model has no thinking control and offers no choice

#### Scenario: A model that always thinks at a level

- **WHEN** the Models tab is opened on `gpt-oss:20b`, whose levels are `low`, `medium` and `high`
- **THEN** the Thinking row reads "Always thinks; BookLoom sends the lowest level, low." and offers no choice

#### Scenario: A model that always thinks with no level

- **WHEN** the Models tab is opened on `mistralai/ministral-3-14b-reasoning`, whose LM Studio reasoning options are `["on"]`
- **THEN** the Thinking row reads "Always thinks; this cannot be changed." and offers no choice

### Requirement: Show BookLoom's sampling choice for a model and let the person override it

The Models tab SHALL show for the chosen model a Sampling row that names what BookLoom sends (the temperature per call kind and the samplers its profile sets) and SHALL offer two choices, Profile default and Custom, Custom with fields for temperature, top_k, top_p and min_p where an empty field keeps the profile's value and a filled temperature is the tab's temperature choice My value.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`, the owner's decision that thinking and sampling are set per model on the Models tab. In plain words: sampling decides how adventurous the wording is. The profile's values are right for most models, and a person with a reason can type their own. There is no "server's own" choice: on LM Studio, llama-server and Ollama's OpenAI path the server's defaults are not neutral, so leaving samplers out would bring back the values the inference capability's "Choose sampling from the model profile" keeps out. The temperature field and the temperature choice already on the tab are one stored value, so they cannot disagree.

#### Scenario: The default is shown

- **WHEN** the Models tab is opened on a model with no stored choice
- **THEN** the Sampling row names the temperature per call kind and the samplers BookLoom sends, with Profile default selected

#### Scenario: Only two choices are offered

- **WHEN** the Sampling choice is opened for `gemma4:e4b-mlx`
- **THEN** it offers Profile default and Custom, and no choice that leaves sampling to the server

#### Scenario: Custom values are typed

- **WHEN** Custom is chosen with top_k `40` and top_p `0.9`, and temperature and min_p left empty
- **THEN** the row shows `top_k 40` and `top_p 0.9` as what every request to that model will carry, the profile's min_p, and the temperature per call kind unchanged

#### Scenario: A custom temperature is the temperature choice

- **WHEN** Custom is chosen with temperature `0.5` and saved
- **THEN** the tab's temperature choice reads My value `0.5`

#### Scenario: An invalid custom value holds Save

- **WHEN** Custom is chosen with top_p `1.5`
- **THEN** Save is held and the tab asks for a top_p from 0 to 1

### Requirement: Name an engine's address form in the address hint for llama-server

WHERE the provider is treated as llama-server, the address field SHALL show the hint `http://127.0.0.1:9931/v1`.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: llama-server has no fixed port and its address ends in `/v1`. A hint shows the shape of a working address.

#### Scenario: The hint appears for llama-server

- **WHEN** a provider is added and treated as llama-server
- **THEN** its empty address field shows the hint `http://127.0.0.1:9931/v1`

#### Scenario: Another engine keeps its own hint

- **WHEN** a provider is treated as LM Studio
- **THEN** its address hint is not `http://127.0.0.1:9931/v1`

### Requirement: Show the detected server engine under the provider's address

The Providers form SHALL show under the address one line naming the detected engine: "Detected: {engine} {version} · {connection}" for a recognised server, or "Not recognised: generic OpenAI-compatible server".

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama, LM Studio and llama-server all speak a similar dialect but need different requests. The person sees which one BookLoom thinks is answering, and a wrong guess is visible. Detection itself is the llm-provider capability's "Detect the server engine only when the person saves, tests or asks"; a provider saved as Ollama that answers as another engine fails its test with the message of the llm-provider capability's "Fail the test of an Ollama provider whose server is another engine".

#### Scenario: Ollama is recognised

- **WHEN** Save is used on the address `http://127.0.0.1:11434` and Ollama 0.40.11 answers
- **THEN** the line reads "Detected: Ollama 0.40.11 · native connection"

#### Scenario: llama-server and LM Studio are recognised

- **WHEN** `http://127.0.0.1:9931/v1` answers as llama-server build b11669, and `http://127.0.0.1:1234/v1` answers as LM Studio
- **THEN** the lines read "Detected: llama-server b11669 · OpenAI-compatible" and "Detected: LM Studio · OpenAI-compatible"

#### Scenario: An unknown server

- **WHEN** the address answers like an OpenAI-compatible server that matches no known engine
- **THEN** the line reads "Not recognised: generic OpenAI-compatible server"

### Requirement: Let the person detect the engine again

WHEN the person uses "Detect again" beside the detected-engine line, the application SHALL ask the server again, SHALL update the line from the answer, and SHALL send no model request.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: after the person upgrades or swaps the program behind an address, one press makes BookLoom look again. It costs no model time.

#### Scenario: The engine changed behind the address

- **WHEN** the line reads "Detected: Ollama 0.40.11 · native connection", the program behind the address is replaced by llama-server b11669 and "Detect again" is used
- **THEN** the line reads "Detected: llama-server b11669 · OpenAI-compatible"

#### Scenario: Detecting again is allowed while a run holds the model

- **WHEN** a translation is running and "Detect again" is used
- **THEN** the line is updated and no "BookLoom is translating" message is shown

#### Scenario: The server does not answer

- **WHEN** "Detect again" is used and nothing answers at the address
- **THEN** the line keeps its last text and the screen says the server does not answer

### Requirement: Let the person name the engine of a server

The Providers form SHALL offer "Treat this server as…" listing the engines Ollama, LM Studio, llama-server, Unsloth, OpenRouter and generic OpenAI-compatible, SHALL show the stored choice as chosen, and SHALL NOT offer it for an app the llm-provider capability refuses as one that cannot translate a book.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: detection can be wrong behind a proxy. The person can say what the server really is; what the choice does to the requests, and that it lasts until Detect again, is the llm-provider capability's "Let the person choose the engine with Treat this server as…", and the apps it refuses are its "Refuse an app that cannot translate a book at the connection test".

#### Scenario: A choice is stored

- **WHEN** a provider behind a reverse proxy is not recognised and "Treat this server as…" is set to llama-server and saved
- **THEN** after a restart the form shows llama-server chosen under "Treat this server as…"

#### Scenario: The list of engines

- **WHEN** the choice is opened
- **THEN** it lists Ollama, LM Studio, llama-server, Unsloth, OpenRouter and generic OpenAI-compatible

#### Scenario: No offer for an unusable app

- **WHEN** the provider's address is AnythingLLM's `http://127.0.0.1:3001/api/v1/openai`
- **THEN** "Treat this server as…" is not offered

### Requirement: Offer Ollama's own connection once when Ollama is behind an OpenAI-compatible provider

WHEN the llm-provider capability makes its one offer of Ollama's own connection, the Providers form SHALL show it inline as "This is Ollama. Use Ollama's own connection (recommended)? It keeps the window, overflow and sampling controls." with the buttons Use it and Keep as is.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Ollama's own connection lets BookLoom set the window and handle overflow, and the OpenAI-style one does not. The person is told, and the choice stays theirs. When the offer is made, that it is made only once and that the provider never switches by itself are the llm-provider capability's "Offer Ollama's own connection once when Ollama answers behind an OpenAI-compatible provider".

#### Scenario: The offer is shown

- **WHEN** Test is used on a provider saved as OpenAI-compatible at `http://127.0.0.1:11434/v1` and Ollama answers
- **THEN** the inline offer is shown with Use it and Keep as is, and the provider's kind is unchanged

#### Scenario: Use it switches the connection

- **WHEN** Use it is chosen
- **THEN** the provider uses Ollama's own connection and the offer is gone

#### Scenario: Keep as is is remembered

- **WHEN** Keep as is is chosen and Test is used again
- **THEN** the offer is not shown again for that provider

#### Scenario: Detect again keeps the offer declined

- **WHEN** Keep as is was chosen and Detect again is used
- **THEN** the offer is not shown and "Treat this server as…" shows no engine chosen by the person

### Requirement: Say that LM Studio has no model with the typed id

WHEN the llm-provider capability's id check stops a test or a start because LM Studio does not list the model id, the application SHALL show `LM Studio has no model "{id}". It would answer with the model that is loaded. Pick a model from the list.`

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio answers with whatever is loaded when the id is wrong, so a book could be translated by the wrong model without anyone noticing; the screen says why it stopped. The check itself is the llm-provider capability's "Check the model id against the server's own list where any id is served".

#### Scenario: An unknown id

- **WHEN** Test inference is used on LM Studio with the id `gemma-4-e4b-typo` and the server lists no such id
- **THEN** the test stops and the screen reads `LM Studio has no model "gemma-4-e4b-typo". It would answer with the model that is loaded. Pick a model from the list.`

#### Scenario: A listed id passes

- **WHEN** the id is `google/gemma-4-e4b` and the server lists it
- **THEN** no such message is shown

### Requirement: Say that a typed LM Studio variant was removed

WHEN the llm-provider capability removes a typed `@variant` from an LM Studio model id, the Providers form SHALL say once `"@{variant}" was removed from the model id: it would load a second copy with default settings.`

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: the person typed something and BookLoom changed it, so the screen says what was changed and why. The removal itself is the llm-provider capability's "Never send an LM Studio quant variant".

#### Scenario: A variant is removed

- **WHEN** the typed id is `google/gemma-4-e4b@q4_k_m` and the server lists `google/gemma-4-e4b`
- **THEN** the id becomes `google/gemma-4-e4b` and the screen reads `"@q4_k_m" was removed from the model id: it would load a second copy with default settings.`

### Requirement: Say at Start that LM Studio will load a model with its own settings

WHEN Start is about to run on an LM Studio model that is not loaded, the application SHALL show the line "LM Studio will load it with its own default settings; the window may differ." and SHALL NOT block Start.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: LM Studio loads a model on first use with settings of its own, so the window BookLoom budgets for may not be the one in use. The person hears it before the run.

#### Scenario: A model that is not loaded

- **WHEN** Start is about to run on the LM Studio model `google/gemma-4-e4b` and it is not loaded
- **THEN** the line "LM Studio will load it with its own default settings; the window may differ." is shown and Start stays available

#### Scenario: A loaded model shows no such line

- **WHEN** the same model is loaded
- **THEN** the line is not shown

### Requirement: Run the optional Advanced features check for a model

The Providers test SHALL offer the optional stage "Advanced features (optional, uses the model)" for the selected model, never started by the other stages, and SHALL show one verdict line per check it ran, also on the Models tab of that model.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`, `design.md#d17--server-engines-detection-and-traits`. In plain words: a server of unknown make may or may not honour structured answers, a thinking switch, extra samplers or an answer cap. A few short, invented-text calls tell, and the answer is remembered. They are optional because they use the model. Which checks are sent and how the verdicts are stored is the llm-provider capability's "Check what a server honours only when the person asks".

#### Scenario: Verdicts are listed

- **WHEN** the Advanced features stage runs for `qwen3.5-9b` on a generic server
- **THEN** the lines read "Structured answers: honoured", "Turning reasoning off: works with `reasoning_effort`", "top_k/min_p: ignored" and "Answer cap: `max_tokens`"

#### Scenario: The stage is optional

- **WHEN** Test connection, Test models and Test inference are used without the Advanced features stage
- **THEN** no Advanced features call is sent

#### Scenario: Verdicts survive a restart

- **WHEN** the stage ran for `qwen3.5-9b` and the application is restarted
- **THEN** the Models tab still shows the verdicts for that model

#### Scenario: A run holds the model

- **WHEN** a translation is running and the stage is started
- **THEN** the stage is refused before any call is sent, the same way as Test inference, and runs once the run is paused

### Requirement: Say that a check is not started while a run holds the model

WHILE a translation run is translating, the Providers screen SHALL refuse Test inference and the Advanced features stage before any model call is sent and SHALL show "BookLoom is translating. Try again when the run ends or while it is paused."; WHILE the run is paused, stopped or ended, both SHALL run.

**Source**: `docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#three-stage-verification`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: a local model answers one call at a time, so BookLoom already refuses a model check while a run translates and lets it run while the run is paused. Two things change: the sentence the Providers screen shows (it read "Unavailable while “Translation” is running. Wait for it to finish or stop it.") and the new Advanced features stage, which is refused the same way. The other model actions keep the `app-shell` capability's "Disable model actions while a translation is at work".

#### Scenario: Test inference during a run

- **WHEN** a translation is running with `gemma4:e4b` and Test inference is used
- **THEN** no request reaches the model and the screen shows "BookLoom is translating. Try again when the run ends or while it is paused."

#### Scenario: A pause frees the model

- **WHEN** the run is paused and Test inference is used
- **THEN** the test runs

#### Scenario: Connection tests are not model calls

- **WHEN** a translation is running and Test connection or Test models is used
- **THEN** the test runs and no "BookLoom is translating" message is shown

### Requirement: Show the privacy line of a provider with its host

The Providers area SHALL show for a selected provider whose host is not a loopback address (`127.0.0.1`, `::1` or `localhost`) "Your book stays private when you use a local model. With a provider outside this computer, the text you translate is sent to {host}.", for a loopback host only its first sentence, and no consent dialog or acknowledgement checkbox for any provider.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`, the owner's decision that privacy is stated as "private when used with local models" and that no consent is asked. In plain words: BookLoom keeps books on the machine only when the model is local. For any other provider it says where the text goes, and leaves the choice to the person.

#### Scenario: A provider outside this computer

- **WHEN** the OpenRouter provider at `openrouter.ai` is selected
- **THEN** the line reads "Your book stays private when you use a local model. With a provider outside this computer, the text you translate is sent to openrouter.ai."

#### Scenario: A local provider shows its own host

- **WHEN** the Ollama provider at `127.0.0.1:11434` is selected
- **THEN** the line reads "Your book stays private when you use a local model." and names no host

#### Scenario: Saving a remote provider asks nothing

- **WHEN** the OpenRouter provider is saved and made current
- **THEN** no consent dialog and no acknowledgement checkbox is shown

### Requirement: Note that LM Studio keeps request text in its own log

WHERE the selected provider is LM Studio, the Providers area SHALL show "LM Studio keeps request text in its own server log unless you turn that off in LM Studio."

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: the book's text stays on the machine, but LM Studio may write it to a log file there. The person is told, and can turn that off in LM Studio.

#### Scenario: The note is shown for LM Studio

- **WHEN** the LM Studio provider is selected
- **THEN** the note is shown under the privacy line

#### Scenario: The note is not shown for Ollama

- **WHEN** the Ollama provider is selected
- **THEN** the note is not shown

### Requirement: Help the person set up an Unsloth server

WHERE the provider is treated as Unsloth, the Providers form SHALL show the key hint "Create a key in Unsloth › Settings › API and put its environment-variable name here, for example UNSLOTH_API_KEY.", the line "Unsloth's own chat tools (web search, code) are separate from BookLoom; BookLoom turns them off on every request.", and, WHEN the sign-in is refused, the sentence "Unsloth needs an API key. Create one in Unsloth › Settings › API and put its environment-variable name here."

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: Unsloth needs a key and has its own chat tools. The form says where to make the key, that the key is named by a variable and never typed in, and that BookLoom keeps the tools out of the translation.

#### Scenario: The hint and the tools note

- **WHEN** a provider is treated as Unsloth
- **THEN** the form shows the key hint and the tools note

#### Scenario: A refused sign-in names the fix

- **WHEN** Test connection on the Unsloth provider is answered with HTTP 401
- **THEN** the result carries "Unsloth needs an API key. Create one in Unsloth › Settings › API and put its environment-variable name here."

#### Scenario: Another engine shows neither

- **WHEN** a provider is treated as Ollama
- **THEN** the key hint and the tools note are not shown

### Requirement: Say in one sentence why an app cannot be used

WHEN the llm-provider capability refuses an app at the connection test, the application SHALL show one plain sentence for its reason: "This app does not support structured replies, so BookLoom cannot use it." for `not-suitable-structured`, or "This address chats with documents, not with a model." for `not-suitable-documents`.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#providers-tab`, `design.md#d17--server-engines-detection-and-traits`. In plain words: some desktop apps look like an AI server but cannot do the job. A person told why gives up quickly and does not hunt through error codes. How the apps are recognised is the llm-provider capability's "Refuse an app that cannot translate a book at the connection test".

#### Scenario: GPT4All

- **WHEN** Test connection reaches GPT4All at `http://127.0.0.1:4891/v1`, whose model rows carry `"owned_by":"humanity"`
- **THEN** the result reads "This app does not support structured replies, so BookLoom cannot use it."

#### Scenario: AnythingLLM

- **WHEN** Test connection reaches AnythingLLM at `http://127.0.0.1:3001/api/v1/openai`
- **THEN** the result reads "This address chats with documents, not with a model."

#### Scenario: A usable server passes

- **WHEN** Test connection reaches a llama-server
- **THEN** neither sentence is shown

### Requirement: Understand a pasted model name in the Add a model id box

WHEN a Hugging Face URL, an `ollama run`, `llama-server -hf` or `lms get` command, a file name or a repository id is pasted into the "Add a model id" box, the application SHALL show one line under it saying what was understood, `Understood: Hugging Face repo {repo}, file {file} → {model} · {quant}`, SHALL never fetch the URL, and WHERE the id is not in the server's list SHALL add "Not in this server's list yet."

**Source**: `docs/specification/01_Product/07_SETTINGS.md#models-tab`, `design.md#d19--model-identity-reading-model-names`. In plain words: people copy a model's link or its pull command from a web page. BookLoom reads it, says what it made of it, and never goes to the internet to check.

#### Scenario: A repository and a file

- **WHEN** `https://huggingface.co/unsloth/GLM-4.7-Flash-GGUF/blob/main/GLM-4.7-Flash-UD-Q4_K_XL.gguf` is pasted
- **THEN** the line reads `Understood: Hugging Face repo unsloth/GLM-4.7-Flash-GGUF, file GLM-4.7-Flash-UD-Q4_K_XL.gguf → GLM 4.7 Flash · UD-Q4_K_XL` and the URL is not requested

#### Scenario: An Ollama pull command is offered as text

- **WHEN** the same paste is made for an Ollama provider
- **THEN** the box offers the text `ollama pull hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL` and runs nothing

#### Scenario: An id the server does not list

- **WHEN** the pasted id is not in the server's model list
- **THEN** the line ends with "Not in this server's list yet."

#### Scenario: Plain model ids pass through

- **WHEN** `gemma4:e4b` is typed
- **THEN** it is accepted as the id as before

### Requirement: Show what each quality choice does on Generation

The Generation tab SHALL show what Fast, Balanced and Max do as a read-only table with the rows "Fixing calls", "Proofread" and "Extra model calls", SHALL say under it "The same for every model.", and SHALL offer no Thinking and no Sampling choice.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#generation-tab`, `design.md#d2--the-runs-three-stages-and-the-quality-dial`, the owner's decision that the dial means the same for every model. In plain words: the person sees what Fast, Balanced and Max do, and what each costs in calls, without being able to break them. Thinking and sampling moved to the model they belong to.

#### Scenario: The rows and their cells

- **WHEN** the Generation tab is opened
- **THEN** Fixing calls reads "none, marks for review (lost formatting marks retried once)" for Fast and "one repair per piece" for Balanced and Max
- **AND** Proofread reads "marks for review, no model calls" for Fast, "marked and doubtful windows" for Balanced and "marked and doubtful windows, then the whole book re-read" for Max

#### Scenario: Extra model calls count calls, not hours

- **WHEN** the Generation tab is opened and the current model's batch size is 4
- **THEN** Extra model calls reads "no extra calls" for Fast, "about one call per marked passage" for Balanced and "about one call per marked passage, plus one call per 4 paragraphs of the whole book" for Max

#### Scenario: The same for every model

- **WHEN** the Generation tab is opened
- **THEN** the line "The same for every model." is shown under the table and none of its cells can be edited

#### Scenario: Thinking and Sampling are not on Generation

- **WHEN** the Generation tab is opened
- **THEN** it holds no Thinking before answering choice and no Sampling choice

#### Scenario: The old rows are gone

- **WHEN** the Generation tab is opened
- **THEN** the table has no row for the reviewer, the backward read or a model-written summary

### Requirement: Show the time limits on Generation as fixed values

The Generation tab SHALL list the time limits as read-only rules: a prescan waits 120 s, longer for a model that always thinks; every other call waits 0.5 s per expected output token, at least the provider's request timeout and at most 600 s; a stream that sends nothing for 60 s is stopped. It SHALL offer no control to change them.

**Source**: `docs/specification/01_Product/07_SETTINGS.md#generation-tab`, S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`). In plain words: how long BookLoom waits for each kind of call follows fixed rules, and the tab says what they are rather than offering a knob that is not there. The rules themselves are the llm-provider capability's "Wait for a chat call by its kind, its expected output and its model" and "Read an Ollama-native reply as a stream that must keep arriving".

#### Scenario: The limits cannot be edited

- **WHEN** the Generation tab is opened
- **THEN** it lists "Prescan: 120 s (longer for a model that always thinks)", "Other calls: 0.5 s per expected token, at least the provider's request timeout, at most 600 s" and "Silence while streaming: 60 s", and none can be edited

### Requirement: Report what the program says about a model, engine by engine

The Models tab SHALL show, as found out from the program, how much the model can read at once, who sets that window on this engine, how much is loaded now where the program says so, and for each kind of call whether structured answers come as a schema, as plain JSON or not at all, each from a test or from a refusal. It SHALL NOT store what it read from a server.

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: before changing a number, a person sees what the model itself reports about its limits and who controls them. The structured-answer row follows what BookLoom learned for each kind of call, since a server may take a schema for one and refuse it for another.

#### Scenario: Reported limits are shown

- **WHEN** `gemma4:e4b` reports a context length of 131072 and 16384 loaded
- **THEN** the Models tab shows both figures under "Found out from the program"

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
- **THEN** the Models tab shows only how much it can read at once and no loaded figure, and no `/api/ps` request is sent

#### Scenario: Structured answers are shown per kind of call

- **WHEN** a test found that `gpt-oss:20b` honours a schema, and Name reading was refused a schema and now uses plain JSON
- **THEN** the Models tab shows Translate as a schema and Name reading as plain JSON

#### Scenario: Nothing is known yet

- **WHEN** no test of `gemma4:e4b` has run and nothing was refused since the application started
- **THEN** the Models tab reads "structured answers: not checked yet" and names the Providers tab test that checks it

### Requirement: Choose when to stop and ask, and the defaults, on Automation

The Automation tab SHALL offer When to stop and ask you, a default quality, and switches to open the review desk when a translation finishes with segments to review, to Keep the computer awake (on by default) and to Reopen the last project (on by default), and SHALL NOT offer a switch for operating-system notifications or for a last check before saving.

**Source**: S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#automation-tab`, ADR-0045, `design.md#d16--export-validates-and-writes-with-no-model-call`. In plain words: how often BookLoom interrupts, and what it does at the end of a run, is the person's choice. Notifications are not offered because BookLoom sends none. The last-check switch is gone because export no longer makes a model call. What the window opens at launch with Reopen the last project on is the `app-shell` capability's rule.

#### Scenario: The stop-and-ask choice is stored

- **WHEN** After every segment is chosen
- **THEN** the next run pauses after each decided segment

#### Scenario: No notification switch

- **WHEN** the Automation tab is opened
- **THEN** it holds no switch for operating-system notifications

#### Scenario: No last-check switch

- **WHEN** the Automation tab is opened
- **THEN** it holds no switch for running a last check before saving

#### Scenario: Reopen the last project is a stored switch

- **WHEN** Reopen the last project is switched off and the application is closed and opened again
- **THEN** the Automation tab shows Reopen the last project switched off

#### Scenario: An explicit launch value is named on Automation

- **WHEN** BookLoom was launched with `BOOKLOOM_REVIEW_MODE=unattended` and the Automation tab is opened
- **THEN** a note under When to stop and ask you says `BOOKLOOM_REVIEW_MODE` is set for this launch, so it decides this, and that the choice made here is kept for launches without it

#### Scenario: A new book takes the default quality

- **WHEN** Max quality is chosen as the default quality for new books and a book is opened that has no stored project, or whose stored project has no target language yet (it is created again from the book)
- **THEN** that project's Book Brief holds Max quality, and a stored project that already has a target language keeps the quality its own Book Brief holds

### Requirement: Apply the Appearance, Automation and Storage tabs at once and say when

The Appearance, Automation and Storage & logs tabs SHALL apply a change at once and SHALL say when it takes effect: "Changes apply at once", "to the segments not translated yet", or "after BookLoom restarts".

**Source**: theme H, option H1 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`. In plain words: a switch takes effect when flipped, and the screen is honest about the cases where the next launch is what changes. The Generation tab no longer holds a setting to flip, so it is not in this list.

#### Scenario: A theme change applies at once

- **WHEN** the dark theme is chosen on Appearance
- **THEN** the window turns dark immediately and the tab says "Changes apply at once"

#### Scenario: A language change waits for a restart

- **WHEN** Ukrainian is chosen as the language on Appearance
- **THEN** the tab says the change applies "after BookLoom restarts"

## MODIFIED Requirements

### Requirement: Show a check's details in a test dialog

WHEN the person opens a check's details, the settings screen SHALL show a dialog with a verdict banner, the request line (the route actually used) with Copy as curl (key hidden), three columns Body, a table of Status, Time, First token, Speed, Tokens and Structured, and Response, and a footer that switches between the tests that have run and closes; a copy is confirmed in the footer, never a toast. A figure the engine does not report SHALL read –, never 0.

**Source**: S2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#dialog--test-connection--models--inference-s2`, `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: when a test fails, a person or a helper can see exactly what was asked and what came back, and can reproduce it in a terminal without the secret being exposed. A speed the server never reported is shown as missing, not as zero, so it cannot be mistaken for a measurement.

#### Scenario: The dialog shows the request and the answer

- **WHEN** the inference check of `Ollama` with `gemma4:e4b` is opened
- **THEN** the banner reads "The model replied correctly. Translation can start."
- **AND** the request line shows `POST http://127.0.0.1:11434/api/chat` and the three columns are filled

#### Scenario: The request line shows the route the engine uses

- **WHEN** the inference check of `LM Studio` with `google/gemma-4-e4b` is opened
- **THEN** the request line shows `POST http://127.0.0.1:1234/api/v0/chat/completions`

#### Scenario: A figure the engine does not report is a dash

- **WHEN** the inference check of an Ollama provider reached through `/v1` is opened and that route reports no time to first token
- **THEN** the First token cell reads `–` and not `0`

#### Scenario: Prompt reading says it is uncached on MLX

- **WHEN** the inference check of `gemma4:e4b-mlx` on Ollama reports 600 prompt tokens of which 200 were cached
- **THEN** the table shows prompt reading labelled "uncached" with the cached share, 33 %, beside it

#### Scenario: Copy as curl hides the key

- **WHEN** Copy as curl is used for a provider that signs in with a bearer token from `HOME_API_KEY`
- **THEN** the copied command carries `Authorization: Bearer ••••` and no key value

#### Scenario: Copying says so in the dialog's footer

- **WHEN** Copy as curl is used in the test dialog
- **THEN** the footer reads "Copied as curl — the key is hidden." and no toast is shown

#### Scenario: A check result opens the test dialog

- **WHEN** the check result `The model replied in 1.2 s` is activated
- **THEN** the test dialog opens on the inference check

### Requirement: Set a model's context window and answer length

The Models tab SHALL offer for each model a Use context window number (filled in from what is loaded) from 512 to 1,048,576 tokens, which is the one size every call to that model uses and is read-only where BookLoom cannot set the window on that engine, a Limit answer length switch no larger than the window, and under Advanced the field that carries the answer length, `max_completion_tokens` or `max_tokens`, which SHALL NOT apply to Ollama.

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: a person can widen the window a model reads or cap how long it may answer, and for OpenAI-style servers choose the field name the server expects. Ollama has no such choice, so the field is not offered for it. Where the server owns the window, the number is shown but cannot be edited, so it never pretends to be a setting.

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

### Requirement: List the settings a model refused and let the person send them again

The Models tab SHALL list under "Settings this model refused" each setting the model refused, its scope (the whole model, or one call kind or schema), when it was refused and what BookLoom did instead, with Send again on each row that acts on that scope only, and SHALL say "Nothing refused" when the list is empty.

**Source**: S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/mockups/README.md#refused-settings-s6`, `design.md#d20--model-profiles-resolution-reasoning-sampling-message-shaping`. In plain words: BookLoom learns by itself that a model rejects a setting, sometimes for every call and sometimes for one kind of call, and the person can see what it learned and undo exactly that part if the model was updated.

#### Scenario: A refusal is listed

- **WHEN** `gpt-oss:20b` refused `temperature` on 2026-10-20 and BookLoom stopped sending it
- **THEN** the list shows a row for `temperature` with the scope "whole model", that date and the action taken

#### Scenario: A refusal of one kind of call is scoped

- **WHEN** `qwen3.5-9b` refused a schema for Name reading on 2026-10-21 and BookLoom used plain JSON instead
- **THEN** the list shows a row for the schema with the scope "Name reading", that date and the action taken

#### Scenario: Send again forgets the refusal

- **WHEN** Send again is chosen on the `temperature` row
- **THEN** the row disappears and the next request to that model carries the temperature again

#### Scenario: Send again on a scoped row touches only that scope

- **WHEN** Send again is chosen on the Name reading schema row while the Translate schema row is also listed
- **THEN** the Name reading row disappears, the next Name reading call sends a schema again and the Translate row stays

#### Scenario: A folded system message is listed

- **WHEN** `gemma3:12b` refused the system role and BookLoom put the system text into the first user message
- **THEN** the list shows a row for the system message with the date and the action taken

#### Scenario: An empty list says so

- **WHEN** a model has refused nothing
- **THEN** the section reads "Nothing refused"

#### Scenario: Send again acts at once and leaves unsaved typing

- **WHEN** the temperature choice of `gpt-oss:20b` is changed to My value and Send again is chosen on its `temperature` row
- **THEN** the stored refusal is removed at once and the unsaved temperature choice stays in the form

### Requirement: Show all six settings areas, every one enabled

The settings screen SHALL carry a subtitle saying that books stay private when a local model is used and that BookLoom sends the text only to the AI model chosen here, and SHALL NOT say the text always stays on this computer; it SHALL present six areas — Providers, Models, Generation, Appearance, Automation, and Storage & logs — as a row of tabs whose selected tab is marked by an underline, every one enabled, with no accent row on Appearance.

**Source**: decision 6, S4, S5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/specification/01_Product/07_SETTINGS.md#settings-tabs`, the owner's decision that privacy is stated as "private when used with local models". In plain words: with the models, generation, automation and storage tabs built, nothing is greyed any more. The subtitle must not promise more than BookLoom can keep: with a remote provider the book's text leaves the computer, so only the local-model case is called private.

#### Scenario: All six areas are listed

- **WHEN** the settings screen is opened
- **THEN** tabs for Providers, Models, Generation, Appearance, Automation, and Storage & logs are all present and enabled
- **AND** the subtitle reads `Your books stay private when you use a local model. BookLoom sends your text only to the AI model you choose here.`

#### Scenario: The subtitle never says always local

- **WHEN** the settings screen is opened in English or in Ukrainian
- **THEN** the subtitle does not claim that the books always stay on this computer

#### Scenario: The selected area is underlined

- **WHEN** the settings screen is opened on the Providers tab
- **THEN** the Providers tab is marked by an underline and no other tab is

#### Scenario: Every area opens

- **WHEN** the Generation tab is activated
- **THEN** it opens and shows its own settings

#### Scenario: The appearance area has no accent

- **WHEN** the Appearance area is shown
- **THEN** it presents the theme choice and no accent row

### Requirement: Store the speed the inference test measured on the model

WHEN Test inference passes for a model of a stored provider, the application SHALL store the measured speed in seconds per segment on that model's settings, with where it was measured (the later measurement winning), and the estimate before Start SHALL say where. WHEN a translation ends it SHALL store its seconds per drafted segment, pauses left out, and nothing if none was drafted; a translation or names pass SHALL also store seconds per call of each kind it made.

**Source**: S2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `openspec/changes/archive/2026-10-27-finalize-ui-and-persistence/design.md` "D13", `design.md#d23--timings-interactive-callers-and-the-model-name-filter`. In plain words: an estimate needs a measurement behind it. The test the person just ran is one, and a finished run measures each kind of call (draft, repair, proofread, names), which is more exact than one figure per segment. Storing them on the model keeps them after a restart and keeps two models apart.

#### Scenario: The measured speed is stored and named

- **WHEN** Test inference for `gemma4:e4b` passes and measures 3.0 seconds per segment, and the application is restarted
- **THEN** the settings of `gemma4:e4b` hold 3.0 seconds per segment, measured by the inference test, and the estimate before Start says it comes from the inference test

#### Scenario: A translation's own speed replaces the test's

- **WHEN** a translation of the book with `gemma4:e4b` ends after Test inference had stored a speed
- **THEN** the stored speed is the translation's and the estimate before Start says it comes from an earlier translation

#### Scenario: A translation stores its seconds per kind of call

- **WHEN** a translation with `gemma4:e4b` ends having made 300 draft calls in 900 s and 20 proofread calls in 120 s
- **THEN** the settings of `gemma4:e4b` hold 3.0 seconds per draft call and 6.0 seconds per proofread call, and the estimate before Start says they were measured on this model

#### Scenario: A names pass stores its own kinds

- **WHEN** a names pass with `gemma4:e4b` ends having made 40 name-reading calls in 200 s
- **THEN** the settings of `gemma4:e4b` hold 5.0 seconds per name-reading call

#### Scenario: A kind that made no call stores nothing

- **WHEN** a translation at Fast ends without a proofread call and `gemma4:e4b` held 6.0 seconds per proofread call before
- **THEN** the stored 6.0 seconds per proofread call is unchanged

#### Scenario: A run that drafted nothing stores nothing

- **WHEN** a translation is cancelled or refused before any segment was drafted
- **THEN** the speed stored on the model is unchanged

## REMOVED Requirements

### Requirement: Show how quality, thinking and sampling are set on Generation

**Reason**: Thinking and Sampling are chosen per model on the Models tab, and the quality table gains new rows and a new meaning, so a Generation tab that offered Thinking before answering and Sampling no longer exists.
**Migration**: "Show what each quality choice does on Generation" and "Show the time limits on Generation as fixed values" replace the table and the time limits; "Show what a model offers for thinking and let the person override it" and "Show BookLoom's sampling choice for a model and let the person override it" replace Thinking and Sampling.

### Requirement: Report what the program says about a model

**Reason**: The Models tab now reports the window engine by engine and structured answers per kind of call, so the scenario that the loaded figure is read from Ollama only, and the one that structured answers come from the session's last inference test, no longer hold.
**Migration**: "Report what the program says about a model, engine by engine" replaces it.

### Requirement: Choose when to stop and ask on Automation

**Reason**: The last-check default switch leaves Automation because export no longer makes a model call, so the scenario about the unwritten last-check default no longer holds.
**Migration**: "Choose when to stop and ask, and the defaults, on Automation" replaces it.

### Requirement: Apply the other tabs at once and say when

**Reason**: The Generation tab no longer holds a setting to apply, so the scenario that a generation change reaches only later segments no longer holds.
**Migration**: "Apply the Appearance, Automation and Storage tabs at once and say when" replaces it.
