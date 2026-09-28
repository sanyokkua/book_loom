# Re-plan notes — after group 8 (checkpoint, 2026-09-28)

Read this before re-planning groups 9–15, fold each entry into the task it names, then delete this file. Each entry says
what group 8 changed or left out, and what the receiving task's text must now say. The task text below was already
edited where marked **(edited)**; the rest is for the re-plan to decide.

## Moved out of group 8 (owner rule: postpone what nothing uses yet, into the task that first uses it)

- **9.3 — glossary check reads the restored target (edited).** From 9.3 on a locked term is a token that
  `DisplayText` drops, so `GlossaryCheck` searching the target display text would fail every segment holding a locked
  name. 9.3 adds `SoftCheckInput.targetWithRenderings` and points `GlossaryCheck` at it.
- **10.2 — repair of a segment drafted in pieces (edited).** 8.6 repairs it like any other segment. 10.2 re-runs the
  piece path with the round's findings as each piece's extra instruction, adds the optional `[Extra instruction]` block
  to the DRAFT user template (10.6 reuses it), and records on `DraftOutcome.Drafted` that it was drafted in pieces.
- **10.3 — `DraftOutcome.Reused`, `AcceptanceRule.acceptsReuse`, a reuse kept out of the judge (edited).** 8.6's
  sealed `DraftOutcome` has two records; 10.3 adds the third with its tests.
- **8.4 — `DirectedFix` takes no note (edited).** No caller would pass one: a review retry is a draft (design D9).

## Shapes group 8 fixed that later task text does not know yet

- `QaFinding(kind, severity, note, raisedBy)` — `raisedBy` is `script`, `echo`, `repetition`, `length`, `glossary`,
  `refusal`, `placeholder`, `locked-term`, `kept-run` or `judge`. Anything that builds or stores a finding (9.3, 10.2,
  10.6, the review panel 13.3, persistence) passes it. The review panel can badge or filter by it.
- `heal.SegmentOutcome` carries `@Nullable AppError flagReason` for a segment flagged at once. `api.project.SegmentRecord`
  has no field for it — 10.2 (which stores decisions) must decide where the reason lives (a field, or a finding).
- Self-heal calls answer `heal.RepairReply` — `Rewritten`, `Malformed` (round fails), `FlagNow` (flag at once).
- `PromptName` carries its response schema; `ChatRequests.build` sends it. New prompt names (9.2 pre-scan, 9.5
  summary, 10.7 revision) each need a schema constant, and must be added to `SegmentTranslator.messagesFor`'s grouped
  "not a draft-step call" case.
- `prompt.CallFrame` (languages, style sheet, foreign-passage policy) and `prompt.ModelCalls` are the frame and seam
  every non-draft call uses; 10.5's call events hook `ModelCalls`.
- `ScriptedChatModel` (test) is public; `WireMockProvider` is still package-private in the root test package.
- A judge call answered `emptyCompletion` or `contextWindow` reads as an unreadable verdict; any other model error ends
  the step — 10.4's failure routing must include judge and self-heal calls.
- Reflect, improve and polish omit the catalogue's optional preceding-target text: nothing passes preceding targets to
  self-heal yet. 9.6/10.2 decide whether they should.
- The judge's finding type is kept as the model wrote it (the catalogue's `tag` = the checks' `markup`); the review
  panel's main-finding badge (design D8) must map `tag` like `markup`.

## Names the later task text gets wrong

- τ is `ReviewMode.threshold()`; there is no `tau()`.
- A finding's type is `QaFinding.kind`.
- `SegmentStatus` is in `ua.bookloom.api.document`, not `api.project`.
- `DocumentPort.unmask(BookFormat, Segment, String)` reports every gate rule (multiset, pair order, emptied pair, line
  break) as `ErrorCode.validation`; the rule itself is not visible to callers.
- `DisplayText` lives in the root package `ua.bookloom.pipeline`.
- Whole-word matching must not use `\b` (ASCII-only since JDK 19); `qa.NameRemoval` uses `(?<![\p{L}\p{N}])…(?![\p{L}\p{N}])`
  — 9.1's scan and 9.3's `ProtectedSpans` should reuse that rule.

## Decisions group 8 took that later tasks must honour

