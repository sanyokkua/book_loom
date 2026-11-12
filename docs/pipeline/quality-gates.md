# Quality gates

Every deterministic check a translated segment passes through, what level each finding has, how the never-worse gate
decides whether an edit may stay, what export validates, and the limits the owner accepted.

Design: `openspec/changes/redesign-translation-pipeline/design.md` D12–D16; decision:
[ADR-0046](../spec_baseline/adr/ADR-0046-three-stages-one-repair-never-worse.md). Classes are under
`modules/pipeline/src/main/java/ua/bookloom/pipeline/` unless stated. No check calls a model: there is no judge model at
run time.

## Order of work on a reply

1. **Placeholder hard gate** (`:document` `mask/PlaceholderGate`, read through `heal/DocumentGate`): the multiset of
   `⟦gN⟧` tokens must equal the masked source's; paired tokens must open before they close, nest, and keep their text.
   A bracket glyph outside a whole token fails first. The gate reports and never repairs; a failing target restores
   nothing.
2. **Protected spans** (`memory/ProtectedGate`, `memory/RestoredCheck`): each locked-term and kept-foreign-run token must
   come back exactly once.
3. **Code repairs before any check counts**: lost formatting marks restored, control codes mapped
   (`ControlCharacterMapper`), typography and stress marks normalised (`typography/TypographyNormalizer`,
   `typography/StressStrip`), heading numbers and quotes repaired. These cost no call.
4. **Checks** (`qa/QaEvaluator`): hard gates, five weighted soft checks, the deterministic text checks
   (`checks/TextChecks`, turned into gate results by `qa/TextCheckGates`).
