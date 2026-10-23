## ADDED Requirements

### Requirement: Add, edit and delete providers

The settings screen SHALL let a person add a provider, open a stored provider for editing by choosing its row (there is no separate Edit button), and delete one after confirmation with Delete…; deleting a provider SHALL also remove its per-model settings, and deleting the current provider SHALL leave no current AI model.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: a person whose AI program is not one of the two usual local ones can describe it themselves, removing one is never a single accidental click, and a deleted provider leaves nothing behind that could be mistaken for the model in use.

#### Scenario: A provider is added

- **WHEN** the person adds a provider named `Office LM` of kind OpenAI-compatible at `http://10.0.0.5:1234/v1` and saves
- **THEN** `Office LM` appears in the list with the host `10.0.0.5:1234`

#### Scenario: Choosing a row opens it for editing

- **WHEN** the person chooses the `Office LM` row
- **THEN** its form opens with the name `Office LM` and the address `http://10.0.0.5:1234/v1`

#### Scenario: Delete asks first

- **WHEN** the person chooses Delete… on `Office LM` and then cancels the confirmation
- **THEN** `Office LM` is still listed

#### Scenario: A confirmed delete removes it

- **WHEN** the person chooses Delete… on `Office LM` and confirms
- **THEN** `Office LM` is no longer listed

#### Scenario: Deleting the current provider

- **WHEN** Ollama is current with the model `gemma4:e4b` and Delete… on Ollama is confirmed
- **THEN** Ollama is no longer listed, no AI model is current, and the settings of `gemma4:e4b` are gone

#### Scenario: Deleting another provider keeps the current one

- **WHEN** Ollama is current with the model `gemma4:e4b` and Delete… on `Office LM` is confirmed
- **THEN** `Office LM` is no longer listed, Ollama is still current with `gemma4:e4b`, and the settings of `gemma4:e4b` are kept

### Requirement: Describe a provider's connection in a form

The provider form SHALL ask for a name, a kind (Ollama or OpenAI-compatible), an address, a connect timeout and a request timeout, and WHEN the address of an OpenAI-compatible provider does not end in `/v1`, the form SHALL warn that the address usually ends in `/v1`.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: the form asks only for what the person can know, and the most common mistake with OpenAI-style servers, leaving off `/v1`, is flagged before a test fails mysteriously.

#### Scenario: A missing /v1 is flagged

- **WHEN** the kind is OpenAI-compatible and the address is `http://localhost:1234`
- **THEN** the form warns that the address usually ends in `/v1`

#### Scenario: An Ollama address is not flagged

- **WHEN** the kind is Ollama and the address is `http://localhost:11434`
- **THEN** no `/v1` warning is shown

### Requirement: Choose how a provider signs in without entering a key