- **Resume inside a chunk (10.2, 10.4).** `heal.QualityLoop.start` makes the chunk's one judge call and returns a
  `ChunkDecider`; `nextDecision()` decides one segment per call in document order and, when a model call answers an
  error that does not flag at once, returns that error WITHOUT advancing — calling it again redoes the same segment.
  There is no way to rebuild a decider for a half-decided chunk without a new judge call: if a pause may outlive the
  decider (a restart, or a decider not kept across the pause), 10.2/10.4 must either keep it or accept a re-judge of
  the undecided pairs, and say which.
- **`SegmentPath` meaning.** DRAFT = accepted as drafted, no self-heal round; REPAIRED = accepted after at least one
  round. A FLAGGED `SegmentOutcome` records DRAFT or REPAIRED only to say whether rounds ran — any "repaired and
  accepted" count (10.5, 13.2) filters on status ACCEPTED as well.
- **What a FLAGGED segment carries.** Its last evaluation's QA findings plus the findings of the verdict that last
  decided it (the chunk's, or a re-judge's), the last evaluated confidence, that verdict's score, and — only when it
  was flagged at once — `flagReason`. `SegmentRecord` (10.2) must be able to store every one of these.
- **Foreign marking and uncatalogued languages.** `LanguageTags.normalize` answers empty for a tag outside the
  catalogue, so under the Keep policy a block declared `la` (Latin) is never marked foreign and its echo/script checks
  run. 9.3's kept foreign runs inherit the same limit. Decide at the re-plan whether an uncatalogued but well-formed
  tag should still count as "different from the source".

## Open quality items found at the checkpoint (not group-8 scope)

- **No output cap is sent with any request.** `TokenEstimator.outputAllowance` is computed for every call but only
  scales the timeout; neither `num_predict` (Ollama) nor `max_tokens` (OpenAI-compatible) is sent. In the packaged
  window, `qwen2.5:1.5b` looped on one segment of `earth-gravity.md` until the 3-minute timeout, three attempts in a
  row (~9 minutes for one paragraph) before the run moved on. Capping output at the allowance (with a margin) would
  turn a runaway reply into a cut-off one, which design D3 already flags at once. Decide in the re-plan (llm-provider /
  inference capability, near task 10.4's failure routing) — it changes a request field in both dialects.
- **Real-model runs on the job's original path flag short emphasised sentences.** `TranslateCommandLiveTest`
  (`:app:liveLocal`, written before task 5.8) now fails with the intended `gemma4:e4b-mlx`: the model answers
  `Другий ⟦g0⟧⟦g1⟧ позначений абзац.` for `The ⟦g0⟧second⟦g1⟧ marked paragraph.`, the `EMPTIED_PAIR` rule (task 5.8,
  ADR-0040) refuses it — correctly: accepting it wrote `Другий ** …` before — and the one placeholder repair repeats
  the mistake. Two contributing causes to weigh in 9.6/10.2: the preceding-target context shows earlier targets in
  their restored form (`*перший* позначений абзац.`), which the model imitates, and the placeholder-repair prompt does
  not say which rule broke (the gate's `AppError` message does). The quality loop's directed fix (group 8) gets the
  gate's message as its finding, so re-run `./gradlew :app:liveLocal` once 10.2 wires the loop, and update the test's
  expectation then. The small `qwen2.5:1.5b` also drops every placeholder of a Markdown link list (`MULTISET`).

- **Two tests fail only under load**: `OllamaClientUsageTest.chat_temperatureAndOrContextWindow_postsExpectedNativeOptions[2]`
  (`:llm`, a WireMock 404) and `TranslationJobProviderAbortTest` (`:pipeline`, a 5 s `awaitChatRequests` budget). See
  the checkpoint report for what the investigation found.
- **The spec still names provider types that do not exist** (`Provider`, `ProviderFactory`, `ProviderProfile`,
  `CredentialRef`) in `02_Architecture/04_LLM_INTEGRATION.md`, `02_MODULES_AND_LAYERING.md`, `10_DI_AND_LIFECYCLE.md`,
  `01_Product/04_LLM_PROVIDERS_AND_MODELS.md`, `00_Foundation/02_GLOSSARY.md`, `04_DESIGN_DECISIONS.md`,
  `06_IMPLEMENTATION_STAGES.md` (the agent files were corrected at this checkpoint; ADRs stay as written). Task 15.4,
  or a small docs change, should rename them to `ChatModel`, `ChatModelFactory`, `ProviderClient(Factory)`,
  `ProviderConfig`, and mark credential resolution as planned.
- **`docs/Architecture.md` is only partly refreshed** (§1, §4.4, §6–§9); its full regeneration is task 15.4.
