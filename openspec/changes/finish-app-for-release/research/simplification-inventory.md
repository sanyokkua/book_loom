# Simplification inventory — dead code and over-engineering not covered by `finish-app-for-release`

Date: 2026-11-14 · branch `feature/finish-app-for-release` (production code = `master`, `8d507357`) · read-only.

## How this was measured

- **Sources**: 2,130 files under `modules/*/src/main/java` (230,808 lines), 2,120 test files, 99 non-binary resources.
- **Bytecode**: the 5,669 class files under `modules/*/build/classes/java/{main,test,archTest}` (built 2026-11-12 15:12;
  their top-level class set equals today's source set in all 8 modules, 0 differences) were read with `javap -v -p`.
  A method, field or enum constant counts as "used" when any class's constant pool refers to it (calls, method
  references and lambdas included) on the class, a supertype or a subtype. This is exact for calls; it does not see
  reflection, so Guice (`@Inject`, `@Provides`, `@ImplementedBy`), FXML (`fx:controller`, `%key`), Jackson and
  `valueOf` were checked separately by grep. **Caveat**: the classes are one day older than the squash merge; if the
  merge changed code after 15:12 on Nov 12, single rows can be off — re-run after the next build.
- **Source graph**: a word-level reference graph over comment-stripped sources, with reachability from `main` methods,
  `module-info.java` and every type named in a resource (FXML controllers).
- Not run: Gradle, any model, any test.
- Scripts and raw tables: `/private/tmp/claude-501/-Users-ok-Development-GitHub-book-loom/0240d80d-c76b-48e4-9a50-2f3b1bb0dced/scratchpad/simplify/`
  (`bc.py` → `bc.pkl`, `an.py`, `msize.py` → `msize.tsv`, `iface.py` → `iface.tsv`, `fanin.py`, `keys.py`, `enums.py`,
  `rec.py`, `dup.py`, `evalstale.py`).

**What the planned change already removes (left out below unless marked "covered")**: chapter summaries and story
shape; the recurring-term lexicon (`LexiconService`, `TermReading`, `LexiconReading`, `TermChoice`, term learning, the
lexicon store); tallied gender; name refusal and shared-word swap; five brief keys; the Models chooser and `ModelRoster`;
the Start dialogs; eval code of removed stages (task 1.5); `docs/spec_baseline`; the rebuild of `SearchableCombo` and of
the provider form's model entry on it (tasks 8.1, 8.8 — so `ModelFilterField` vs `SearchableCombo` is **covered**).

**Headline**: the code base is tight at class level (10 dead classes out of 2,043) and has almost no copy-paste (2
duplicated blocks). What it carries is (a) one unused extension seam threaded through 15 files, (b) about 190 methods
nobody in production calls, (c) a parser that extracts 33 facts only a test reads, and (d) a very fine-grained class
split: 741 classes (36 %) have exactly one user.

---

## 1. Dead code

### 1.1 Classes no production code references — sure, mechanical

Evidence for each: no other file under `modules/*/src/main` names the type (comments stripped), no resource names it,
no `bind(`/`@Provides`/`@ImplementedBy`, not in `StoredShapes` roots, not named in the change's `tasks.md`/`design.md`.

| Path (under `modules/`) | Lines | Referenced by | Note |
|---|---|---|---|
| `api/.../api/project/SnapshotRendering.java` | 19 | nothing | record; not part of any stored shape |
| `api/.../api/project/SnapshotTerm.java` | 41 | `SnapshotTermTest` (33 lines) only | delete with its test |
| `api/.../api/project/SnapshotTmHit.java` (+ nested `TmHitKind`, 3 constants) | 36 | nothing | |
| `llm/.../llm/dto/OllamaErrorBody.java` | 14 | nothing | error bodies are read another way |
| `llm/.../llm/dto/OpenAiErrorBody.java` | 15 | nothing | |
| `llm/.../llm/profile/GgufMetadata.java` | 47 | `ModelFactsSnapshotTest` only | head of a dead chain ↓ |
| `llm/.../llm/profile/GgufIdentity.java` | 21 | `GgufMetadata` + that test | dead once `GgufMetadata` goes |
| `llm/.../llm/profile/ArchitectureAliases.java` | 37 | `GgufMetadata` + `ArchitectureAliasesTest` (27 lines) | dead once `GgufMetadata` goes |
| `llm/.../llm/profile/PipelineTags.java` | 48 | `ModelFactsSnapshotTest` only | |
| `pipeline/.../pipeline/checks/DictionaryWordValidator.java` | 49 | `WordValidatorTest`, `WordValidatorQaTest` | see 3.1 — the whole seam |

Total: **10 files, 327 lines** of main code, plus about 150 lines of tests that exist only for them.

### 1.2 Methods no code calls at all — sure, mechanical

**45 methods, 358 lines** (2 of them, 26 lines, sit in classes the change already removes) — full list in Appendix A.
The ones worth a look, because they are more than a stray accessor:

