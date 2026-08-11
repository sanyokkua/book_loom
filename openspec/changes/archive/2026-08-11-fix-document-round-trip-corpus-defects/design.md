## Context

See `proposal.md` — Why. The evidence, with a minimal hand-authorable repro per defect, is
`docs/implementation_plan/notes-corpus-verification.md`.

Three constraints shape everything below.

**The parser choice is load-bearing and already argued.** Change 3's `design.md` D4 chose jsoup for
EPUB XHTML specifically because it resolves named HTML entities with no DTD fetch, and 53 surveyed
books use `&nbsp;` under an XHTML 1.1 DOCTYPE. An XML parser cannot resolve those without loading a
DTD, and loading one over the network would break the offline invariant. Any change to the parser
has to keep that true.

**Two comparators are the only thing standing between a fix and a silent regression**, and both are
implicated in this change. `EpubCanonicalAssert` re-parses both sides with jsoup, so a fix that
changes how a source parses changes what "the source" means to the comparator. `TextCoverage` is the
only assertion that checks something *happened* rather than that nothing broke, and it currently
cannot measure two of the four formats.

**Real books never enter the repository** (change 3 `design.md` D6, change 4 `design.md` D8). The
corpus is 314 MB of third-party copyrighted material, git-ignored. Every defect must be reproduced
hand-authored into a `@TempDir` fixture, and the corpus verification must be able to find nothing at
all and still leave the build green.

## Goals / Non-Goals

**Goals:**

- Every one of the twelve zero-segment books imports with segments, and no book that round-tripped
  before regresses — both proven by re-running the same measurement that found the defects.
- A zero-edit write is a fixed point, checkable without a comparator.
- Each defect gains a fixture carrying its shape, so the gate catches it again.
- The coverage metric measures all four formats on comparable terms.

**Non-Goals:**

- **No masking.** `masked` stays equal to `sourceInner` and `placeholders` empty. Deciding how a
  literal `&` in model output should be *escaped* is change 5's; this change only fixes how the
  resulting failure is *classified*.
- **No reader/writer acceptance parity.** That an EPUB can open and never be exported (no `mimetype`
  entry) is real and recorded in `CHANGE_BACKLOG.md`, not fixed here — it needs a product decision
  about failing fast at open versus synthesizing a container entry on write.
- **No document lifetime seam.** No `close()`/eviction on the open-document registries; backlog.
- **No chunker work.** The 26,306-character single segment is correct output from `:document` and a
  problem for `:pipeline`'s sentence-split-on-overflow; backlog.

## Decisions

### D1 — Repair self-closed raw-text elements before parsing, rather than parsing as XML first

**Chosen:** normalize `<script/>`, `<style/>` and `<noscript/>` to an explicit open/close pair in the
byte stream before handing the document to jsoup.

**Alternative considered and rejected: parse as XML, fall back to jsoup on failure.** The obvious
objection to this — that 53 books need `&nbsp;` resolved without a DTD — turns out **not** to apply
to the affected books, which use only `&amp;`, a predefined XML entity. So the fallback would work.
It is rejected on different grounds: it changes the parser for all 194 EPUBs to fix 12, it makes the
parse path taken depend on document content (so a book can silently move between two parsers with
different DOM shapes and different entity handling), and it requires the fallback path itself to be
gated and tested as thoroughly as the primary. The pre-parse repair changes one function, is
independent of document content, and leaves every book that works today on exactly the path it is on
today.

**The tag list is the measured set, not the HTML category.** Measured on jsoup 1.23.1, a self-closed
`<script/>`, `<style/>` or `<noscript/>` in `<head>` collapses `body.children()` to 0; `<title/>` and
`<textarea/>` parse correctly. 21 surveyed books carry a self-closed `<title/>` and are healthy.
Repairing tags that are not broken would be inert but would also misstate the mechanism to the next
reader, and the fixture would then be testing something the code does not need to do.

This revises change 3's `design.md` D4, which is why it needs **ADR-0028** (ADR-0027 is the highest
in use). The ADR records that jsoup remains the EPUB XHTML parser and states the one pre-parse
normalization it now requires, so the entity-resolution argument that chose jsoup is preserved intact.

