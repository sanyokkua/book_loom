# Spec Delta

## ADDED Requirements

### Requirement: Send each call kind at its own temperature with reasoning off

The system SHALL send each model call at the temperature of its kind: draft `0.2`, and the draft a review retry makes
`0.1` when the person asks for a lower temperature; judge `0.1`; directed fix `0.2`; reflect and improve `0.35`; polish,
pre-scan, summary and revision `0.2`. Every call SHALL carry reasoning control set to disabled.

**Source:** FR-INFER-03 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/01_Product/05_TRANSLATION_ALGORITHM.md#generation-parameters`,
`docs/specification/02_Architecture/05_PIPELINE_ENGINE.md#generation-parameters`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#reflect-rewrite`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#dialog-retry-with-note`.
In plain words: every call stays low and near-deterministic for fidelity; a scorer is the steadiest, so the same draft
gets the same verdict, and only the rewrite after a vague critique gets a little more room to escape a bad phrasing. A
person who retries a segment and asks for a lower temperature gets a steadier draft of it. Reasoning is turned off
because it only costs time and can leave the answer empty; how each server is told so is the `llm-provider`
capability's rule.

#### Scenario: A judge call is steadier than a draft

- **WHEN** a Balanced run drafts `ch07.xhtml:41` and then judges its chunk through the provider `lmstudio`
- **THEN** the draft request body has `"temperature":0.2` and the judge request body has `"temperature":0.1`

#### Scenario: A lower-temperature retry

- **WHEN** the person retries the segment `ch05.xhtml:11` in review and asks for a lower temperature
- **THEN** the retry's draft request carries the temperature `0.1`
- **AND** a retry of the same segment without that request carries `0.2`

#### Scenario: Improve gets more room

- **WHEN** an improve call is sent to the provider `ollama`
- **THEN** its body's `options` holds `"temperature":0.35` and the body has `"think":false`

### Requirement: Carry the provider's token usage on a reply

WHEN a provider reports how many tokens a call used, the system SHALL return with the reply its prompt tokens, its
completion tokens and its generation time, each only when reported. WHEN the provider reports none of them, the reply
SHALL carry no usage at all, never zeros.

**Source:** `docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`,
`docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md#screen-translating`.
In plain words: the Translating screen's tokens-per-second figure comes from what the server says it generated. A
missing figure must look missing, so the engine knows to estimate it instead of showing a speed of zero. Which field of
each server's reply holds which figure is the `llm-provider` capability's rule.

#### Scenario: Usage is carried when reported

- **WHEN** a provider reports 812 prompt tokens, 96 completion tokens and a generation time of 3.2 seconds
- **THEN** the reply carries 812, 96 and 3.2 seconds

#### Scenario: No usage is carried when none is reported

- **WHEN** a provider's reply carries no usage figures
- **THEN** the reply carries no usage, and neither count reads 0

## MODIFIED Requirements

### Requirement: The pseudo model answers with the last user message in capitals

WHEN the pseudo model receives messages, the system SHALL take the text inside the last user message's `<Text>…</Text>`
block, or the whole last user message when it has none, and SHALL upper-case it. WHEN the request carries no response
format, the reply SHALL be that text. WHEN it carries one, the reply SHALL be a well-formed answer of that format's
catalogue shape:

- draft, structural repair, placeholder repair, directed fix, improve, polish and revision: `{"target":"<text>"}`;
- judge: `{"score":1.0,"verdict":"accept","findings":[],"deferrals":[]}`;
- reflect: `{"issues":[]}`;
- pre-scan: the capitalised words of the text as candidate terms of type `other` and gender `unknown`;
- summary: an empty bilingual summary with no facts.

The pseudo model SHALL report a normal finish and no token usage. The conversion SHALL follow the same rules whatever the
machine's default locale. Every `⟦gN⟧` token and every character or entity reference SHALL be kept exactly as written.
The pseudo model SHALL never return an error and SHALL never open a network connection.

**Source:** FR-INFER-01 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`,
`docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`,
`docs/specification/01_Product/12_PROMPT_CATALOG.md#judge-quality-evaluation`, `#name-term-pre-scan`,
`#rolling-summary-update`.
In plain words: a book translated by the pseudo model is easy to recognise: it shows the original text in capitals,
with its formatting intact. Now that a run also judges, reflects, scans and summarises, the pseudo model answers each of
those in its expected shape, and its judge always accepts, so a whole run — review, memory and export included — works
without any server. Its answer to a draft or a repair is the source, or the rejected target, in capitals, which the
quality checks rightly call an untranslated echo: a segment whose source has at least 20 characters is flagged in every
review mode, and a short one such as `Yes, sir.` is accepted (the `quality-gates` capability). It reports no usage, which
exercises the path that estimates throughput.

#### Scenario: Letters are capitalised, tokens and references are kept

- **WHEN** the last user message is `Tom ⟦g0⟧ran⟦g1⟧ &amp; hid&nbsp;&#160;&#xA0;⟦g12⟧`
- **THEN** the reply is `TOM ⟦g0⟧RAN⟦g1⟧ &amp; HID&nbsp;&#160;&#xA0;⟦g12⟧` with a normal finish

