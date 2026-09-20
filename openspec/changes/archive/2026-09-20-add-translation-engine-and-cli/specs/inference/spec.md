## Purpose

How the translation engine gets text from a language model without knowing which provider or model answers. Also
covers the deterministic pseudo model that stands in for a real model until LLM clients exist.

## ADDED Requirements

### Requirement: Get a chat model by provider id and model id

WHEN a caller asks for a chat model with a provider id and a model id, the system SHALL return a model that answers
an ordered list of system, user and assistant messages. The answer SHALL be reply text plus a finish reason: normal,
cut off by length, or other. Whoever then uses the model never names the provider or the model.

IF the provider id is unknown or the model id is blank, THEN the system SHALL return `ErrorCode.validation` and no
model.

**Source:** FR-INFER-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`), FR-PROV-03 (`#fr-prov`),
`02_Architecture/04_LLM_INTEGRATION.md#provider-architecture`, `#chat-contracts`.
In plain words: the caller that starts a translation picks the provider and the model once, and the translation engine
only talks to the model it is handed. Real providers will plug in behind the same call. The only provider in this
change is the built-in `pseudo`.

#### Scenario: The pseudo provider gives a working model

- **WHEN** a chat model is requested for provider `pseudo` and model `uppercase`
- **THEN** a model is returned
- **AND** when sent the single user message `hello`, it replies `HELLO` with a normal finish

#### Scenario: An unknown provider is refused

- **WHEN** a chat model is requested for provider `ollama` and model `qwen3:8b`
- **THEN** the result is `ErrorCode.validation` and no model is returned

#### Scenario: A blank model id is refused

- **WHEN** a chat model is requested for provider `pseudo` with an empty model id
- **THEN** the result is `ErrorCode.validation`

### Requirement: The pseudo model answers with the last user message in capitals

WHEN the pseudo model receives messages, the system SHALL reply with the content of the last user message in upper
case, and SHALL report a normal finish. The conversion SHALL follow the same rules whatever the machine's default
locale. Every `⟦gN⟧` token and every character or entity reference SHALL be kept exactly as written. The pseudo model
SHALL never return an error and SHALL never open a network connection.

**Source:** FR-INFER-01 (`01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-infer`),
`03_NonFunctional/03_PRIVACY_AND_OFFLINE.md#offline-invariant`, `02_Architecture/03_DOCUMENT_MODEL.md#unmask-and-validate`.
In plain words: a book translated by the pseudo model is easy to recognise. It shows the original text in capitals,
with its formatting intact, and every real step around the model still runs. Tokens and references must survive
untouched. Otherwise restoring the markup would fail for a reason no real translation causes.

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