### D2 — The comparator normalizes both sides identically; it is never relaxed

After D1 the skeleton holds `<script …></script>` where the source held `<script …/>`. That is
permitted by DD-43 — canonical-equal, not byte-equal — but `EpubCanonicalAssert` re-parses **both**
sides with jsoup, so without a change the source side is still swallowed and the two do not compare
equal. The comparator therefore applies the same pre-parse normalization to both sides before
canonicalizing.

**This is the one dangerous edit in the change,** because a comparator that has been taught to ignore
a difference can no longer catch it. Two guards: `GoldenComparisonMetaTest` gains a case proving the
comparator still fails on a real difference *after* the normalization is added, and the corpus
verification is re-run to confirm the fix on books the comparator never sees.

**Explicitly rejected:** comparing production's output against a single-parse canonicalization of the
source. This was proposed during investigation as a way to make seven failing books pass. It would
have hidden a real defect: measured with production's own parse and serialize and no comparator
involved at all, writing a book and then writing that output produces different bytes. The write is
not a fixed point, and the comparator is right to say so. **F12b is fixed in the writer; the
comparator is not touched for it.**

### D2a — The comparator has a structural blind spot, and it is not fixable

**A canonical comparator that re-parses both sides cannot detect any transformation the parser applies identically
to both sides.** `<p><div>x</div></p>` un-nests to `<p /><div>x</div><p />` on the *first* parse — a real structural
change against the source bytes — but it happens on the expected side and the actual side alike, so it cancels and
`EpubCanonicalAssert` reports equal.

This is not a defect in the comparator and no change here fixes it; it is a structural limit of DD-43's
re-parse-and-compare method. It is recorded because it is the reason D3 exists as an independent check rather than
as a restatement of D2, and because the next person to find a fidelity bug invisible to the golden tests should
recognise the shape rather than rediscover it. Any defect where **the source parse itself** is lossy — not just the
write path — needs a fixed-point probe, a production-parse-only probe, or a comparison against the original bytes.

### D2b — F12a and F12b are parse-time and output-syntax defects, not writer-logic defects

Naming the fix site precisely, because the obvious reading sends an implementer to the wrong file.

`XhtmlParser` parses with jsoup's HTML tree builder and then sets `syntax(xml)` on the output settings
(`XhtmlParser.java:49`). `EpubWriter` never parses and never chooses a syntax — it calls `outerHtml()` on the tree it
was handed (`EpubWriter.java:185`). So:

- **F12a — a self-closed `<a …/>` duplicating its id and absorbing a heading** happens during `Jsoup.parse`. The
  writer cannot repair a tree that was already wrong when it received it. This belongs with D1's pre-parse
  normalization, extended from the three raw-text elements to self-closed **non-void** elements generally.
- **F12b — the write not being a fixed point** comes from the XML output syntax emitting `<p />` for an empty
  non-void element, which the HTML parser then does not treat as closed on the next read. The fix is in how the tree
  is serialized, which is also configured in `XhtmlParser`.

Neither is a change to `EpubWriter`'s write-back logic. **Do not switch the output syntax wholesale** — `syntax(html)`
would emit `<img>` rather than `<img/>` and break XHTML well-formedness. The requirement is narrower: a void element
still self-closes, a non-void element never does.

### D3 — A fixed-point assertion, not just a comparator assertion

The skeleton-preservation obligation is expressed as *write twice, compare the two outputs*. This is
strictly stronger than comparing one output against the source, needs no canonicalization to state,
and is immune to the failure mode in D2 — a comparator taught to ignore a difference still cannot
make two successive writes agree if they do not.

### D4 — Detection prefers a coherent decode, and the preference is evidence-based

The charset ladder's detection rung currently accepts the detector's top candidate. Every single-byte
encoding decodes every byte, so "it decoded" is no evidence at all — three surveyed books resolve as
`windows-1252` and render Russian as `Ñïàñèáî`. The fix constrains the choice among candidates that
all decode successfully, preferring one whose decoded text is coherent in a known script.

**The settled test — measured against the pinned `com.ibm.icu:icu4j:77.1` and against real corpus
files, not the literal proposal below.** Two earlier formulations were tried and rejected.