The provider form SHALL offer the sign-in choices None, Bearer token and API-key header (with the header's name), SHALL take the key from the environment variable whose name the person enters, and SHALL show that variable as "set" or "not set" without showing its value.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67` ("Keys are never saved, only environment-variable names"), ADR-0043. In plain words: the form never asks the person to type a secret. It asks where the secret lives and tells them whether BookLoom can find it.

#### Scenario: A variable that exists is shown as set

- **WHEN** Bearer token is chosen with the variable `HOME_API_KEY` and that variable has a value
- **THEN** the form shows `HOME_API_KEY` as "set" and does not show the value

#### Scenario: A variable that is missing is shown as not set

- **WHEN** API-key header `X-Api-Key` is chosen with the variable `OFFICE_KEY` and no such variable exists
- **THEN** the form shows `OFFICE_KEY` as "not set"

### Requirement: Let the endpoints be overridden where the server needs it

The provider form SHALL offer a Models endpoint override and an Inference endpoint override, and WHILE the kind is Ollama SHALL show the Inference endpoint override as disabled with the reason that Ollama's chat address is fixed.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: some OpenAI-style servers put their routes under unusual paths, so both can be changed. Ollama's route is fixed, and a greyed field that says why is clearer than a field that silently does nothing.

#### Scenario: An override is accepted for an OpenAI-compatible server

- **WHEN** the kind is OpenAI-compatible and the Models endpoint override is `/catalog/models`
- **THEN** the form accepts it and the Inference endpoint override is editable

#### Scenario: The inference override is disabled for Ollama

- **WHEN** the kind is changed to Ollama
- **THEN** the Inference endpoint override is disabled and a note states the reason

### Requirement: Take a provider's custom headers and models

The provider form SHALL let the person add custom headers as name and value rows, where a value MAY be `${env:NAME}`, and SHALL let the person add custom model identifiers shown as chips.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: a company gateway may need an extra header, and some servers do not list the models they serve, so both can be added by hand and a secret header value can point at an environment variable.

#### Scenario: A header with an environment reference

- **WHEN** the header `X-Team-Key` is added with the value `${env:TEAM_KEY}`
- **THEN** the row is kept with that text, not with a resolved value

#### Scenario: A custom model chip

- **WHEN** the custom model `acme-translate-1` is added
- **THEN** it appears as a chip and is offered among the models of that provider

### Requirement: Use one provider and ask first while the open project has stored decisions

WHEN the person chooses Use this one on a stored provider, the settings screen SHALL make it the current provider; and WHILE the open project has stored segment decisions, SHALL first ask "Use LM Studio for the rest of this book?" (naming the chosen provider) and change nothing unless the person confirms.

**Source**: S3, decision 7 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#changes-while-translating-i`, `docs/specification/mockups/README.md#providers`. In plain words: switching the AI model in the middle of a book changes the voice of the rest, whether or not a run is going at that moment, so the person is asked once and told that segments already done keep their translation.

#### Scenario: Use this one with no project open

- **WHEN** no project is open and Use this one is chosen on `LM Studio`
- **THEN** `LM Studio` carries the badge `current` at once and the previous provider shows `idle`

#### Scenario: Use this one in the middle of a book asks first

- **WHEN** the open project `Earth Gravity · EN → UK` has 74 of 142 segments decided, no run is going, and Use this one is chosen on `LM Studio`
- **THEN** the question "Use LM Studio for the rest of this book?" is shown with "Using the same AI model keeps the style even. Segments already done keep their translation."
- **AND** the current provider changes only when the person confirms

#### Scenario: Keep changes nothing

- **WHEN** the question "Use LM Studio for the rest of this book?" is answered with Keep Ollama
- **THEN** `Ollama` stays current, the badges are as they were and nothing is stored

### Requirement: Show a provider's connection details and open them when they matter

The provider detail SHALL hold the connection form in a fold called Connection details, and the fold SHALL open by itself when the provider has a problem, when the provider is new, or when Show technical details is on.

**Source**: theme H (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`. In plain words: someone whose AI model works never needs to see the form, and someone who needs it finds it already open.

#### Scenario: A healthy provider keeps the fold closed

- **WHEN** the current provider `Ollama` passed its last check
- **THEN** the Connection details fold is closed

#### Scenario: A failed check opens the fold

- **WHEN** the last check of `Ollama` reported "Ollama does not answer"
- **THEN** the Connection details fold is open

#### Scenario: A new provider opens the fold

- **WHEN** the person starts adding a provider
- **THEN** the Connection details fold is open

### Requirement: Show a check's details in a test dialog

WHEN the person opens the details of a check, the settings screen SHALL show a dialog with a verdict banner, the request line with Copy as curl (the key hidden), three columns Body, a table of Status, Time, First token, Speed, Tokens and Structured, and Response, and a footer that switches between the tests that have run and closes; a copy is confirmed in the dialog's footer, never in a toast.

**Source**: S2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#dialog--test-connection--models--inference-s2`. In plain words: when a test fails, a person or a helper can see exactly what was asked and what came back, and can reproduce it in a terminal without the secret being exposed.

#### Scenario: The dialog shows the request and the answer

- **WHEN** the inference check of `Ollama` with `gemma4:e4b` is opened
- **THEN** the banner reads "The model replied correctly. Translation can start."
- **AND** the request line shows `POST http://127.0.0.1:11434/api/chat` and the three columns are filled

#### Scenario: Copy as curl hides the key

- **WHEN** Copy as curl is used for a provider that signs in with a bearer token from `HOME_API_KEY`
- **THEN** the copied command carries `Authorization: Bearer ••••` and no key value

#### Scenario: Copying says so in the dialog's footer

- **WHEN** Copy as curl is used in the test dialog
- **THEN** the footer reads "Copied as curl — the key is hidden." and no toast is shown

#### Scenario: A check result opens the test dialog

- **WHEN** the check result `The model replied in 1.2 s` is activated
- **THEN** the test dialog opens on the inference check

### Requirement: Describe each model on the Models tab and keep its own settings

The Models tab SHALL list the models across all providers, and SHALL keep for the chosen model its own optional settings, with the explanation "Each model keeps its own settings. All are optional — off means BookLoom picks the value per task."

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`. In plain words: one model may refuse a setting that another accepts, so settings belong to a model, and leaving them off always means the safe default.

#### Scenario: Two models keep different settings

- **WHEN** `gemma4:e4b` is given the context window 16384 and `gpt-oss:20b` is left unchanged
- **THEN** the Models tab shows 16384 for `gemma4:e4b` and no value for `gpt-oss:20b`

### Requirement: Report what the program says about a model

The Models tab SHALL show, as found out from the program, how much the model can read at once, how much is loaded now where the program says so, and whether it gives structured answers.

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`. In plain words: before changing a number, a person sees what the model itself reports about its limits.

#### Scenario: Reported limits are shown

- **WHEN** `gemma4:e4b` reports a context length of 131072 and 16384 loaded
- **THEN** the Models tab shows both figures under "Found out from the program"

#### Scenario: The loaded figure is read from Ollama only

- **WHEN** the model belongs to an OpenAI-compatible provider
- **THEN** the Models tab shows only how much it can read at once and no loaded figure, and no `/api/ps` request is sent

#### Scenario: Structured answers come from this session's last inference test

- **WHEN** no inference test of `gemma4:e4b` has run since the application started
- **THEN** the Models tab reads "structured answers: not checked yet" and names the Providers tab test that checks it

### Requirement: Choose how the temperature is sent for a model

The Models tab SHALL offer for each model Let BookLoom choose (the default), My value (one value for every request) and Don't send (the temperature is left out entirely).

**Source**: S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`. In plain words: most models want the tuned values, some people want one fixed value, and a few models refuse the setting altogether, so the choice is explicit and Don't send exists for them.

#### Scenario: My value sets one temperature

- **WHEN** My value is chosen for `gemma4:e4b` with `0.5`
- **THEN** the Models tab shows `0.5` as the value that every request to that model will carry

#### Scenario: Don't send is selectable

- **WHEN** Don't send is chosen for `gpt-oss:20b`
- **THEN** the Models tab shows that no temperature will be sent to that model

### Requirement: Set a model's context window and answer length

The Models tab SHALL offer for each model a Use context window number (filled in from what is loaded), a Limit answer length switch, and under Advanced the field that carries the answer length, `max_completion_tokens` or `max_tokens`, which SHALL NOT apply to Ollama.

**Source**: decision 6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#models-decision-6`. In plain words: a person can widen the window a model reads or cap how long it may answer, and for OpenAI-style servers choose the field name the server expects. Ollama has no such choice, so the field is not offered for it.

#### Scenario: The context window is filled in from the program

- **WHEN** `gemma4:e4b` has 16384 tokens loaded and the tab is opened
- **THEN** Use context window shows `16384`

#### Scenario: The field choice is absent for Ollama

- **WHEN** the model belongs to an Ollama provider
- **THEN** the Advanced answer-length field choice is not offered

#### Scenario: The field choice is offered for OpenAI-compatible servers

- **WHEN** the model belongs to an OpenAI-compatible provider
- **THEN** Advanced offers `max_completion_tokens` and `max_tokens`

#### Scenario: The loaded figure is a suggestion while the window is off

- **WHEN** nothing is stored for `gpt-oss:20b`, 16384 tokens are loaded and Use context window is off
- **THEN** the number field shows `16384` disabled, the draft is unchanged and the footer shows no unsaved mark

### Requirement: List the settings a model refused and let the person send them again

The Models tab SHALL list under "Settings this model refused" each setting the model refused, when it was refused and what BookLoom did instead, with Send again on each row, and SHALL say "Nothing refused" when the list is empty.

**Source**: S6 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), FR-INFER-09e (`docs/specification/01_Product/04_LLM_PROVIDERS_AND_MODELS.md#response-handling`), `docs/specification/mockups/README.md#refused-settings-s6`. In plain words: BookLoom learns by itself that a model rejects a setting, and the person can see what it learned and undo it if the model was updated.

#### Scenario: A refusal is listed

- **WHEN** `gpt-oss:20b` refused `temperature` on 2026-10-20 and BookLoom stopped sending it
- **THEN** the list shows a row for `temperature` with that date and the action taken

#### Scenario: Send again forgets the refusal

- **WHEN** Send again is chosen on the `temperature` row
- **THEN** the row disappears and the next request to that model carries the temperature again

#### Scenario: An empty list says so

- **WHEN** a model has refused nothing
- **THEN** the section reads "Nothing refused"

#### Scenario: Send again acts at once and leaves unsaved typing

- **WHEN** the temperature choice of `gpt-oss:20b` is changed to My value and Send again is chosen on its `temperature` row
- **THEN** the stored refusal is removed at once and the unsaved temperature choice stays in the form

### Requirement: Save Providers and Models through a pinned footer and guard leaving

The Providers and Models tabs SHALL change nothing until saved, SHALL show a pinned footer with "● Unsaved changes", Cancel and Save while they hold changes, and WHEN the person leaves with changes SHALL ask Keep editing, Discard or Save.

**Source**: theme H, option H1 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/specification/mockups/README.md#other-dialogs`. In plain words: a provider's address typed halfway must not become live, and moving to another tab must not silently lose or silently apply what the person typed.

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

- **WHEN** `gemma4:e4b` of `Ollama` has unsaved changes and `gpt-oss:20b` is chosen in the model list
- **THEN** the question "Save the settings of gemma4:e4b · Ollama?" is asked once with Keep editing, Discard and Save
- **AND** `gpt-oss:20b` opens only after Discard or Save

#### Scenario: Leaving Models asks

- **WHEN** the Models tab holds unsaved changes and the Providers tab is opened
- **THEN** the question is asked and the Providers tab opens only after Discard or Save

#### Scenario: Save stores the change

- **WHEN** Save is chosen
- **THEN** the stored timeout is 240 and the footer disappears

### Requirement: Apply the other tabs at once and say when

The Generation, Appearance, Automation and Storage & logs tabs SHALL apply a change at once and SHALL say when it takes effect: "Changes apply at once", "to the segments not translated yet", or "after BookLoom restarts".

**Source**: theme H, option H1 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`. In plain words: a switch takes effect when flipped, and the screen is honest about the cases where the running translation or the next launch is what changes.

#### Scenario: A theme change applies at once

- **WHEN** the dark theme is chosen on Appearance
- **THEN** the window turns dark immediately and the tab says "Changes apply at once"

#### Scenario: A generation change reaches only later segments

- **WHEN** Thinking before answering is changed during a run
- **THEN** the tab says the change applies "to the segments not translated yet"

### Requirement: Show how quality, thinking and sampling are set on Generation

The Generation tab SHALL show what each quality choice does as a read-only table, SHALL offer Thinking before answering as Off, Low or On, SHALL offer Sampling as Program defaults or Tuned by BookLoom, and SHALL show the time limits as fixed read-only values.

**Source**: S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#generation-tab`. In plain words: the person can see what Fast, Balanced and Max mean without being able to break them, and can change only the two knobs that the program really has.

#### Scenario: The quality table is read-only

- **WHEN** the Generation tab is opened
- **THEN** the quality table lists Fast, Balanced and Max and none of its cells can be edited

#### Scenario: Thinking is chosen

- **WHEN** Low is chosen for Thinking before answering
- **THEN** the stored choice is Low and it is read again after a restart

### Requirement: Choose the theme, the language and technical details on Appearance

The Appearance tab SHALL offer the theme, the language of the app with the note "Applies after BookLoom restarts." and a Restart now action, and a Show technical details switch, and SHALL NOT offer an accent row.

**Source**: S5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#appearance-tab`. In plain words: the language changes on the next launch because the whole window is built in one language, and Restart now saves the wait. The old fixed accent line is gone because nothing could change it.

#### Scenario: A language choice waits for a restart

- **WHEN** Ukrainian is chosen while the window is in English
- **THEN** the window stays in English, the note "Applies after BookLoom restarts." is shown and Restart now is offered

#### Scenario: Technical details are a switch

- **WHEN** Show technical details is switched on
- **THEN** technical details appear where the screens offer them and stay on after a restart

#### Scenario: There is no accent row

- **WHEN** the Appearance tab is opened
- **THEN** it has no accent row

### Requirement: Choose when to stop and ask on Automation

The Automation tab SHALL offer When to stop and ask you, a default quality, and switches to open the review desk when a translation finishes with segments to review, to run the last check before saving by default, to Keep the computer awake (on by default) and to Reopen the last project (on by default), and SHALL NOT offer a switch for operating-system notifications.

**Source**: S4 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#automation-tab`, ADR-0045. In plain words: how often BookLoom interrupts, and what it does at the end of a run, is the person's choice. Notifications are not offered because BookLoom sends none. What the window opens at launch with Reopen the last project on is the `app-shell` capability's rule.

#### Scenario: The stop-and-ask choice is stored

- **WHEN** After every segment is chosen
- **THEN** the next run pauses after each decided segment

#### Scenario: No notification switch

- **WHEN** the Automation tab is opened
- **THEN** it holds no switch for operating-system notifications

#### Scenario: Reopen the last project is a stored switch

- **WHEN** Reopen the last project is switched off and the application is closed and opened again
- **THEN** the Automation tab shows Reopen the last project switched off

### Requirement: Name the review modes with one set of words

The Automation tab's When to stop and ask you SHALL offer exactly these three choices, each standing for one review mode of the run and one launch value:

| Automation choice | Review mode of the run | Launch value |
|---|---|---|
| Never — translate unattended | Unattended | `unattended` |
| When something needs review | Assisted | `assisted` |
| After every segment | Manual | `manual` |

**Source**: `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#automation-tab`, ADR-0045. In plain words: the tab, the title bar and the environment variable `BOOKLOOM_REVIEW_MODE` name the same three modes, so a person and a script mean the same thing. The default is Never — translate unattended.

#### Scenario: The words map to the modes

- **WHEN** When something needs review is chosen and a run is started
- **THEN** the run's review mode is Assisted

#### Scenario: Nothing stored means unattended

- **WHEN** nothing is stored for When to stop and ask you, no launch value is set, and a run is started
- **THEN** the run's review mode is Unattended

### Requirement: Show storage and logs on one tab

The Storage & logs tab SHALL show where the projects are saved, how large they are, an Open folder action and Make a backup…, and SHALL show the log folder, that 10 launches are kept, a Detailed diagnostic log switch, Open log folder and Save a diagnostic bundle…, and SHALL show each path in short form with the full path as its tooltip.

**Source**: `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#storage-and-logs-tab`. In plain words: a person can find their saved work and the logs without hunting through the file system, and can make a backup or hand support a bundle. Long paths stay readable, and the whole path is one hover away.

#### Scenario: A long path is shortened

- **WHEN** the projects are saved in `~/Library/Application Support/BookLoom`
- **THEN** the tab shows a shortened form and the tooltip shows the full path

#### Scenario: Logs keep ten launches

- **WHEN** the Storage & logs tab is opened
- **THEN** it states that 10 launches are kept

### Requirement: Show all six settings areas, every one enabled

The settings screen SHALL carry the subtitle "Your books stay on this computer. BookLoom sends your text only to the AI model you choose here.", SHALL present six areas — Providers, Models, Generation, Appearance, Automation, and Storage & logs — as a row of tabs whose selected tab is marked by an underline, and SHALL have every one of them enabled, with no accent row on Appearance.

**Source**: decision 6, S4, S5 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/specification/01_Product/07_SETTINGS.md#settings-tabs`. In plain words: with the models, generation, automation and storage tabs built, nothing is greyed any more. The subtitle states the promise precisely: the books stay here, and the book's text goes only to the AI model the person chose (the first-launch look asks for model lists and sends no text).

#### Scenario: All six areas are listed

- **WHEN** the settings screen is opened
- **THEN** tabs for Providers, Models, Generation, Appearance, Automation, and Storage & logs are all present and enabled
- **AND** the subtitle reads `Your books stay on this computer. BookLoom sends your text only to the AI model you choose here.`

#### Scenario: The selected area is underlined

- **WHEN** the settings screen is opened on the Providers tab
- **THEN** the Providers tab is marked by an underline and no other tab is

#### Scenario: Every area opens

- **WHEN** the Generation tab is activated
- **THEN** it opens and shows its own settings

#### Scenario: The appearance area has no accent

- **WHEN** the Appearance area is shown
- **THEN** it presents the theme choice and no accent row

### Requirement: Apply the app language after a restart and the review mode at the next run start

The application SHALL apply a changed app language at the next launch and a changed When to stop and ask you at the next run start, leaving a running run in its mode; WHERE the review mode is set by the environment variable `BOOKLOOM_REVIEW_MODE` or the launch property `bookloom.review.mode`, that value SHALL win over the stored choice.

**Source**: `docs/specification/mockups/README.md#settings--s1s5-h1-decisions-67`, `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `openspec/changes/finalize-ui-and-persistence/design.md` "D12". In plain words: the language needs a restart because every screen is built in one language; the review mode belongs to a run, so a change steers the next run rather than surprising the one going. Scripts and evaluations that set the mode explicitly keep control of it.

#### Scenario: A changed review mode reaches the next run

- **WHEN** a run is going in Unattended, the person chooses After every segment on Automation, the run ends, and a new run is started
- **THEN** the running run keeps translating without pausing, and the new run pauses after each decided segment

#### Scenario: An explicit launch value wins

- **WHEN** BookLoom was launched with `BOOKLOOM_REVIEW_MODE=unattended`, After every segment is stored on Automation, and a run is started
- **THEN** the run translates unattended

#### Scenario: The language waits for the next launch

- **WHEN** Ukrainian is chosen on Appearance while the window is in English, and the application is restarted
- **THEN** the window stays in English until the restart and is in Ukrainian after it

### Requirement: Release the keep-awake hold at once when the switch is turned off

WHEN Keep the computer awake is turned off while the application holds the computer awake, the application SHALL release the hold at once and SHALL NOT take it again while the switch is off.

**Source**: `docs/specification/mockups/README.md#generation--appearance--automation--storage--logs`, `docs/specification/01_Product/07_SETTINGS.md#automation-tab`. In plain words: the switch applies at once like the other Automation switches, so a person who wants the laptop to sleep is not overruled by a run that is already going. When the computer is held awake is the `app-shell` capability's rule.

#### Scenario: Turning the switch off releases the hold

- **WHEN** a run is translating with the computer held awake and Keep the computer awake is switched off
- **THEN** the hold is released at once, the run goes on, and no hold is taken for the rest of the run

### Requirement: Store the speed the inference test measured on the model

WHEN Test inference passes for a model of a stored provider, the application SHALL store the measured speed in seconds per segment on that model's settings, together with where it was measured (the inference test or an earlier translation, the later measurement winning), and the estimate before Start SHALL say where it was measured.

**Source**: S2 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#g10--progress-and-time-left-count-all-the-work-pr`, `openspec/changes/finalize-ui-and-persistence/design.md` "D13". In plain words: an estimate needs a measurement behind it, and the test the person just ran is one; storing it on the model keeps it after a restart and keeps two models' speeds apart.

#### Scenario: The measured speed is stored and named

- **WHEN** Test inference for `gemma4:e4b` passes and measures 3.0 seconds per segment, and the application is restarted
- **THEN** the settings of `gemma4:e4b` hold 3.0 seconds per segment, measured by the inference test, and the estimate before Start says it comes from the inference test

#### Scenario: A translation's own speed replaces the test's

- **WHEN** a translation of the book with `gemma4:e4b` ends after Test inference had stored a speed
- **THEN** the stored speed is the translation's and the estimate before Start says it comes from an earlier translation


## MODIFIED Requirements

### Requirement: Check a provider in three stages and report each one

The application SHALL offer Test connection, Test models and Test inference on the provider being edited, each running on the form as it stands, saved or not, and each running its own stage and every stage before it.

**Source**: FR-PROV-06, FR-PROV-07 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), S2, S3 (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`. In plain words: a person can test a new provider before saving it, and the answer is a sentence they can act on, not a code. A server with no listing endpoint is a normal, working configuration, so an unreadable listing is a qualified pass, not a failure.

#### Scenario: The check is unavailable until a model is chosen

- **WHEN** the Ollama provider is being edited and no model has been chosen
- **THEN** Test connection is available and Test models and Test inference are present and unavailable
- **WHEN** `gemma3:12b` is then chosen
- **THEN** Test models and Test inference become available

#### Scenario: A passing connection shows its time

- **WHEN** Test connection is used and the server at `http://localhost:11434` answers in 41 ms
- **THEN** the result reads `Ollama answers (41 ms)`
- **AND** the models and inference results are not reported

#### Scenario: An unreachable server fails the first stage only

- **WHEN** Test inference is used and nothing is listening at `http://localhost:11434`
- **THEN** the connection result reports that Ollama does not answer
- **AND** the models and inference results are reported as not attempted, not as passed

#### Scenario: A reachable server with the chosen model passes all three

- **WHEN** `gemma3:12b` is chosen, Test inference is used, and the server answers in 41 ms, lists 7 models including `gemma3:12b`, and answers the short request in 1.2 s
- **THEN** the results read `Ollama answers (41 ms)`, `7 models found — the chosen one is there` and `The model replied in 1.2 s`

#### Scenario: A server with no model listing still passes

- **WHEN** Test inference is used and the provider answers but its listing cannot be read
- **THEN** the models result is reported as a qualified pass carrying a note
- **AND** the inference result is still attempted

#### Scenario: A check in flight reports itself

- **WHEN** Test models has been started and the verification has not yet answered
- **THEN** the screen reports the test as in progress and the three test actions are unavailable
- **AND** they become available again when the report arrives

#### Scenario: An unsaved provider can be tested

- **WHEN** a new provider `Office LM` at `http://10.0.0.5:1234/v1` has not been saved and Test connection is used
- **THEN** the server at that address is asked and the result is shown, and `Office LM` is still not stored

### Requirement: Choose a model from the list, or name one the list does not hold

The application SHALL offer the models the provider being edited reports, SHALL accept a model identifier typed by hand, and SHALL also offer the provider's custom models.

**Source**: FR-MODEL-01, FR-MODEL-02, FR-MODEL-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`), DD-38, `docs/specification/mockups/README.md#providers`. In plain words: some servers list what they hold and some do not, and a server that holds nothing is a valid state rather than a broken one. Typing the identifier has to stay possible in all three cases. The chosen model is discarded on a provider change because a model identifier means nothing on a different server.

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

### Requirement: Describe each provider in its row and its detail card

The Providers area SHALL list each provider as a row showing its name, its endpoint's host and port, and a badge "current" or "idle"; and SHALL show a summary card — "Your AI model: Ollama · gemma4:e4b — ready ✓" for the current provider, "This provider's model: …" for another — with its speed and model count, or that no listing has been made yet, and the actions Test and Change model.

**Source**: FR-PROV-01, FR-PROV-04 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-prov`), S3, theme H (`docs/proposals/2026-10-19-ui-finalization-and-persistence.md`), `docs/specification/mockups/README.md#providers`, `docs/specification/mockups/README.md#first-launch-and-the-ai-model-owner-decision-13`. In plain words: a person sees at a glance which AI model is in use and whether it works, without opening the form. The host alone tells `127.0.0.1:11434` from `127.0.0.1:1234`.

#### Scenario: The rows name their hosts and status

- **WHEN** the Providers area is shown with the Ollama and LM Studio providers the first-launch look stored, Ollama current
- **THEN** the Ollama row shows `127.0.0.1:11434` and the badge `current`
- **AND** the LM Studio row shows `127.0.0.1:1234` and the badge `idle`

#### Scenario: The detail card after a listing

- **WHEN** Ollama is current with the model `gemma4:e4b`, it answered in about 1 second and its last listing returned 7 models
- **THEN** the summary card reads `Your AI model: Ollama · gemma4:e4b — ready ✓` with `Answers in about 1 second · 7 models available`, and offers Test and Change model

#### Scenario: The detail card before any listing

- **WHEN** LM Studio is current and its models have not been listed
- **THEN** the summary card says that no listing has been made yet and still offers Test and Change model

#### Scenario: The card of a provider that is not current

- **WHEN** the LM Studio row, which is not current, is selected
- **THEN** the card begins `This provider's model:` and names its model

#### Scenario: Save of the current provider's Model makes it the current model

- **WHEN** Ollama is current, its Model field is changed from `gemma4:e4b` to `gemma4:26b-mlx`, and Save is chosen
- **THEN** the summary card reads `Your AI model: Ollama · gemma4:26b-mlx` and `gemma4:26b-mlx` is the current AI model

#### Scenario: A provider that is not current and has no typed model

- **WHEN** the LM Studio row, which is not current, is selected and no model has been typed for it
- **THEN** the card reads `This provider's model: LM Studio · no model chosen yet`

## REMOVED Requirements

### Requirement: Show every settings area, and enable only what works

**Reason**: Every settings area is built now, so none is shown as unavailable, and the accent row is gone; the old scenarios ("An unimplemented area does not open", "The appearance area shows a fixed accent") would describe a screen that no longer exists.

**Migration**: Replaced by "Show all six settings areas, every one enabled" in this capability.

### Requirement: Offer two local providers from first launch

**Reason**: The settings no longer present two built-in providers that cannot be changed. A first launch looks once for a local AI program and stores what it finds, and a person adds any other provider themselves.

**Migration**: Providers are stored and listed as the person's own (see "Add, edit and delete providers"); the first-launch look is specified in `llm-provider` ("Look once for a local AI program at first launch").

### Requirement: Keep nothing chosen here beyond the session

**Reason**: Providers, the chosen model, the theme, the language and the other settings are now stored and survive a restart (`local-storage`).

**Migration**: See `local-storage` ("Keep the application's work across a restart") and the Appearance and Automation requirements above.