5. **Decision** (`heal/AcceptanceRule`), the one repair (`heal/OneRepair`) and the never-worse gate (`qa/NeverWorse`);
   see [translation-pipeline.md](translation-pipeline.md#acceptance-the-one-repair-and-the-never-worse-gate).

## Levels

A finding's level is derived each time it is read, never stored (`qa/CheckLevels.of`), so the run, the audit, export and
the review desk agree on what blocks:

| Level | Effect on the segment |
|---|---|
| **BLOCK** | cannot be accepted while it stays; earns the one repair; FLAGGED if it survives |
| **REPAIR** | may be accepted; earns the one repair; if it stays, the segment carries a proofread mark |
| **NOTE** | accepted with an audit mark; earns no call |

A high-severity finding has its check's level (the table below). A medium-severity finding is REPAIR, except that a
weighted soft check that fails outright blocks. A low-severity finding is a NOTE. A finding no check raised is a NOTE.
Severity only orders what the person sees. The soft-check weights blend into a confidence figure (`qa/Confidence`) that
orders segments for review; acceptance uses no score and no threshold.

## Check catalogue

From `qa/CheckName` (the one level table). "Wire name" is the `raisedBy` value stored on a `QaFinding`.

**Hard gates** (no weight):

| Check | Wire name | Level | Catches |
|---|---|---|---|
| `REFUSAL` | `refusal` | BLOCK | an empty target, or one that opens with a refusal phrase |
| `PLACEHOLDER` | `placeholder` | BLOCK | token multiset, pair order or nesting broken |
| `LOCKED_TERM` | `locked-term` | BLOCK | a locked term's protected span not back exactly once |
| `KEPT_RUN` | `kept-run` | BLOCK | a kept foreign run's protected span not back exactly once |

**Soft checks** (weighted into confidence; an outright failure blocks):

| Check | Wire name | Weight | Catches |
|---|---|---|---|
| `SCRIPT` | `script` | 0.20 | letters not in the target language's script |
| `ECHO` | `echo` | 0.15 | the source copied back untranslated |
| `REPETITION` | `repetition` | 0.10 | a decode-loop repetition |
| `LENGTH` | `length` | 0.25 | length outside the pair's expected band |
| `GLOSSARY` | `glossary` | 0.30 | a locked glossary term not rendered as entered |

**Text checks**:

| Check | Wire name | Level | Catches |
|---|---|---|---|
| `SCRIPT_PURITY` | `script-purity` | BLOCK | a word mixing the target script with another script or a digit |
| `ALPHABET` | `alphabet` | BLOCK | a letter outside the target alphabet (for Ukrainian: ы ъ э ё) |
| `LATIN_RUN` | `latin-run` | BLOCK | a run of source Latin words left in a target of another script |
| `LANGUAGE_IDENTITY` | `language-identity` | BLOCK | a paragraph of at least 12 words still in the source language |
| `SENTENCE_COUNT` | `sentence-count` | BLOCK | a significant source sentence with no counterpart |
| `QUOTE_BALANCE` | `quote-balance` | BLOCK | a quote pair the source balanced left open, stray or crossed |
| `PROTOCOL_LEAK` | `protocol-leak` | BLOCK | reply structure (braces, a wrapper label) left in the text |
| `IDENTIFIER` | `identifier` | BLOCK | a UUID, hex string, ISBN, URL or e-mail changed or lost |
| `NUMBER` | `number` | BLOCK (a lost two-digit number is REPAIR, an extra one NOTE) | a digit number changed or lost |
| `NAME_SWAP` | `name-swap` | BLOCK | a source glossary name replaced by another glossary name |
| `VOCATIVE` | `vocative` | BLOCK | a name the source calls out lost in the target |
| `NAME_SPELLING` | `name-spelling` | BLOCK for a pinned name, NOTE for a suggested one | a glossary name spelled another way |
| `NAME_MISSING` | `name-missing` | REPAIR | a source glossary name lost |
| `FUNCTION_WORD_RESIDUE` | `function-word-residue` | REPAIR | function words only the source language has, left in the target |
| `GENDER` | `gender` | REPAIR | a past-tense form after the narrator's «я» of the wrong gender |
| `NAME_GENDER` | `name-gender` | REPAIR | a woman's name with a masculine verb or declension |
| `ADDRESS_MIX` | `address-mix` | REPAIR | one speech mixing informal and formal address for one listener |
| `ADDRESS_PARITY` | `address-parity` | REPAIR | a speech whose form of address changed between informal and formal |
| `TERMINAL_MARK` | `terminal-mark` | REPAIR | a paragraph ending in another class of mark than its source |
| `DUPLICATE_WORD` | `duplicate-word` | NOTE | the same word twice in a row |
| `SPACING` | `spacing` | NOTE | doubled or misplaced spaces |
| `UNKNOWN_WORD` | `unknown-word` | NOTE | a word the word validator doubts |
| `TYPOGRAPHY` | `normalised` | NOTE | the typography pass changed the target |

The checks are generic per language pair: alphabets, forbidden letters, function words, pronouns and quote conventions
come from the bundled language files ([context-glossary-memory.md](context-glossary-memory.md#language-data)). Text
checks are skipped for a kept foreign passage. Only `USER` and `BOOK` glossary values are enforced; a `LIKELY` value is
never checked against.

## Never-worse gate

`qa/NeverWorse.judge(current, candidate, facts)` decides every text the program changes after its draft: the one repair
and every Proofread rewrite. It answers `Keep` or `Reject(reason)`.

**Vetoes** (`qa/NeverWorseVetoes`, checked in fixed order; the first broken one names the rejection):

1. the placeholder gate fails;
2. a new BLOCK finding appears;
3. text vetoes: a forbidden letter, more source-only function words, a name form outside a pinned or locked target's
   forms, a map name dropped, fewer sentences, a lost negation, a lost number, fewer quotes, dashes or clause marks, a
   new Latin run, a stress mark, a doubled word, a lower-case sentence start, a new mid-sentence capital;
4. the finding count rises for `alphabet`, `language-identity`, `function-word-residue`, `script-purity`, `number`,
   `terminal-mark`, `address-parity` or `sentence-count`.

Counted vetoes compare against the current text **and the source**: they fire only when the candidate loses what the
source holds, so a repair that removes a wrong number or a doubled sentence is not refused for it.

**Score**: 5 per BLOCK finding, 1 per REPAIR or NOTE finding, 1 per audit doubt no finding names. With no veto, the
candidate is kept when its score is not higher and it either clears the finding that triggered the edit or scores
strictly lower; for an unmarked paragraph of Max's second round, a small rewrite (changed-word share within
`TextGuards.REWRITE_CAP`) is also kept. The weights and the cap are unmeasured named constants.

## The final audit

`run/RunAudit` with `audit/FinalAudit` reruns the cheap checks once, when Translate ends, over every accepted segment no
person reviewed: a paragraph left in the source language, mixed alphabets, an open quote pair, a doubled word, a
placeholder token in the text, the narrator's gender, a doubted word, a lost glossary name. Its doubts become audit
marks for the Proofread mark list. It is not rerun after Proofread; the desk can run it again on demand.

## Export validation

Export (`export/ExportJobImpl`) makes no model call and adds no new quality judgement. It:

- copies each decided target onto a freshly opened source (`export/ExportDecisions`); a FLAGGED segment with no usable
  target is written in the source language (`export/Fallbacks`, `export/EffectiveTargets`), and the export banner says
  so;
- reassembles through the placeholder gate, then re-opens the written book and compares each body segment's placeholder
  markup with what was written (`export/SegmentVerification`);
- writes a quality report side file (`export/QualityReport`) with the stored Proofread summary.

## Gates and the review desk

The gates decide automatically; the review desk is where their results reach the person:

- FLAGGED segments (a BLOCK left after the one repair) are listed first.
- At Fast, the Proofread mark list goes to the desk instead of to model calls.
- Proofread rewrites the gate kept are listed as "Changed by proofread" with the text before (Undo proofread); rewrites
  of a person's edit, or of a FLAGGED segment they do not unblock, wait as proposals.
- The review mode (Unattended, Assisted, Manual) decides only whether a flag pauses the run.

## Known limits

Accepted by the owner on 2026-11-11 after the closing run; the review desk and the audit's findings are the guard. The
figures and their method are in
[results-redesign-closing-run.md](../evaluation/results-redesign-closing-run.md#redesign-closing-run).

- **Quality against the old pipeline is not proven better (Q1).** The paired comparison with baseline `79ffede6` did not
  reach significance; term consistency improved.
- **Narrator gender slips.** Verb forms of a first-person narrator can take the wrong gender; the old pipeline had none
  on the same suite. `GENDER` is REPAIR-level and Proofread reads marked windows, but slips remain.
- **Source-language words in ru→uk output.** A content word that differs only slightly between Russian and Ukrainian
  can stay Russian. No check reads single words: `LANGUAGE_IDENTITY` works on a whole paragraph and `ALPHABET` only sees
  letters the target lacks.
- **Possessives and case forms as separate glossary rows.** The names pass can store a possessive or case form as its
  own row with its own target. Such a row can be read as another character and raise a false `NAME_SWAP` block, and a
  name can end up with two renderings. In one measured case this left a paragraph FLAGGED and export wrote it in the
  source language.

## See also

- [translation-pipeline.md](translation-pipeline.md)
- [context-glossary-memory.md](context-glossary-memory.md)
- [llm-providers.md](llm-providers.md)
- [../evaluation/README.md](../evaluation/README.md)
- [../architecture/overview.md](../architecture/overview.md)