The first — decode only the byte positions that differ between candidates, and prefer the candidate
under which they fall predominantly within a single Unicode *block* — does not discriminate. Measured
on the corpus case (2,000 chars ASCII French + a 195-byte `windows-1251` Cyrillic line), both
`ISO-8859-1` and `windows-1251` score **1.000** on that metric, because `windows-1251`'s Cyrillic range
`0xC0–0xFF` maps under Latin-1 to `U+00C0–U+00FF`, which is entirely the single block *Latin-1
Supplement*. The byte positions are identical under every candidate, so only the *identity* of the
decoded characters — not their block — can discriminate.

The second — score and re-rank the single-byte candidates ICU already lists over the whole file —
discriminates correctly on every synthetic fixture but **fails all three real corpus books it targets**,
because ICU's whole-file `detectAll()` does not list `windows-1251` at all for them: one 1.4 MB book
carries 22,298 non-ASCII bytes, of which ~22,100 are smart-punctuation bytes (curly quotes, en dash,
ellipsis, guillemets) that decode to the *same* code point under every Western European single-byte
candidate and therefore vote for none of them, and only ~195 are the Cyrillic region — swamped roughly
114 to 1. No re-ranking of a list that never contains the right answer can reach it.

The settled test therefore **detects over the distinguishing region itself, not over the whole file**:
find the file's maximal runs of two or more consecutive bytes `>= 0x80` whose immediate neighbouring
byte is not an ASCII letter (a "word" written entirely outside ASCII); if there are none, the
preference does not fire and ICU's whole-file answer stands. Otherwise, concatenate those runs
(space-separated) and run ICU's detector over that region alone — measured on the real corpus, this
lists `windows-1251` at confidence 37, top of the list, where whole-file detection listed it not at
all. Build the candidate order as the region detection's single-byte candidates first (in its own
order — single-byte identified exactly by
`charset.canEncode() && charset.newEncoder().maxBytesPerChar() == 1.0f`), then any whole-file
single-byte candidates not already present; score each by the fraction of the foreign-word runs that
decode, under it, to characters that are all letters and all of one `Character.UnicodeScript` that is
not Latin; take the first candidate in that order achieving the maximal score, so a tie (a wholly
Cyrillic file scores both `windows-1251` and `KOI8-R` at 1.000) resolves to whichever detection already
ranked first rather than to an arbitrary one. A maximal score of zero leaves ICU's whole-file answer
untouched.

**Deliberately narrow.** It applies only where detection is reached — a byte-order mark or an in-band
declaration still outranks it, unchanged — and only to choosing among single-byte candidates. It must
not become a general "guess the language" mechanism; that is
`add-metadata-units-and-language-detection`'s job and this change must not pre-empt it.

**Verified against the real corpus, not just the synthetic fixture.** The corpus case is Western
European prose whose only Cyrillic bytes are a small run of site boilerplate; a whole-file heuristic is
dominated by the majority language, and — as measured above — can even be blind to the correct
candidate entirely once real-world smart punctuation is mixed in. Region detection over the
foreign-word runs is what recovers `windows-1251` for all three affected real books, while leaving a
genuinely Western European book (no foreign-word runs) and a wholly Cyrillic book (already correct)
unchanged. The fixture set must reproduce both the synthetic shape (8.2, 8.3) and this real shape —
mostly-ASCII prose carrying both non-distinguishing smart punctuation *and* a small foreign-script
region — or the difference between the rejected whole-file re-rank and the settled region-detection
test is never actually exercised.

### D5 — The coverage metric is made symmetric, and the floors are re-derived afterwards

Two independent faults: the numerator keeps raw Markdown syntax while the denominator is
markup-stripped, and the TXT denominator is decoded as UTF-8 regardless of the resolved charset.
Both fabricate a shortfall.

The consequence is the reason this is not a test-tidiness nit: the declared Markdown floor is `0.90`
and real Markdown files measure `0.853`–`0.956`, so the floor was calibrated against a metric that
understates. **After the metric is corrected, every per-format floor in `FixtureCatalog` must be
re-derived and raised to what a correct metric actually reports** — otherwise the change fixes the
measurement and leaves the slack that made it useless.