#### Scenario: The machine's locale does not change the result

- **WHEN** the default locale is Turkish (`tr`) and the last user message is `title`
- **THEN** the reply is `TITLE`

#### Scenario: Only the last user message is answered

- **WHEN** the messages are system `Translate into uk`, user `one`, assistant `ONE` and user `two`
- **THEN** the reply is `TWO`

#### Scenario: An empty message gives an empty reply

- **WHEN** the last user message is empty
- **THEN** the reply is empty, with a normal finish

#### Scenario: A draft gets a target object

- **WHEN** a request with the draft response format has a last user message whose `<Text>` block holds
  `He opened the ⟦g0⟧old⟦g1⟧ door.`
- **THEN** the reply is `{"target":"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR."}` and carries no usage

#### Scenario: A directed fix gets the rejected target back in capitals

- **WHEN** a request with the directed-fix response format has a last user message that carries the source
  `He opened the ⟦g0⟧old⟦g1⟧ door.` outside its one `<Text>` block, and that block holds the rejected target
  `he opened the ⟦g0⟧old⟦g1⟧ door.`
- **THEN** the reply is `{"target":"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR."}`

#### Scenario: The judge always accepts

- **WHEN** a request with the judge response format is sent to the pseudo model
- **THEN** the reply is `{"score":1.0,"verdict":"accept","findings":[],"deferrals":[]}`

### Requirement: Carry a temperature and a response format per call

The system SHALL let a request carry, besides its messages, an optional temperature, an optional response format made of
a name and a JSON schema, an optional context size in tokens, and an optional number of output tokens it expects. A
request built from messages alone SHALL carry none of them. WHEN one is absent, the model SHALL leave it out of what it
sends; WHEN present, the model SHALL pass it on as its provider's dialect takes it. The pseudo model SHALL ignore the
temperature, the context size and the expected output.

**Source:** FR-INFER-03, FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#chat-contracts`, `#response-handling`, `#effective-context`,
`#service-owned-retry`, ADR-0033.
In plain words: the engine decides how creative the model may be, what shape it wants back, how much context it needs
and how long an answer it expects, without knowing which server answers. A setting that is not given is simply not sent,
so an old call site keeps working and a strict server never sees a `null`. Where each dialect puts the context size —
Ollama only, never an OpenAI-compatible server — and how the expected output sets the call's timeout are the
`llm-provider` capability's rules.

#### Scenario: A request built from messages alone carries no settings

- **WHEN** a request is built from the single user message `hello`
- **THEN** it has no temperature, no response format, no context size and no expected output

#### Scenario: A temperature and a format reach the server

- **WHEN** a request with temperature `0.2` and the response format `draft` with the schema
  `{"type":"object","properties":{"target":{"type":"string"}},"required":["target"]}` is sent to the provider
  `lmstudio`
- **THEN** the posted body has `"temperature":0.2` and a `response_format` whose `json_schema.schema` is that object

#### Scenario: The pseudo model ignores both

- **WHEN** the single user message `hello` with temperature `0.2`, context size `8192`, 400 expected output tokens and no
  response format is sent to the pseudo model, so the temperature, the context size and the expected output are all
  present
- **THEN** it replies `HELLO` with a normal finish

### Requirement: Request provider controls without trusting them as guarantees

IF an Ollama-native server explicitly rejects the reasoning control a request carries, THEN the system SHALL retry that
one request once with the control absent and record the downgrade without exposing the response body.

WHEN a request carries a response format and a provider explicitly rejects that format, THEN the system SHALL retry
the request once without the format. The downgrade is not a transport retry and SHALL not consume the retry-policy
attempt budget. Unrelated validation errors SHALL not trigger either downgrade.

A request sent again without a rejected control or format SHALL keep everything else it carried: its messages, its
temperature, the other control or format, its context size and the output it expects.

**Source:** FR-INFER-09 (`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#response-handling`, `#effective-context`, ADR-0013.
In plain words: a server that does not understand a control or a structured-output schema says so, and the request is
sent once more without it rather than failing; that second request must still be the same request in every other way,
or dropping the schema would quietly shrink Ollama's context back to its small default and cut the call's timeout.
Which calls disable reasoning is "Send each call kind at its own temperature with reasoning off"; how each server is told
is the `llm-provider` capability's rule.

#### Scenario: An unsupported native thinking control is omitted once

- **WHEN** Ollama rejects a request carrying `"think":false` and the context size `8192`
- **THEN** the next request has the same messages, temperature, response format and `"num_ctx":8192` but no `think`
  field

#### Scenario: A structured-output rejection downgrades once

- **WHEN** a provider explicitly rejects a JSON schema response format
- **THEN** the next request has no structured-output field and no retry delay

#### Scenario: A request retried without structured output still carries its context size

- **WHEN** Ollama explicitly rejects the JSON-schema format of a draft request that carries the temperature `0.2`, the
  context size `8192` and 400 expected output tokens, from a provider with the default request timeout
- **THEN** the next request has no `format` field and has `"options":{"temperature":0.2,"num_ctx":8192}`
- **AND** its attempt waits up to 200 seconds, as its expected output gives
