# Spec Delta

## ADDED Requirements

### Requirement: Answer a caller that asks which models a provider offers

The system SHALL let a caller ask, by provider id, for the models that provider offers, and SHALL answer with
the model ids discovery returns.

The answer SHALL be the outcome of the discovery this capability already contracts under "Discover the models a
provider offers" — the same two endpoints, the same reading of each dialect's listing shape, the same
empty-listing-is-success rule and the same failure classification. Asking through this route SHALL change no
discovery behaviour and SHALL add no obligation to it.

IF the provider id names no registered provider, THEN the system SHALL answer `ErrorCode.validation` and SHALL
send no request.

**Source:** FR-MODEL-01, FR-MODEL-02
(`docs/specification/01_Product/01_FUNCTIONAL_REQUIREMENTS.md#fr-model`),
`docs/specification/02_Architecture/04_LLM_INTEGRATION.md#model-discovery`,
`docs/specification/01_Product/07_SETTINGS.md#models-tab`.
In plain words: discovery is already built and already specified, and until now it has had exactly one caller —
the verification routine, which checks whether the chosen model is in the list and then throws the list away. A
screen that wants to offer that list has nothing to call. This adds only the way to ask, and the one behaviour
that is genuinely new because it is about the asking rather than the discovering: a provider id that names
nothing is refused before anything is sent, rather than producing a call to an endpoint assembled from a blank.
The existing discovery requirement's own note already anticipated this route — "a screen will fill a list from
this" — so the scenarios below prove it reaches that behaviour in both dialects rather than restating it.

#### Scenario: The route reaches Ollama-native discovery

- **GIVEN** a provider registered as `ollama` whose `/api/tags` answers
  `{"models":[{"name":"gemma3:12b","model":"gemma3:12b","size":1},{"name":"qwen3:8b","model":"qwen3:8b","size":2}]}`
- **WHEN** a caller asks `ollama` for its models
- **THEN** the answer carries `gemma3:12b` and `qwen3:8b` in that order

#### Scenario: The route reaches OpenAI-compatible discovery

- **GIVEN** a provider registered as `lmstudio` whose `/v1/models` answers
  `{"object":"list","data":[{"id":"google/gemma-3-12b","object":"model"}]}`
- **WHEN** a caller asks `lmstudio` for its models
- **THEN** the answer carries `google/gemma-3-12b`

#### Scenario: An unknown provider id is refused before anything is sent

- **WHEN** a caller asks `not-a-provider` for its models
- **THEN** the answer is `ErrorCode.validation`
- **AND** no HTTP request is made to any endpoint