### D6 — The corpus verification is committed, tagged, and environment-gated

This reverses the throwaway decision the verification effort itself made. It is reversed because the
same measurement is now the acceptance evidence for nine fixes, and a measurement that has to be
rebuilt from scratch will not be re-run.

It follows the pattern `04_Build_and_Release/06_TESTING_STRATEGY.md` already sets for `liveLocal`,
`promptEval` and `visual`: its own JUnit tag, its own registered Gradle task, **not** wired into
`check`, excluded from CI and from coverage, and skipped via an environment-variable guard so a
checkout with no corpus is green without it.

It **records** rather than asserts. A verification that stops at the first bad book cannot answer
"did anything regress", which is the question it exists to answer.

**What it measures, per book.** The previous harness was deleted, so this is a specification, not a
description of something to be found in git history:

| Probe | What it does | What it records |
|---|---|---|
| **P0 open** | `open` the book | ok/error code, format, charset, byte-order mark, declared language, unit and segment counts, segment-kind histogram, min/median/p95/max segment length, count of empty or duplicate-id segments, elapsed time |
| **P1 identity** | `write` with zero edits | write ok/error, and that format's canonical comparison against the source |
| **P2 fixed point** | `write` P1's output again with zero edits | whether the second output is canonical-equal to the first |
| **P3 mutation** | set `targetInner` on **every** segment, `write`, then re-`open` the output | write ok/error, unit and segment counts against P0, whether the ordered `(unit order, segment id, kind, anchor)` tuples equal P0's, and the marker-strip result below |
| **P4 idempotence** | re-`open` P1's output | whether its tuples and `sourceInner` values equal P0's |
| **P5 resource** | — | wall-clock and file size per book |

**The mutation and its check — the load-bearing part.** P3 sets each segment's `targetInner` to a marker
prefix concatenated with that segment's own `sourceInner`. After writing and re-opening, the check is
that stripping the marker prefix from each re-opened segment's `sourceInner` yields exactly the
original `sourceInner` for the same segment id. This is deliberately self-verifying: it proves a text
edit survives write → read losslessly through whatever real inline markup the book contains, and it
needs no comparator to say so. Use a marker that cannot occur in book text and is representable in
every charset the corpus uses — the original run used `⟪T⟫`, which also exercises `TxtWriter`'s
unrepresentable-character refusal on non-UTF-8 sources, so a `validation` refusal there is a correct
outcome and must be recorded as such rather than as a failure.

**The report.** One JSON object per book per line, written to a path given by a second environment
variable, outside the repository. Keys stable and greppable so two runs can be diffed mechanically.
A summary table beside it with one row per book. Flush after each book so a crash still leaves a
partial report.

**Five mechanical traps, all hit during the original run.** Each produced a confident wrong result:

- Gradle does not treat environment variables as task inputs, so a re-run reports `up-to-date` and
  silently does nothing while printing `BUILD SUCCESSFUL`. Declare the corpus directory as a task
  input via a `providers.environmentVariable(...)` provider, or mark the task never up-to-date. The
  codebase has no existing example of this — `liveLocal` and its siblings never needed it — so it must
  be written, not copied. The evidence of a run is its report file, never its exit code.
- The test JVM's working directory is the module, not the repository root. Resolve the corpus
  directory to an absolute path; a relative one throws `NoSuchFileException`.
- A probe must not change the output file name. For Markdown, TXT and FB2 the unit href *is* the file
  name and segment ids derive from it, so writing to a renamed output renames every segment and makes
  every comparison fail on books that are perfectly fine. Each probe writes into its own directory
  under the original name.
- Diff canonical forms, not raw bytes. jsoup rewrites an XML prolog to a comment, so a raw-byte diff
  reports a difference on nearly every EPUB — permitted under DD-43 and meaningless. Record raw-byte
  equality and canonical equality as separate fields. Note that `Fb2CanonicalAssert.xmlOf` returns
  **raw** decoded XML; canonicalization is a different step.