| Path | Method | Lines | Why it matters |
|---|---|---|---|
| `api/.../api/pipeline/ReviewDesk.java` + `pipeline/.../review/ReviewDeskImpl.java` | `audit(String)` | 8 + 6 | a **port method** with an implementation and no caller in main or tests (the command line's `report.audit(…)` is another type) |
| `pipeline/.../pipeline/run/Step.java` | `fold(…)` on the interface, `Done`, `Stopped` | 27 of 47 | more than half of the file is an unused visitor |
| `pipeline/.../pipeline/prompt/LanguageRules.java` | `batchExamples(source, target)` | 7 | `FrameWording` reads the pair file's `batchExample.*` itself; the constant `BATCH_EXAMPLE` goes with it |
| `pipeline/.../pipeline/checks/CharacterNames.java` | `isLikely`, `likelyOf` | 22 | |
| `pipeline/.../pipeline/lexicon/TermMatch.java` | `matches/2` | 19 | covered if the package goes with the lexicon — check |
| `pipeline/.../pipeline/prompt/PromptTemplates.java` | `sectionsOf/3` | 12 | |
| `document/.../document/mask/PlaceholderGate.java` | `compare/2` (public static) | 11 | |
| `ui/.../ui/control/Banner.java` | `addAction/3` | 17 | |
| `ui/.../ui/SettingWriter.java` | `isWritten/1` | 14 | |
| `ui/.../ui/dialog/LeaveDialog.java` | `askUnsaved/2` | 10 | |
| `ui/.../ui/state/RunNotice.java` | `ProviderError.endpointHost()` | 13 | |
| `ui/.../ui/control/RequestWords.java` | `line/4` | 12 | |

Confidence **sure** for "no caller"; `SearchableCombo.invalidTextProperty()` is a JavaFX property accessor and that
class is rebuilt by task 8.1 — leave it to that task.

### 1.3 Enum constants nothing refers to

Evidence: no `Fieldref` from any other class, the name occurs once in its own file, and the enum has no
`values()`/`valueOf` call in main.

| Constant | Confidence | Note |
|---|---|---|
| `api/llm/ModelProfile.Quirk.NO_KNOWLEDGE_HINT` | sure | 0 main, 0 test references |
| `llm/engine/CheckedModelId.Verdict.NOT_LISTED` | sure | 0 / 0 |
| `pipeline/proofread/RevisionWriter.Change.NEIGHBOUR` | sure | 0 / 0 |
| `ui/control/SearchableCombo.Source.RESTORED` | sure | 0 / 0; falls under task 8.1 |
| `api/project/DeferralReason.NAME_UNRESOLVED` | likely | 0 / 0, but the enum is stored in JSON — fine before release (ADR-0043: the database is set aside anyway) |
| `api/project/BriefField.{FOREIGN_PASSAGES, FOOTNOTES, UNITS, BALANCE, ALSO_TRANSLATE}` | likely | 0 / 0; this enum only feeds the stored `likely` set, which never holds them. The change removes four other constants of the same enum (task 8.7) — do these in the same edit |

Not dead (checked): `Genre`, `Language`, `MonoFont`, `SlantFace`, `NameField.Chip`, `RuleKey`, `RefusalPhrases` are
iterated or looked up by name.

### 1.4 Message keys, prompt files, property keys, settings, switches

- **`MessageKey`** (4,359 lines, 2,152 constants; the task brief's 2,143 is out of date): `messages_en` and
  `messages_uk` hold exactly 2,152 keys each, **no key missing from `MessageKey` and none extra, EN = UK**. 93 keys are
  used from FXML (`%key`), 40 through `GenreLabels` (`valueOf(PREFIX + …)`). **7 constants are unused** (sure,
  mechanical; about 14 lines in the enum and 14 in the two bundles): `ACTIVITY_DETAIL_SEGMENTS`, `SETTINGS_MODEL_LABEL`,
  `EXPORT_BOOK_TITLE`, `EXPORT_BOOK_AUTHOR`, `REVIEW_RETRY` (the `ActivityKind` constant of the same name is a different
  enum and is used), `DIALOG_SAME_PAIR_KEEP`, `LIVE_MODE`. An eighth, `BRIEF_SOON` ("not used yet"), is reachable only
  through a parameter that is always `false` (see 3.4).
- **`PromptName` / `.prompt` files**: all 17 constants and all 18 files are used; 7 have exactly one caller, which is
  normal. `CHAPTER_SUMMARY`, `TERM_CHOICE`, `TERM_SUMMARY`, `lexicon-reading`, `term-reading` are **covered**.
- **Language files**: three keys of `pipeline/prompt/languages/ru.properties` are read by nothing — `honorifics`,
  `clippedVocative`, `femSurnameMap` (grep over `pipeline/src/main/java`: 0 hits each; sure, mechanical).
  `blocks.properties`, `typography-rules.properties`, `quote-pairs.properties`, `style-phrases.properties` and the
  `labels/*.properties` files are read by composed keys or by iteration — no dead key found.
- **`AppSetting`**: all 11 keys are read and written.
- **Environment variables and system properties**: all are read. Three are documented nowhere and set by no script or
  test: `BOOKLOOM_WINDOW` (`app/WindowPlacement`), `bookloom.smoothScroll` (the kill switch in
  `ui/control/SmoothScroll`), `bookloom.log.scroll` (`ScrollStats`, one doc mention). Kind: check — either document
  them in `docs/DEVELOPMENT.md` or delete the switch (design decision; smooth scrolling itself stays, per the owner).
- **Experiment leftovers**: no `static final boolean … = false` flag exists in main. T8 ("relevant earlier pairs —
  stays off") left one method behind: `pipeline/index/BookIndex.sharing(terms, beforeOrdinal, limit)` (33 lines, its
  Javadoc says "for relevant earlier pairs"), called only by `BookIndexSharingTest` (74 lines). Sure, mechanical.
  X3, T6 and the token-rule reorder left nothing in main.

---

## 2. Test-only production code

**159 methods, about 1,776 lines** of main code are called only from test sources (12 of them, 184 lines, are inside
classes the change removes; **147 methods, about 1,590 lines remain**). Full list: Appendix B. Confidence **sure** for
"no production caller". Removing them is not always mechanical, because tests have to be re-pointed; the groups:

| Group | Where | Size | Mechanical? |
|---|---|---|---|
| **Port methods only the contract tests call** | `api/persistence`: `ProjectRepository.findByNameKey`, `.findByPair`, `SegmentRepository.byUnit`, `.firstPending`, `TmRepository.put`, `PromptFrameRepository.all` — each with an in-memory and a SQLite implementation | 6 port methods × 3 = 18 methods, 222 lines, plus their contract tests | yes — delete the method, both adapters and the contract case |
| **Withers written for tests** | `api/llm/ProviderConfig.with{DisplayName, CredentialRef, Auth, ApiKeyHeader, ModelsPath, Headers, CustomModels, ChatPath}` (8 methods, 188 lines of a 398-line record), `ModelFacts.withLanguages` (29), `SegmentRecord.withRejectedTarget` (27), `SafeDetails.withAttempt` (23), `ChatRequest.withSource` (11), `ModelSettings.withoutRefusal` (10) | about 290 lines | needs a decision: move to test fixtures (`testFixtures` source set or a test builder) — the tests keep their convenience, `:api` loses it |
| **A second entry point that only tests use** | `pipeline/SegmentTranslator.translate` ×2, `.translateSplit`; `batch/BatchDrafter.draft/3`; `qa/QaEvaluator.evaluate/2`; `export/BookExporter.export/3`; `run/PromptRequests.{draftRequest, formatReaskRequest, repairRequest ×2, batchRequest}`; `prompt/DraftReplyParser.{parse, parseRepair}`; `context/ContextBuilder.plan/5`; `review/RetryDraft.retry/5`; `api/pipeline/ReviewDesk.retry/5` (a default method) | about 230 lines | **design decision** — these tests drive an overload production never takes, so they prove a path that is not the shipped one (rule "never mock the boundary a test exists to prove" in spirit). Re-point the tests at the production entry, then delete |
| **Accessors that expose internals to tests** | `ui/state` (`ProgressLedger.rebuilt` 35, `TestDetails.of/7` 22, `ActivityTracker.Handle.progress` 18, `StateMirror.publishRunStarted/publishOutcome`, `ReviewViewModel.open/acceptAvailable`, `TranslatingViewModel.pendingRemain/preparing`, …), `RunSummaryLogger.heldCallTimes`, `RoutedCalls.heldPauseCounts`, `EvidenceAppender.file/heldBytes` | about 45 methods, 420 lines | check each: some are legitimate test seams (package-private "held…" probes), some are old API |
| **Whole classes that only tests reach** | the four `llm/profile` classes and `DictionaryWordValidator` of 1.1 | 202 lines | yes |

Smoke-only code in main (not a candidate, listed for completeness): `app/ResolveAll`, `ui/OpenStepsModule`,
`BookLoomApplication.smokeBuild` — used by the packaged image's `resolve-all` launch check.

Eval-harness-only: nothing in main is used only by the eval harness apart from the test-only rows above
(`BookIndex.sharing` was the one case; the harness itself lives in test sources).

---

## 3. Over-engineering candidates (YAGNI / KISS / DRY)

### 3.1 `WordValidator` — an extension seam with no production implementation — sure; one small decision

- `pipeline/checks/WordValidator` (32 lines, 1 abstract method). Implementations: `NoWordValidator` (13 lines, returns
  nothing) and `DictionaryWordValidator` (49 lines, **tests only**).
- Production binding: `PipelineModule:78  bind(WordValidator.class).toInstance(WordValidator.none())`; five more call
  sites pass `WordValidator.none()` directly (`CallFrame` ×2, `RetryDraft`, `ExportJobImpl`, `QaEvaluator`).
- The instance is threaded through **15 main files**: `PipelineModule`, `RunWiring`, `ProofreadWiring`, `RunChecks`,
  `CallFrame` (a record component), `QaEvaluator`, `TextCheckGates`, `AuditRecorder`, `FinalAudit.Book` (a record
  component), `RunAudit`, `ProofreadStage.Parts`, `ProofreadStage`, `RetryDraft`, `ExportJobImpl`, plus
  `CheckName.UNKNOWN_WORD` and `FindingKind.UNKNOWN_WORD`, which can therefore never fire in the app.
- `AGENTS.md`: "No word dictionary is bundled or planned (owner decision)."
- Removal: delete the interface, both implementations, the parameter in 15 files, the two enum constants and two test
  classes (122 lines). About **250 lines of main code and one constructor/record parameter in 15 files**. The only
  decision is confirming "never" for the dictionary.

### 3.2 A model-name parser that extracts facts nothing reads — likely; design decision

- `llm/profile`: 57 classes, 5,316 lines; only 19 are used from outside the package, 38 are internal.
- `llm/profile/NameFacts` (70 lines): **25 of its components are read only by tests** (`ctx`, `date`, `dateEight`,
  `dwq`, `dynamic`, `expertB`, `experts`, `finetune`, `finetuner`, `format`, `imatrix`, `impliedFormat`, `instance`,
  `monthDay`, `namespace`, `qat`, `quant`, `quantExtra`, `quantType`, `release`, `shard`, `sidecar`, `spec`,
  `unquantized`, `unspecified`). `llm/profile/NameOrigin` (37 lines): **8 components test-only** (`alias`, `file`,
  `inputForm`, `quantizer`, `revision`, `routerVariants`, `serverAlias`, `subfolder`). `ModelIdentityParser` (278
  lines) and its helpers fill them. `QuantRow.{confidence, family, needsImatrix, source}`,
  `IdentityPattern.{allowedVariants, effB, line, requiredVariants, sizes, version}` and
  `BaseFamilyAliases.Entry.{distillOf, families, maxB, minB, version}` have no accessor call either, but may be used
  inside their own class — check.
- The change says "the same requests for every model … no special model gets a profile, prompt or format of its own"
  and moves the model facts under the provider's picker. That is the moment to decide which facts the window shows and
  delete the parsing of the rest. Not mechanical; potential several hundred lines in `llm/profile` and `llm/knowledge`.

### 3.3 Interfaces with one implementation and no test fake

Measured over all 126 interfaces and 9 abstract classes in main. Excluded: the `:api` storage ports (two adapters each),
every interface a test implements or fakes (34 with one main and one test implementation, e.g. the nine
`ui/dialog/*Prompt` interfaces — each has a recording fake, they are justified), sealed hierarchies, and functional
callbacks implemented by lambdas.

| Interface / abstract class | Implementer | Injection or use sites (main) | Tests naming it | Verdict |
|---|---|---|---|---|
| `pipeline/DraftPrompts` (56 lines, 7 methods, package-private) | `TranslateDraftPrompts` (275) | 5 files | 0 | likely — fold into the class; its Javadoc says both callers "plan them … as a batch of one", so the second variant is gone. Mechanical |
| `ui/state/ModelFormProperties` (abstract, 100 lines) | `ModelForm` (389) | 1 | 0 | likely — a superclass that exists to keep `ModelForm` under 400 lines (see 4.2). Decision |
| `ui/dialog/DeleteDataPrompt` (18) | `DeleteDataDialog` | 3 | 0 | check — its Javadoc says "so Delete all data can be tested against a recording fake", and no test has one. Either write the test or drop the interface |
| `ui/state/ExportAnnouncer` (13) | `ExportAnnouncement` | 6 (1 `bind`) | 0 | check — same situation |
| `document/model/OpenDocumentRegistry<T>` (abstract, 58) | 4 empty subclasses (`OpenEpubRegistry` … 8 lines, 2 of code each) | 4 | 0 | check — subclasses exist "purely so Guice can inject each as its own" type; a `TypeLiteral` binding or one registry keyed by format would do. Small |
| `pipeline/checks/GenderCheck` (33) | `UkrainianGenderCheck` | 5 | 3 | keep — a real per-language extension point |
| `api/document/BookInspector`, `SentenceSplitter`, `api/EnvironmentVariables`, `api/pipeline/ProviderProbe`, `ui/state/StepLocks`, `ProgressFigures`, `ui/dialog/TextClipboard`, `app/env/CommandRunner`, `llm/…/ProfileRows`, `BaseKeyResolver` | one each | 3–13 | 1–8 (lambda fakes or module-edge ports) | keep |

Guice: 159 `bind`/`install` lines and 11 `@Provides` in 9 modules (864 lines); 3 qualifiers (`@BackgroundExecutor` 47
files, `@IoExecutor` 5, `@BuildVersion` 4). Nothing bound-and-unused was found. No strategy enum or registry with a
single variant was found beyond the rows above.

### 3.4 Knobs with one possible value

- `ui/screen/BriefCards.card(id, messages, title, boolean unused, body…)`: every caller reaches it through the
  4-argument overload that passes `false`; the `if (unused)` branch, the `tag-note` label and the key `brief.soon` are
  dead. Sure, mechanical, about 10 lines + 1 key.
- `WordValidator.none()` (3.1).
- Not checked exhaustively: constant-valued parameters cannot be found by grep; a compiler-based check (IntelliJ
  "parameter always has the same value", or Sonar S1172 for unused ones) would list the rest — worth one run inside the
  change's "every analyser at zero" task.

### 3.5 Duplicates (DRY)

- **Literal duplication is almost absent**: over all main sources (comments, imports, null checks and log lines
  removed), only **2 file pairs** share a block of 11 lines or more: `api/llm/ModelFacts` ↔ `llm/capability/FactsDraft`
  (21 lines, a record and its builder) and `document/epub/EpubStats` ↔ `document/fb2/Fb2Inspection` (14 lines). No
  near-duplicate pair exists among the 43 classes of `pipeline/checks`. Nothing to do here.
- **Two ways to wrap a label** (check, decision): `Wrapped.label` (55 uses), `Wrapped.wholeLines` (25),
  `Wrapped.wholeWords` (3) against **118 hand-written `setWrapText(true)`** and 35 hand-written
  `setMinHeight(Region.USE_PREF_SIZE)` in `modules/ui/src/main`. `AGENTS.md` makes `Wrapped.wholeLines` the rule for
  a wrapping label; 118 sites do not follow it (some legitimately — `TextArea`, cells). This is the one place where a
  "one implementation per job" pass has real size; it also carries the Linux font risk the manual describes.
- **Two model-list filters** — `ModelFilterField` (121) and `SearchableCombo` (266): **covered** by tasks 8.1 and 8.8.
- **Eleven pairs of types with the same simple name in different packages** (check, rename or merge):
  `BriefField` (`api/pipeline` 26 lines **and** `api/project` 24 lines — two enums of the same name in one module),
  `ModelFacts` (`api/llm` 238 / `ui/state` 69), `NameOrigin` (`llm/profile` 37 / `api/project` 9), `Window`
  (`pipeline/context` 68 / `pipeline/index` 14), `ReviewQueries` (`pipeline/review` 383 / `ui/state` 114),
  `ProjectOpener` (`pipeline/project` 153 / `ui/state` 212), `MeaningQuestions` (134 / 83), `ProviderChecks`
  (`ui/screen` 307 / `ui/state` 23), `RunEnd` (20 / 55), `VisibleText` (`util/text` 57 / `ui/state` 30), `Severity`
  (`ui/notify` 47 / `api/project` 17). None is a duplicate implementation, but each forces a fully qualified name
  somewhere (`BriefSuggestion.java:257` writes `ua.bookloom.api.project.BriefField` in full).
- **Dialogs**: 39 classes, 4,800 lines, all built on `ModalCard`/`FormCard`; no duplicated dialog found. The change
  removes `NarratorDialog`, `CharacterGendersDialog`, `MeaningNameDialog` (Start cards) and `AddRecurringTermDialog`.

### 3.6 Record components nobody reads

58 records hold 134 components whose accessor production never calls and that the record does not use itself
(Appendix C; every row "check" because of Jackson). The clear ones besides 3.2:

- `pipeline/index/Posting`: 5 of 7 components (`unitId`, `offset`, `sentence`, `capitalised`, `sentenceInitial`) are
  written by `IndexBuilder` for every word of the book and never read (only `ordinal` and `standalone` are). Likely;
  removing them also shrinks the in-memory index. `pipeline/index/WordStats`: 6 components test-only.
- `ui/state/WaitingCall.{attempt, maxAttempts}`, `ui/state/ProviderRow.{endpoint, engine, kind}`,
  `pipeline/context/RunFrame.{bookFacts, ruleFacts, styleSheet}`, `api/pipeline/{SegmentDrafted.confidence,
  BatchStarted.kind, RecoveryWaiting.progress, NamesEstimate.callsByKind}` — check; event records may be read by tests
  or logs only.

---

## 4. Size and cohesion hot spots (SOLID)

### 4.1 Packages

| Package | Classes | Lines |
|---|---|---|
| `ui/state` | **365** | 39,368 |
| `ui/screen` | 134 | 19,390 |
| `pipeline/glossary` | 115 | 18,349 |
| `api/pipeline` | 108 | 5,430 |
| `ui/control` | 84 | 10,891 |
| `api/llm` | 71 | 4,403 |
| `llm/profile` | 57 | 5,316 |
| `pipeline/run` | 55 | 7,565 |
| `pipeline` (root) | 50 | 5,446 |
| `pipeline/prompt` | 50 | 4,502 |

`ui/state` is a flat package of 365 classes — view models, run state, provider form, export, review, estimates,
glossary edits. Its largest name families: `Run*` 30 classes, `Model*` 22, `Provider*` 21, `Export*` 20, `Review*` 19,
`Project*` 13, `Brief*` 12, `Change*` 10. Splitting it into sub-packages by these families is a mechanical move
(package-private access is the only thing to watch) and would make the next reader's job much easier. `pipeline/glossary`
loses a good part of its 115 classes to the change (lexicon, tally, term reading).

### 4.2 The 400-line cap produces single-user classes

- **741 of 2,043 top-level classes (36 %, 86,031 lines) are used by exactly one other class**; 462 of them (51,216
  lines) are named by no test. 312 more have two users. Median class length is 78 lines.
- 66 classes are at 370–400 lines; 7 are at exactly 400 and 9 at 399 — they are written to the cap.
- Typical shapes: an abstract superclass with one subclass (`ModelFormProperties`/`ModelForm`), parameter-bundle
  records that exist for the 7-parameter limit (`*Parts` 8 files, `*Tools` 5, `*Inputs` 5, `*Wiring` 2, `*Stores` 1 —
  1,200 lines), screen families such as `ui/screen/Provider*` (13 classes, all 13 with one user) and `ui/screen/Brief*`
  (15 classes, 12 with one user, 1 named by a test).
- This is a **policy decision, not a list of deletions**: the split is consistent and enforced by Checkstyle, and
  undoing it by hand is not worth it. The cheap lever is the rule itself — allow package-private nested classes or a
  higher cap for view classes — so new code stops paying the tax. Recorded here as a measured fact for the owner.

### 4.3 The 15 classes at the cap that mix the most responsibilities

Ranked by a quick read of fields, public methods, constructor parameters and the number of project classes each one
touches (bytecode counts).

| # | Class (lines) | Measured | What is mixed |
|---|---|---|---|
| 1 | `ui/state/RunSession` (397) | 20 fields, 44 project classes | job listener, run lifecycle, pause/recovery state and event fan-out in one object |
| 2 | `pipeline/project/ProjectServiceImpl` (396) | 12 constructor parameters, 14 public methods, 36 classes | open, import, rename, relocate, brief edits, run records |
| 3 | `pipeline/glossary/GlossaryServiceImpl` (396) | 16 public methods, 35 methods | CRUD, CSV, scans, model review, suggestions behind one port |
| 4 | `ui/state/ReviewViewModel` (399) | 18 fields, 28 public methods | list state, selection, edit buffer, every desk action |
| 5 | `ui/state/SettingsViewModel` (400) | 19 fields, 25 public methods | provider list, form, tests, dirty tracking, navigation guard |
| 6 | `ui/state/BookBriefViewModel` (400) | 28 public methods | languages, tone, policies, suggestion, lock rules |
| 7 | `ui/state/ProviderForm` (399) | 14 fields, 21 public methods | form state, validation, saving, "use this one" |
| 8 | `ui/state/TranslatingViewModel` (394) | 19 fields, 14 public methods, 30 classes | summary strip, live rows, banners, estimates |
| 9 | `ui/state/ProviderTestRunner` (399) | 21 fields | three test stages, their timing, results and cancellation |
| 10 | `pipeline/run/WorkList` (400) | 20 public methods, 12 fields | the pending queue, its ordering, progress counts and resume position |
| 11 | `llm/client/openai/OpenAiCompatibleClient` (396) | 14 public methods, 11 fields | request shaping, streaming, model listing, error mapping |
| 12 | `document/DocumentService` (392) | 8 constructor parameters, 34 classes | the four formats' open/mask/unmask/export behind one façade (acceptable as a port, but it is at the cap) |
| 13 | `pipeline/setup/BriefSuggester` (399) | 41 project classes over 11 packages | sampling, calls, reply reading, evidence checks |
| 14 | `ui/screen/SettingsController` (386) | 36 fields | six tabs' wiring in one controller |
| 15 | `api/project/BookBrief` (399) | 21 components, 40 public methods | a data record with 19 withers and rules; loses 4 components in task 8.7 |

Also at the cap but going away or shrinking with the change: `pipeline/glossary/PronounGender` (400),
`pipeline/lexicon/LexiconServiceImpl` (399), `pipeline/glossary/LexiconReading` (395), `pipeline/run/ChapterSummaries`
(381), `pipeline/lexicon/CooccurrenceLearner` (393), `ui/screen/ModelsController` (375).
`api/SafeDetails` (461 lines) and `MessageKey` are the two files with a `FileLength` suppression.

### 4.4 `MessageKey`

4,359 lines, 2,152 constants, one Javadoc line per constant (394 of them the formula "Hover explanation of the control
labelled by …"). 7 unused (1.4). Bundles are complete and identical in key set. The file is a generated-looking
registry kept by hand; if it is to shrink, generating it from `messages_en.properties` at build time would remove
4,359 hand-kept lines — a design decision with a build-logic cost, not proposed here as a must.

---

## 5. Stale non-code (eval)

`modules/pipeline/src/test/java/ua/bookloom/pipeline/eval`: 252 files, 31,474 lines; `src/test/resources/eval`: 2.9 MB.
Every eval class is referenced by another test file or is itself a test; no orphan class.

| Path | Kind | Evidence | Confidence |
|---|---|---|---|
| `eval/LongParagraph.java` (46), `eval/LongParagraphsTest.java` (65), `resources/eval/long-paragraphs.json` | stale | the dataset is loaded only by `LongParagraph`, which is used only by its own test; the X3 arm it fed "was removed" (`AGENTS.md`) | likely — mechanical |
| `index/BookIndexSharingTest.java` (74) | stale | the only caller of `BookIndex.sharing` (T8, "stays off") | sure — goes with 1.4 |
| `resources/eval/baseline/*.json` (9 files, 524 KB) | check | read by folder (`BaselineFilesTest`); several are for `gemma4:e4b` at Fast/Max, which the change's new baseline (task 1.7) replaces | check — decide in task 1.7 |
| `resources/eval/datasets/.DS_Store`, `datasets/article-gravity/.DS_Store` | junk | not tracked by git (`git ls-files` = 0), local only | sure — delete locally |
| `eval/EvalChapterSummaries*`, `SummaryGateTallyTest`, coref suite, `--suite` arms of removed stages | covered | task 1.5 / 4.7 | — |

---

## Totals

| Kind | Confidence | Files or items | Main lines | Mechanical? |
|---|---|---|---|---|
| Dead classes | sure | 10 files | 327 | yes |
| Dead methods (no caller anywhere) | sure | 43 methods in about 40 files (2 more are covered) | about 330 | yes |
| Dead enum constants | sure 4 · likely 6 | 10 constants | about 20 | yes |
| Dead message keys, property keys, dead flag | sure | 8 keys + 3 language-file keys + 1 parameter | about 45 (+ 16 bundle lines) | yes |
| T8 leftover (`BookIndex.sharing`) | sure | 1 method | 33 | yes |
| Test-only methods | sure (no production caller) | 147 methods in about 115 files (12 more are covered) | about 1,590 | 222 lines yes (ports); the rest needs tests re-pointed or fixtures moved |
| `WordValidator` seam | sure | 3 files deleted, 15 files touched | about 250 | yes after one decision |
| Single-implementation interface / one-subclass abstract class, no fake | likely 2 · check 3 | 5 types | 245 in the types themselves | 1 mechanical, 4 decisions |
| Facts parsed and never read (`llm/profile`) | likely | 33 components in 2 records + their parser code | several hundred (not measured exactly) | decision |
| Unread record components elsewhere | check | 56 records, about 100 components | about 100 | case by case |
| Stale eval | likely | 3 files + 1 dataset (+ 1 test) | 111 test lines + 74 | yes |
| **Sum that compiles away with no decision** | sure | | **about 1,230 main lines** (327 + 330 + 20 + 45 + 33 + 222 + 250) | |
| **Sum that needs a decision first** | sure–likely | | **about 1,700–2,200 main lines** (1,370 test-only + 245 + `llm/profile`) | |

Against 230,808 main lines this is about 1.3 % — the honest reading is that there is little dead weight to delete and
that the larger simplification available is structural (4.1, 4.2), not removal.

## The 20 highest-value simplifications, in order

1. **Remove the `WordValidator` seam** (3.1): a parameter in 15 files, two enum constants and three classes for a
   dictionary the owner decided never to ship. About 250 lines; the most over-engineering per line in the code base.
2. **Delete the 10 dead classes** (1.1) with their three tests. 327 + about 150 lines, compiles away.
3. **Delete the 43 uncalled methods** (1.2, Appendix A) — including the port method `ReviewDesk.audit` and the unused
   visitor in `run/Step`. About 330 lines, compiles away.
4. **Delete the six repository port methods only contract tests call** (section 2): 18 methods, 222 lines and their
   contract cases; the ports shrink to what the app uses.
5. **Re-point tests at the production entry points and delete the test-only overloads** in `SegmentTranslator`,
   `BatchDrafter`, `QaEvaluator`, `BookExporter`, `PromptRequests`, `DraftReplyParser`, `ContextBuilder`, `RetryDraft`,
   `ReviewDesk.retry/5` (about 230 lines). Value is test honesty more than size.
6. **Decide what model facts the window shows, then delete the unread name facts** (3.2): 33 components of
   `NameFacts`/`NameOrigin` and the parser code behind them; fits the change's "same requests for every model".
7. **Move the test-only withers out of `:api`** (`ProviderConfig` ×8, `ModelFacts`, `SegmentRecord`, `SafeDetails`,
   `ChatRequest`, `ModelSettings` — about 290 lines) into test fixtures.
8. **Remove the T8 leftover** `BookIndex.sharing` and `BookIndexSharingTest` (33 + 74 lines).
9. **Remove the always-false `unused` flag** of `BriefCards.card` with `brief.soon` (3.4).
10. **Remove the 7 unused message keys and the 3 unread `ru.properties` keys**, and `LanguageRules.batchExamples`
    (1.4, 1.2).
11. **Remove the 10 unreferenced enum constants** (1.3), the five `api/project/BriefField` ones inside task 8.7.
12. **One wrapping rule**: bring the 118 hand-written `setWrapText(true)` sites under `Wrapped.*` or state the
    exceptions (3.5) — the only sizeable DRY item, and it is the documented Linux-font risk.
13. **Split `ui/state` (365 classes) into sub-packages** by its families (`run`, `provider`, `model`, `export`,
    `review`, `project`, `brief`) (4.1). No behaviour change.
14. **Fold `DraftPrompts` into `TranslateDraftPrompts`** (3.3): one implementation, no fake, 56 lines.
15. **Trim `pipeline/index/Posting` to the two components that are read** (3.6); check `WordStats` the same way.
16. **Rename or merge the same-named type pairs**, first the two `BriefField` enums inside `:api` (3.5).
17. **Decide the class-size rule** (4.2): 741 single-user classes are its product; allowing nested package-private
    classes or a higher cap for views stops the growth. No code change required to decide.
18. **Either test or drop `DeleteDataPrompt` and `ExportAnnouncer`** — interfaces justified by a fake that does not
    exist (3.3); fold `ModelFormProperties` into `ModelForm` if the cap rule changes.
19. **Delete the X3 eval leftover** (`LongParagraph`, its test, `long-paragraphs.json`) and review the nine stored
    baselines when task 1.7 writes the new one (section 5).
20. **Document or delete the three undocumented switches** `BOOKLOOM_WINDOW`, `bookloom.smoothScroll`,
    `bookloom.log.scroll` (1.4); and run one compiler-based "parameter always has the same value / unused parameter"
    inspection inside the change's analyser task to catch what grep cannot.

## Known limits of this inventory

- Reflection is invisible to the bytecode pass. Guice, FXML, Jackson and `valueOf` were checked by grep; anything
  resolved by a string built at run time elsewhere would be missed (none was found).
- The class files are from 2026-11-12 15:12; the squash merge is from 2026-11-13. The class sets match, the method
  sets were not re-verified against source one by one (12 of the 45 "no caller" rows were, by grep, and held).
- Method sizes include Javadoc; 12 test-only methods and 1 uncalled method could not be sized (multi-line generic signatures).
- "Covered" marks are by class and package name against the change's proposal and task titles, not against its
  `design.md` line by line.
- Constant-valued parameters, unused private fields and unused parameters need a compiler-based inspection.

## Appendix A — methods with no caller in main or test bytecode (45)

Evidence: no `Methodref`/`InterfaceMethodref`/method-handle entry for the name+descriptor on the class, its supertypes or subtypes in any of the 5,669 compiled classes (main, test, archTest). `params` separates overloads. "covered?" marks a class the planned change already deletes or guts.

| File | Method | params | lines | covered? |
|---|---|---|---|---|
| `api/api/pipeline/ReviewDesk.java` | `ReviewDesk.audit` | 1 | 8 |  |
| `api/api/pipeline/TargetIssue.java` | `TargetIssue.isFlag` | 0 | 8 |  |
| `document/document/mask/PlaceholderGate.java` | `PlaceholderGate.compare` | 2 | 11 |  |
| `document/document/md/Frontmatter.java` | `Frontmatter$Split.bodyCharOffset` | 0 | 9 |  |
| `llm/llm/dto/OpenAiChatRequest.java` | `OpenAiChatRequest.alwaysSendFields` | 0 | 9 |  |
| `llm/llm/knowledge/LanguageRow.java` | `LanguageRow.segments` | 0 | 8 |  |
| `llm/llm/verify/RequestAnnouncer.java` | `RequestAnnouncer.on` | 1 | 9 |  |
| `pipeline/pipeline/DraftRequests.java` | `DraftRequests.prompts` | 0 | 4 |  |
| `pipeline/pipeline/SegmentTranslator.java` | `SegmentTranslator.targetLanguage` | 0 | 3 |  |
| `pipeline/pipeline/batch/ItemStatus.java` | `ItemStatus.isUsable` | 0 | 4 |  |
| `pipeline/pipeline/checks/CharacterNames.java` | `CharacterNames.isLikely` | 1 | 11 |  |
| `pipeline/pipeline/checks/CharacterNames.java` | `CharacterNames.likelyOf` | 2 | 11 |  |
| `pipeline/pipeline/glossary/ReadingCounts.java` | `ReadingCounts.votingWindows` | 2 | 7 | yes |
| `pipeline/pipeline/heal/MachineTarget.java` | `MachineTarget.none` | 0 | 8 |  |
| `pipeline/pipeline/heal/RepairReply.java` | `RepairReply$Malformed.reason` | 0 | 9 |  |
| `pipeline/pipeline/heal/ReplyFormatFinding.java` | `ReplyFormatFinding.isReplyFormat` | 1 | 3 |  |
| `pipeline/pipeline/lexicon/TermMatch.java` | `TermMatch.matches` | 2 | 19 | yes |
| `pipeline/pipeline/project/StoredBooks.java` | `StoredBooks.nothingToContinue` | 0 | 6 |  |
| `pipeline/pipeline/prompt/CallDescriptor.java` | `CallDescriptor.withTargets` | 1 | 9 |  |
| `pipeline/pipeline/prompt/LanguageRules.java` | `LanguageRules.batchExamples` | 2 | 7 |  |
| `pipeline/pipeline/prompt/PromptTemplates.java` | `PromptTemplates.sectionsOf` | 3 | 12 |  |
| `pipeline/pipeline/qa/TextGuards.java` | `TextGuards.wordCount` | 1 | 9 |  |
| `pipeline/pipeline/review/ReviewDeskImpl.java` | `ReviewDeskImpl.audit` | 1 | 6 |  |
| `pipeline/pipeline/run/ChunkDrafts.java` | `ChunkDrafts.all` | 0 | 3 |  |
| `pipeline/pipeline/run/PromptRequests.java` | `PromptRequests$PreparedBatch.segmentIds` | 0 | 4 |  |
| `pipeline/pipeline/run/Step.java` | `Step.fold` | 2 | 9 |  |
| `pipeline/pipeline/run/Step.java` | `Step$Done.fold` | 2 | 9 |  |
| `pipeline/pipeline/run/Step.java` | `Step$Stopped.fold` | 2 | 9 |  |
| `ui/ui/SettingWriter.java` | `SettingWriter.isWritten` | 1 | 14 |  |
| `ui/ui/control/Banner.java` | `Banner.addAction` | 3 | 17 |  |
| `ui/ui/control/LayoutClass.java` | `LayoutClass.widthClass` | 0 | 8 |  |
| `ui/ui/control/RequestWords.java` | `RequestWords.line` | 4 | 12 |  |
| `ui/ui/control/SearchableCombo.java` | `SearchableCombo.invalidTextProperty` | 0 | 4 |  |
| `ui/ui/dialog/LeaveDialog.java` | `LeaveDialog.askUnsaved` | 2 | 10 |  |
| `ui/ui/dialog/ModalCard.java` | `ModalCard.size` | 0 | 8 |  |
| `ui/ui/dialog/NameField.java` | `NameField.isTouched` | 0 | 4 |  |
| `ui/ui/screen/GlossaryCells.java` | `GlossaryCells$EntryCell.isSyncing` | 0 | 3 |  |
| `ui/ui/screen/ReviewInlinePanels.java` | `ReviewInlinePanels.isAsking` | 0 | 4 |  |
| `ui/ui/state/ActivityTracker.java` | `ActivityTracker$Handle.title` | 1 | 5 |  |
| `ui/ui/state/ChangeResults.java` | `ChangeResults.calls` | 0 | 8 |  |
| `ui/ui/state/HiddenWork.java` | `HiddenWork.activity` | 0 | -1 |  |
| `ui/ui/state/ProgressDialogModel.java` | `ProgressDialogModel.isHideable` | 0 | 8 |  |
| `ui/ui/state/ReviewPauseFollower.java` | `ReviewPauseFollower.pausedForReview` | 0 | 8 |  |
| `ui/ui/state/RunNotice.java` | `RunNotice$ProviderError.endpointHost` | 0 | 13 |  |
| `ui/ui/state/WaitingCall.java` | `WaitingCall.kindToken` | 0 | 8 |  |

## Appendix B — methods called only from test sources (159)

Same evidence, with at least one reference from a test class. Grouped by file; `name/params(lines)`; `-1` = size not measured.

| File | Methods | covered? |
|---|---|---|
| `api/api/SafeDetails.java` | withAttempt/2(23) |  |
| `api/api/llm/ChatRequest.java` | withSource/1(11) |  |
| `api/api/llm/ModelFacts.java` | kind/0(12), withLanguages/1(29) |  |
| `api/api/llm/ModelSettings.java` | speedOf/1(9), withoutRefusal/1(10) |  |
| `api/api/llm/ProviderConfig.java` | withApiKeyHeader/1(26), withAuth/1(26), withChatPath/1(21), withCredentialRef/1(26), withCustomModels/1(21), withDisplayName/1(26), withHeaders/1(21), withModelsPath/1(21) |  |
| `api/api/llm/VerificationReport.java` | isPassed/0(8) |  |
| `api/api/persistence/LexiconRepository.java` | record/3(10) | yes |
| `api/api/persistence/ProjectRepository.java` | findByNameKey/2(8), findByPair/3(10) |  |
| `api/api/persistence/PromptFrameRepository.java` | all/1(7) |  |
| `api/api/persistence/SegmentRepository.java` | byUnit/2(8), firstPending/2(8) |  |
| `api/api/persistence/TmRepository.java` | put/1(8) |  |
| `api/api/pipeline/BriefChoices.java` | isEmpty/0(8) |  |
| `api/api/pipeline/CallSnapshot.java` | answered/3(11), retrying/4(12) |  |
| `api/api/pipeline/EntryChanges.java` | isEmpty/0(8) |  |
| `api/api/pipeline/FieldEvidence.java` | isAgreed/0(8) |  |
| `api/api/pipeline/ProofreadRoundStarted.java` | isWholeBook/0(4) |  |
| `api/api/pipeline/ReviewDesk.java` | retry/5(18) |  |
| `api/api/pipeline/TranslationJob.java` | state/0(6) |  |
| `api/api/project/AppliedEdit.java` | toFinding/0(8) |  |
| `api/api/project/Narrator.java` | isSpecified/0(8) |  |
| `api/api/project/SegmentRecord.java` | withRejectedTarget/1(27) |  |
| `app/app/AppLifecycle.java` | completedSteps/0(8) |  |
| `app/app/BookLoomApplication.java` | lifecycle/0(8) |  |
| `app/app/OsKeepAwake.java` | afterPending/1(4) |  |
| `app/app/bootstrap/EvidenceAppender.java` | file/0(4), heldBytes/0(4) |  |
| `app/app/bootstrap/LoggingBootstrap.java` | configure/3(14) |  |
| `document/document/detect/CharsetLadder.java` | detect/1(-1) |  |
| `document/document/epub/EpubWriter.java` | write/3(15) |  |
| `document/document/epub/NavigationResources.java` | none/0(3) |  |
| `document/document/model/SkeletonAnchors.java` | writeBack/3(16) |  |
| `llm/llm/http/HttpErrorMapper.java` | map/3(4) |  |
| `llm/llm/knowledge/KnowledgeTables.java` | languageRow/1(9) |  |
| `llm/llm/profile/BuiltInRows.java` | rows/0(8) |  |
| `llm/llm/profile/GgufMetadata.java` | read/1(24) |  |
| `llm/llm/profile/PipelineTags.java` | kind/1(12) |  |
| `llm/llm/provider/ModelSettingsCache.java` | save/1(19) |  |
| `llm/llm/provider/RefusalMemory.java` | withoutRemembered/2(19) |  |
| `persistence/persistence/memory/InMemoryLexiconRepository.java` | record/3(18) | yes |
| `persistence/persistence/memory/InMemoryProjectRepository.java` | findByNameKey/2(15), findByPair/3(21) |  |
| `persistence/persistence/memory/InMemoryPromptFrameRepository.java` | all/1(12) |  |
| `persistence/persistence/memory/InMemorySegmentRepository.java` | byUnit/2(20), firstPending/2(20) |  |
| `persistence/persistence/memory/InMemoryTmRepository.java` | put/1(16) |  |
| `persistence/persistence/sqlite/SqliteDatabase.java` | dataSource/0(4) |  |
| `persistence/persistence/sqlite/SqliteLexiconRepository.java` | record/3(16) | yes |
| `persistence/persistence/sqlite/SqliteProjectRepository.java` | findByNameKey/2(13), findByPair/3(14) |  |
| `persistence/persistence/sqlite/SqlitePromptFrameRepository.java` | all/1(8) |  |
| `persistence/persistence/sqlite/SqliteSegmentRepository.java` | byUnit/2(7), firstPending/2(13) |  |
| `persistence/persistence/sqlite/SqliteTmRepository.java` | put/1(14) |  |
| `pipeline/pipeline/CallKindTotals.java` | cachedShare/0(10) |  |
| `pipeline/pipeline/RecoverySchedule.java` | delayBefore/2(10) |  |
| `pipeline/pipeline/RunSummaryLogger.java` | heldCallTimes/0(4) |  |
| `pipeline/pipeline/SegmentTranslator.java` | translate/1(3), translate/5(21), translateSplit/5(18) |  |
| `pipeline/pipeline/TranslationJobImpl.java` | state/0(5) |  |
| `pipeline/pipeline/WholeWord.java` | cached/0(4) |  |
| `pipeline/pipeline/audit/NameVariants.java` | find/3(12) |  |
| `pipeline/pipeline/batch/BatchDrafter.java` | draft/3(17) |  |
| `pipeline/pipeline/batch/BatchReply.java` | isClean/0(4) |  |
| `pipeline/pipeline/chunk/TokenEstimator.java` | outputRatio/2(11) |  |
| `pipeline/pipeline/context/CharacterSheet.java` | select/2(10) |  |
| `pipeline/pipeline/context/ContextBuilder.java` | plan/5(19) |  |
| `pipeline/pipeline/context/MapLines.java` | isEmpty/0(4) |  |
| `pipeline/pipeline/context/WindowMap.java` | lexiconLines/4(27) | yes |
| `pipeline/pipeline/export/BookExporter.java` | export/3(14) |  |
| `pipeline/pipeline/glossary/LexiconReading.java` | read/5(19), scan/5(24) | yes |
| `pipeline/pipeline/glossary/NameClusters.java` | of/2(12) |  |
| `pipeline/pipeline/glossary/NameHygiene.java` | rejection/3(12) | yes |
| `pipeline/pipeline/glossary/NamesPlan.java` | estimatedMinutes/0(10) |  |
| `pipeline/pipeline/glossary/PronounGender.java` | of/4(17) | yes |
| `pipeline/pipeline/glossary/ReadingVerdict.java` | decide/1(9) | yes |
| `pipeline/pipeline/glossary/StagedLexicon.java` | record/3(12) | yes |
| `pipeline/pipeline/glossary/SuggestTargets.java` | suggest/5(19) |  |
| `pipeline/pipeline/glossary/SuggestionReplies.java` | read/5(21) |  |
| `pipeline/pipeline/glossary/TransliterationCheck.java` | holds/3(-1) |  |
| `pipeline/pipeline/index/BookIndex.java` | sharing/3(33) |  |
| `pipeline/pipeline/lexicon/CooccurrenceLearner.java` | track/1(9) | yes |
| `pipeline/pipeline/memory/ProtectedSpans.java` | mask/4(17) |  |
| `pipeline/pipeline/project/ChapterBoundaries.java` | sameChapter/2(11) | yes |
| `pipeline/pipeline/project/OpenProjects.java` | hasIndex/1(10), put/2(15) |  |
| `pipeline/pipeline/prompt/DraftReplyParser.java` | parse/2(11), parseRepair/3(13) |  |
| `pipeline/pipeline/prompt/GateNotes.java` | describe/4(13) |  |
| `pipeline/pipeline/prompt/OutputLimit.java` | forSource/3(-1) |  |
| `pipeline/pipeline/prompt/PromptHygiene.java` | cleanText/1(-1) |  |
| `pipeline/pipeline/prompt/PromptTemplates.java` | renderSystem/2(8) |  |
| `pipeline/pipeline/prompt/StepAnnouncer.java` | endOpen/3(11) |  |
| `pipeline/pipeline/proofread/DeferralRegister.java` | termChanged/3(13) |  |
| `pipeline/pipeline/qa/QaEvaluator.java` | evaluate/2(14) |  |
| `pipeline/pipeline/review/RetryDraft.java` | retry/5(21) |  |
| `pipeline/pipeline/run/PromptRequests.java` | batchRequest/1(4), draftRequest/3(4), formatReaskRequest/4(5), repairRequest/4(8), repairRequest/5(9) |  |
| `pipeline/pipeline/run/RoutedCalls.java` | heldPauseCounts/0(8) |  |
| `pipeline/pipeline/typography/Normalisation.java` | fired/1(15) |  |
| `pipeline/pipeline/typography/TypographyGate.java` | around/2(10) |  |
| `pipeline/pipeline/typography/TypographyNormalizer.java` | normalise/2(11) |  |
| `ui/ui/control/ScrollStats.java` | detachedInWindow/0(3) |  |
| `ui/ui/control/SearchableCombo.java` | getInvalidText/0(-1), parsed/3(-1), strict/2(4) |  |
| `ui/ui/control/StateMarker.java` | of/2(12) |  |
| `ui/ui/control/StepFooter.java` | withUnavailableForward/2(10) |  |
| `ui/ui/control/TwoColumns.java` | isStacked/0(8), sideBySideFrom/0(8) |  |
| `ui/ui/dialog/ExportCompleteDialog.java` | show/1(10) |  |
| `ui/ui/screen/FormGrid.java` | isStacked/0(8) |  |
| `ui/ui/screen/QuantChip.java` | of/2(6) |  |
| `ui/ui/state/Activity.java` | eta/0(-1) |  |
| `ui/ui/state/ActivityLogFeed.java` | heldSegments/0(8) |  |
| `ui/ui/state/ActivityTracker.java` | blocking/0(4), progress/3(18) |  |
| `ui/ui/state/AutomationSettings.java` | loadedProperty/0(4) |  |
| `ui/ui/state/DetailedLogSwitch.java` | loadedProperty/0(4) |  |
| `ui/ui/state/ExportViewModel.java` | destinationExists/0(4), refresh/0(5) |  |
| `ui/ui/state/HiddenWork.java` | screen/0(-1) |  |
| `ui/ui/state/LogEntry.java` | wording/0(8) |  |
| `ui/ui/state/ModelNameFilter.java` | of/1(10) |  |
| `ui/ui/state/PauseNotice.java` | isLastPause/0(8) |  |
| `ui/ui/state/PendingLines.java` | size/0(8) |  |
| `ui/ui/state/ProgressLedger.java` | rebuilt/4(35) |  |
| `ui/ui/state/ProviderForm.java` | useThis/1(5) |  |
| `ui/ui/state/ReviewSection.java` | pauseNotice/0(-1) |  |
| `ui/ui/state/ReviewViewModel.java` | acceptAvailable/0(4), open/0(14) |  |
| `ui/ui/state/RunInterventions.java` | sendAgain/0(10) |  |
| `ui/ui/state/SettingsViewModel.java` | testSubject/0(4) |  |
| `ui/ui/state/SetupHelper.java` | run/3(-1), runForDecision/3(-1) |  |
| `ui/ui/state/StateMirror.java` | publishOutcome/3(10), publishRunStarted/2(12) |  |
| `ui/ui/state/StyleSuggestion.java` | apply/0(9), isNarratorGenderSuggested/1(15), pending/0(-1) |  |
| `ui/ui/state/TestDetails.java` | of/7(22) |  |
| `ui/ui/state/TranslatingViewModel.java` | pendingRemain/0(8), preparing/0(8) |  |

## Appendix C — record components whose accessor is never called from main code and that the record itself does not use

`(T)` = read by tests only. Heuristic on top of the bytecode result (the component name must not occur in the record body outside null checks), so every row is **check**: a record written to JSON (stored documents, `llm/dto`) is read by Jackson, not by an accessor call. `llm/dto` rows are left out for that reason.

| File | Record | Components |
|---|---|---|
| `api/api/document/Document.java` | `Document` | charset(T) |
| `api/api/llm/LanguageDeclaration.java` | `LanguageDeclaration` | source |
| `api/api/llm/ModelFacts.java` | `ModelFacts` | compatibility(T) parser(T) state(T) |
| `api/api/llm/ModelInput.java` | `ModelInput` | dynamic(T) quant(T) raw |
| `api/api/llm/ProbeVerdicts.java` | `ProbeVerdicts$Verdict` | source(T) |
| `api/api/pipeline/BatchStarted.java` | `BatchStarted` | kind |
| `api/api/pipeline/BookPlan.java` | `BookPlan` | chunksPerUnit(T) |
| `api/api/pipeline/BookRelocation.java` | `BookRelocation$Relocated` | projects(T) |
| `api/api/pipeline/CallSegment.java` | `CallSegment` | currentTarget(T) |
| `api/api/pipeline/ChunkPosition.java` | `ChunkPosition` | chunks sections(T) |
| `api/api/pipeline/ContextAssembled.java` | `ContextAssembled` | context(T) segmentId(T) |
| `api/api/pipeline/Finished.java` | `Finished` | report(T) |
| `api/api/pipeline/JobReport.java` | `JobReport` | format(T) |
| `api/api/pipeline/ModelCallFinished.java` | `ModelCallFinished` | outputChars(T) |
| `api/api/pipeline/ModelCallStarted.java` | `ModelCallStarted` | request(T) |
| `api/api/pipeline/NamesEstimate.java` | `NamesEstimate` | callsByKind |
| `api/api/pipeline/ProjectChange.java` | `ProjectChange$NameInUse` | holder(T) |
| `api/api/pipeline/RecoveryWaiting.java` | `RecoveryWaiting` | progress |
| `api/api/pipeline/SegmentDrafted.java` | `SegmentDrafted` | confidence |
| `api/api/pipeline/SegmentView.java` | `SegmentView` | kind(T) reviewed(T) |
| `api/api/project/ExportRecord.java` | `ExportRecord` | destination(T) exportedAt(T) sideFiles(T) |
| `api/api/project/SegmentRecord.java` | `SegmentRecord` | confidence(T) |
| `api/api/project/SnapshotRendering.java` | `SnapshotRendering` | rendering term |
| `api/api/project/SnapshotTerm.java` | `SnapshotTerm` | locked suggested target term type |
| `api/api/project/SnapshotTmHit.java` | `SnapshotTmHit` | kind source target |
| `document/document/epub/SpineItem.java` | `SpineItem` | idref |
| `llm/llm/knowledge/QuantRow.java` | `QuantRow` | confidence family needsImatrix source |
| `llm/llm/profile/GgufIdentity.java` | `GgufIdentity` | architecture(T) baseModels(T) |
| `llm/llm/profile/NameFacts.java` | `NameFacts` | ctx(T) date(T) dateEight(T) dwq(T) dynamic(T) expertB(T) experts(T) finetune(T) finetuner(T) format(T) imatrix(T) impliedFormat(T) instance(T) monthDay(T) namespace(T) qat(T) quant(T) quantExtra(T) quantType(T) release(T) shard(T) sidecar(T) spec(T) unquantized(T) unspecified(T) |
| `llm/llm/profile/NameOrigin.java` | `NameOrigin` | alias(T) file(T) inputForm(T) quantizer(T) revision(T) routerVariants(T) serverAlias(T) subfolder(T) |
| `llm/llm/profile/ParsedName.java` | `ParsedName` | facts(T) |
| `pipeline/pipeline/chunk/Chunk.java` | `Chunk` | unitId(T) |
| `pipeline/pipeline/context/Block.java` | `Block` | stability(T) |
| `pipeline/pipeline/context/RunFrame.java` | `RunFrame` | bookFacts ruleFacts styleSheet |
| `pipeline/pipeline/glossary/TermReading.java` | `TermReading$WindowReading` | claimed(T) |
| `pipeline/pipeline/index/Posting.java` | `Posting` | capitalised offset sentence sentenceInitial unitId |
| `pipeline/pipeline/index/SegmentInfo.java` | `SegmentInfo` | ordinal |
| `pipeline/pipeline/index/Window.java` | `Window` | lastOrdinal(T) |
| `pipeline/pipeline/index/WordStats.java` | `WordStats` | firstOrdinal(T) lowerCase(T) segments(T) sentenceInitial(T) standalone(T) total(T) |
| `pipeline/pipeline/qa/CheckResult.java` | `CheckResult` | skipped(T) |
| `pipeline/pipeline/review/ReviewQueries.java` | `ReviewQueries$Reading` | locators sources |
| `pipeline/pipeline/run/StoryUnits.java` | `StoryUnits` | ids |
| `ui/ui/ShellRunParts.java` | `ShellRunParts` | runBanner |
| `ui/ui/state/EstimateSources.java` | `EstimateSources` | profiles |
| `ui/ui/state/ExportStorage.java` | `ExportStorage` | stored |
| `ui/ui/state/ImportState.java` | `ImportState$AlreadyInProjects` | source |
| `ui/ui/state/ImportState.java` | `ImportState$NoText` | fileName |
| `ui/ui/state/ImportState.java` | `ImportState$Opening` | fileName |
| `ui/ui/state/PauseNotice.java` | `PauseNotice` | locator |
| `ui/ui/state/PendingSuggestion.java` | `PendingSuggestion` | basis |
| `ui/ui/state/ProviderRow.java` | `ProviderRow` | endpoint engine kind |
| `ui/ui/state/ResultOffer.java` | `ResultOffer` | label(T) |
| `ui/ui/state/ReviewRow.java` | `ReviewRow` | keptAsSource(T) |
| `ui/ui/state/StructureSegments.java` | `StructureSegments$Failed` | code |
| `ui/ui/state/WaitingCall.java` | `WaitingCall` | attempt maxAttempts stuck(T) |

## Appendix D — `MessageKey` constants with no reference (7)

`ACTIVITY_DETAIL_SEGMENTS` (`activity.detail.segments`), `SETTINGS_MODEL_LABEL` (`settings.model.label`), `EXPORT_BOOK_TITLE` (`export.bookTitle`), `EXPORT_BOOK_AUTHOR` (`export.bookAuthor`), `REVIEW_RETRY` (`review.retry`), `DIALOG_SAME_PAIR_KEEP` (`dialog.samePair.keep`), `LIVE_MODE` (`live.mode`)
