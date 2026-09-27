# ADR-0037 — Take the source language from the book's metadata, and check the target language by script

**Status:** accepted **Date:** 2026-09-26 **Deciders:** owner

## Context and problem statement

The specification asks the application to detect the real source language from the text and to trust it over the
declared metadata (FR-IMPORT-03, FR-ALGO-A4, EC-LANG-1), using the Lingua library, and to check that each
translation is in the target language (the QA target-language check, `02_Architecture/05_PIPELINE_ENGINE.md#qa-checks`).
The owner decided that "language detection means read metadata in fb2 or epub. No detection for text files": no
text-based detector is added in this change. A wrong declaration — `Croatian_Lesson_Police.epub` declares `en-US` for
Ukrainian text (`docs/next_features.md` §15) — is fixed by the person, who can now edit the source language.

## Decision

- The source language is **what the book declares**, normalized (`ua` → `uk`, `EN` → `en`, `en-US` → `en`,
  `zh-TW` → `zh-Hant`), preselected on the Book Brief and editable there. Where an EPUB's package and most of its
  content documents disagree, or the package declares nothing, the content documents' majority language is
  preselected instead. A plain-text book declares nothing, so the person chooses.
- The **language-mismatch** import state is raised by the metadata itself: an EPUB whose package language disagrees
  with the language most of its content documents declare (`xml:lang`/`lang`), or a declared code the application does
  not recognize.
- The QA **target-language check becomes a script check**: the share of letters written in the target language's
  script, run only when the source is at least 20 characters and the two languages use different scripts, and skipped
  (full margin) for same-script pairs.
- Each block records the `xml:lang`/`lang` it **declares** (its nearest ancestor-or-self element below the document
  root), never the result of a comparison. The pipeline compares that declaration with the brief's source language: a
  block whose declared language differs is a **pre-detected foreign span** for the foreign-passage policy. Comparing
  at parse time with the package language would mark every block of a mis-declared book foreign.
- Under the Keep foreign-passage policy, an **inline** element whose own `lang`/`xml:lang` differs from the run's
  source language is kept verbatim: it is protected as one placeholder with its inner text and restored unchanged
  (EC-FOREIGN-3), read from the attribute only.
- No detection library is added.

## Considered options

- **Lingua for a limited language set.** Rejected by the owner for now; it adds a large dependency and memory use.
- **No mismatch trigger at all.** Rejected: EPUB metadata that contradicts itself is common enough to warn about.

## Consequences

- **Positive:** no new dependency; import stays instant; behaviour is deterministic.
- **Negative:** a book whose metadata is consistently wrong is not caught; a same-script wrong-language translation
  (Polish for Czech) passes the script check. Both are stated in the specification.
- **Neutral:** FR-IMPORT-03, FR-ALGO-A4, the QA table and the stack list's Lingua entry are edited.

## What would falsify this decision

Wrong-language translations between same-script languages, or silently mis-declared books, turn up often enough in
real runs that people rely on review to catch them.

## Amended in this change (2026-09-27)

The owner decided during the readiness review of `complete-translation-workflow` to build EC-FOREIGN-3 from metadata
only, so inline foreign runs are now kept verbatim under the Keep policy. Block language became a recorded declaration
compared with the brief's source, because the source language is editable and a book declaring `en-US` for Ukrainian
text would otherwise have every block marked foreign. Nothing is detected from the text.