- `EpubWriter` mutates the tree held live in its registry (F9, deferred and not fixed here), so every
  write probe must open a fresh service and a fresh document. Reusing another probe's registry entry
  makes each mutation compound the last.

### D6a — What the re-run is compared against, and what "no regression" can honestly mean

The corpus is git-ignored and machine-local, so there is no committed per-book baseline and there
cannot be one. Two consequences, both stated rather than worked around:

- **The committed anchor is the aggregate.** `notes-corpus-verification.md` records the pre-fix
  counts — 212 of 214 books open, 201 identity round trips pass with 10 failures and 1 correct write
  refusal, 209 of 212 idempotence checks pass, 12 books yield zero segments — and names every
  defective book. That is enough to state the expected post-fix numbers exactly, which task 12.1 does.
- **The machine-local baseline is the diff target.** The run that produced those counts is preserved
  at `.temporary_context/corpus-verification-baseline/` (git-ignored, alongside the corpus it
  describes). Where it is present, the re-run is diffed against it per book. Where it is not — a fresh
  clone, another machine — the comparison falls back to the committed aggregate, and the task says so
  instead of pretending to a precision it does not have.

**One trap in reading the re-run, which the aggregate alone hides.** All 12 zero-segment books
currently *pass* the identity and idempotence checks — trivially, because with no segments there is
nothing to write back. Fixing D1 gives them real content to round-trip for the first time, so they may
newly fail. **That is a newly exposed defect, not a regression caused by the fix**, and it must not be
read as a reason to revert. The re-run therefore has to compare like with like: a book that passed
while empty has not "regressed" when it fails while full, and a book counted as passing must also be
counted as having produced segments.

### D7 — One fixture per defect, each carrying the shape that actually breaks

Change 4's `design.md` D8 governs: hand-authored Java builders writing real files into a `@TempDir`,
registered in `FixtureCatalog`, never committed binaries. Two fixtures need a specific trap avoided,
because the obvious version of each passes whether or not the bug is fixed:

- **`<pre>`**: needs **two or more** leading line breaks. With one, both sides discard it alike and
  the fixture is green against the broken code.
- **Charset**: needs a mostly-ASCII file with a small non-Latin region. A wholly-Cyrillic file is
  detected correctly today.

## Risks / Trade-offs

- **A comparator taught to ignore a difference stops catching it (D2).** → `GoldenComparisonMetaTest`
  gains a case proving it still fails on a real difference after the normalization; the corpus
  verification re-run is the independent check.
- **Pre-parse byte manipulation could corrupt a document it should not touch (D1).** → The
  normalization is confined to three tag names in self-closing form; a fixture asserts a document
  already using the paired form is byte-unchanged, and one asserts the literal text `<script/>`
  inside a code listing is not rewritten.
- **Raising the coverage floors (D5) could turn a real pre-existing shortfall into a new failure.**
  → That is the intended outcome, but it must be attributed: re-derive the floors before changing
  the walker, so any newly-failing fixture is a discovery rather than a regression this change caused.
- **The charset preference could mis-resolve a file that is genuinely Western European (D4).** → It
  only ranks candidates that all decode; a fixture asserts a French-only file still resolves to its
  Western European encoding.
- **The corpus verification is not a merge gate, so it can rot.** → Accepted, and unavoidable: the
  corpus cannot be in the repository. Mitigated by it being committed rather than rebuilt, and by
  `AGENTS.md`'s existing convention that a change touching the parser or writer re-runs it.
- **Nine task groups is a large change.** → Each is independent and separately verifiable; the
  ordering below puts the two that touch shared machinery (the comparator, the coverage metric)
  before the fixes that depend on them.

## Migration Plan

No data migration, no schema change, no API change. Ordering matters only in that the comparator
normalization (D2) must land with or before the parser repair (D1), or the twelve books are fixed and
the golden test fails; and the coverage metric (D5) must be corrected before its floors are re-derived.

Rollback is per task group — each is one commit on its own branch, and no group depends on another's
runtime behaviour.

## Open Questions

- Whether the corpus verification's tag should be a fourth registered Gradle task or reuse the
  existing `liveLocal` task. This changes no requirement, no fixture and no fix, and can be settled
  when the task is wired.
