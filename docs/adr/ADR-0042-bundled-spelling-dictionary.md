# ADR-0042 — Choose a source of words for the garbled-word check (bundled spelling dictionary)

**Status:** proposed **Date:** 2026-10-05 **Deciders:** owner (decision pending; the coder only proposes)

This record is **proposed, not accepted.** Choosing a bundled dictionary means choosing a licence and adding megabytes
to the installer, and that is the owner's call. Until it is accepted, the code holds the port with a no-op default
and an optional model-based implementation that no run uses (task 15d.11).

## Context and problem statement

A local model sometimes writes a word that is not a word: `кафедрахрі`, `розлізяв`, `дірявтиму`. It is in the right
script, so the script-purity check (15d.2) cannot see it, and the deterministic checks that exist do not know a
language's vocabulary. Three layers already stand against it: the script check, the reviewer's "no non-words"
criterion with a mandatory quote (15d.6), and the final audit (15d.12). 15d.11 adds a fourth, the `WordValidator` port
(`ua.bookloom.pipeline.checks`), which turns an unknown word into a soft, low-severity finding with the word's span, never
a blocker. The open question is where the validator's knowledge of "a real word" comes from.

Constraints: the app is offline (nothing is fetched at runtime, `offline-and-privacy.md`), the open-language rule
(any language the JDK can name must work, so a per-language dictionary can only ever be an add-on and the default must
find nothing), and a rare, dialect or archaic word that a list lacks must never stop a book.

## Decision drivers

- Catch at least 70% of the ten known garbled examples and flag none of the fixture's valid words.
- Licence compatible with shipping inside the installer.
- Installer size and start-up cost.
- No network, no native library that `jpackage` must carry per operating system.
- A second language must not need code, only data.

## Considered options

- **A. None.** Keep the no-op default; rely on the reviewer criterion and the final audit.
- **B. A bundled word list for Ukrainian** (hunspell or LanguageTool style, expanded to word forms or kept with affix
  rules), checked by a pure-Java lookup behind `DictionaryWordValidator`.
- **C. A model-based "suspicious words" pass** (`ModelWordValidator`): one extra structured call per batch, temperature 0,
  flat schema, every word and quote verified in code against the text.

## Decision outcome

**Recommended: A now, C as an opt-in measured before use, B only if the measurement of C falls short.** Reason: A
changes nothing and costs nothing; C needs no licence decision and no installer growth and its measurement is cheap (the
`words` suite); B is the only option that needs the owner to accept a licence and a download size, so it should be
asked for only when the numbers show C cannot meet the bar. The port already lets B drop in without touching a caller.

Status stays `proposed`. The task's box stays open until the owner accepts this record and the measured numbers meet
the bar.

### Consequences

- Positive: no run changes today; either implementation arrives as data or one class behind the same port.
- Negative: until one is chosen, the garbled-word defence rests on the reviewer's quoted criterion and the final audit.
- Neutral: the port is bound to a no-op in `PipelineModule`; the model option is built by hand.

## Pros and cons of the options

### Option A — none

- Good: nothing to ship, no false flags, no extra call.
- Bad: a garbled word that the reviewer misses reaches the export unflagged until the final audit lists it.
- **Falsified if:** the final audit (15d.12) plus the reviewer leave more than about one garbled word per thousand
  accepted segments on a real run, or the owner finds garbled words in an exported book that no layer reported.

### Option B — bundled word list for Ukrainian

- Facts to confirm before relying on them (none could be checked without network access, so all are **unverified**):
  the common Ukrainian hunspell/LanguageTool dictionary (the `dict_uk` project, in the `uk_UA` hunspell and
  LanguageTool packages) is, as remembered, under an LGPL- or GPL-family licence with possible share-alike terms; the
  packed dictionary is of the order of a few megabytes and the expanded word-form list of the order of tens of megabytes;
  a hunspell binding that uses native code would need a per-OS library in the `jpackage` image.
- Good: deterministic, instant, no model call, high recall on words that are plain misspellings; repeats exactly.
- Bad: a licence to accept and keep notices for; installer growth; flags every rare, dialect, archaic or newly coined
  word and every name not in the list (false positives), so it can only ever be a soft note; covers Ukrainian only, and
  each further language needs its own list; inflected forms need affix rules or a large expanded list.
- **Falsified if:** the licence cannot be shipped in the installer, or the packed list adds more than the owner will
  accept to the installer, or it flags more than 2% of the words of a clean fixture book, or it catches fewer than 70%
  of the ten known garbled words.

### Option C — model-based suspicious-words pass

- Good: no licence, no installer growth, works for any language the model reads, verifies each reported word and quote
  against the text so a hallucinated word is dropped.
- Bad: one extra call per batch (time on a local model), only as good as the model's sense of the language, can flag a
  rare real word, and a small model may miss garbled words or flag valid ones; not byte-for-byte repeatable beyond temperature 0.
- **Falsified if:** on the `words` suite (`scripts/eval-matrix.sh --suite words`) recall on the ten garbled cases is
  below 70% on the target model (e4b), or any of the ten clean sentences with rare valid words is flagged, or the extra
  calls add more than about 10% to the seconds per segment of a run.

## Links

- Design decisions: DD-16 (judge), DD-45
- Spec clauses: `docs/specification/04_Build_and_Release/02_QUALITY_GATES.md`, `docs/specification/01_Product/12_PROMPT_CATALOG.md#garbled-word-check`; capability `quality-gates` of the `complete-translation-workflow` change
- Stories: none yet; task 15d.11 of `openspec/changes/complete-translation-workflow`
