# Draft Prompt and Reply Parser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this single OpenSpec task with TDD.

**Goal:** Complete OpenSpec task 9.1: send each segment with the catalog draft prompt and strict JSON schema, then tolerate JSON, map, and plain-text replies without changing the existing accept/flag/stop decisions.

**Architecture:** Add a non-exported `ua.bookloom.pipeline.prompt` package. `TranslationJobImpl` creates a language-specific prompt builder and mapper-backed reply parser once per job; `SegmentTranslator` uses them to create a configured `ChatRequest` and passes parsed text into its existing decision logic.

**Tech Stack:** Java 25, Jackson `ObjectMapper`, Guice, JUnit 5, AssertJ.

**Spec:** `openspec/changes/add-real-llm-clients/specs/translation-pipeline/spec.md`; design D7/D10; `docs/specification/01_Product/12_PROMPT_CATALOG.md#draft-translation`.

## Global Constraints

- Keep `:pipeline` FX-free and add no HTTP or public API.
- Use the `LlmModule`-provided tolerant `ObjectMapper`; preserve the existing model-error, finish-reason, whitespace, placeholder-unmask, and terminal decision table.
- Log prompt-builder inputs and parser-shape choices at DEBUG; rendered prompts and translations only at TRACE.
- Complete and tick only OpenSpec task 9.1; task 13.2 owns the full-project gate.

## Review Focus

- Quoted and multiline masked text must form valid JSON in the user message.
- A single wrong reply id is acceptable only for the one segment being sent.
- A JSON object with no usable target must flag `emptyCompletion`, never fall back to JSON text.
- Plain text must still be whitespace-restored and placeholder-validated.
- Provider failures and non-STOP finishes must retain their current decisions.

### Task 1: Draft prompt, reply parser, and translator wiring

**Files:**
- Create: `modules/pipeline/src/main/java/ua/bookloom/pipeline/prompt/{package-info,DraftPromptBuilder,DraftSchema,DraftReplyParser}.java`
- Modify: `modules/pipeline/src/main/java/ua/bookloom/pipeline/{TranslationEngineImpl,TranslationJobImpl,SegmentTranslator}.java`
- Test: pipeline prompt, translator, diagnostics, and end-to-end tests.

**Interfaces:**
- Produces `DraftPromptBuilder(@Nullable String sourceLanguage, String targetLanguage)`, `messagesFor(Segment)`, `DraftSchema.SCHEMA`, and `DraftReplyParser(ObjectMapper).translationFor(String, String)`.
- Consumes the existing nullable `ChatRequest` temperature and response-format components.

- [ ] Write failing prompt-builder and parameterized reply-parser tests for all documented response shapes and JSON escaping; run them and observe the missing-type failure.
- [ ] Add the `@NullMarked` prompt package. Render the D7 catalog subset with five `(none)` slots, exact schema, temperature `0.2`, and JSON-safe source id/text.
- [ ] Wire the injected mapper through engine and job to a builder/parser pair; create a two-message `ChatRequest` with `ResponseFormat("draft_translation", DraftSchema.SCHEMA)` and parse a successful reply before the unchanged decision table.
- [ ] Update translator, diagnostics, and end-to-end tests to use JSON envelopes while retaining a map and a plain-text acceptance test; observe each new behavior RED then GREEN.
- [ ] Run `./gradlew :pipeline:build`, mark OpenSpec 9.1 complete, and commit the task.
