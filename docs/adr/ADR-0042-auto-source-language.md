# ADR-0042 — The source language may be Auto: the model identifies it and the code uses generic rules

**Status:** accepted 2026-10-15 **Date:** 2026-10-12 **Deciders:** owner
**Amends:** ADR-0037

## Context and problem statement

ADR-0037 takes the source language from the book's metadata and says "a plain-text book declares nothing, so the person
chooses". In practice that forced a guess: a TXT or Markdown book, or an EPUB declaring a code the application does not
recognise, could not be translated until the person typed a language, and the code then read every unknown book as
English (English pronouns, English stop words, English plural endings, English contractions). A French book with no
metadata was proposed the name "Le Rouge" and its characters were given genders from English pronouns.
Prompts already know how to say "the language of this segment (infer it from its text)" for a missing source.

## Decision

- A Book Brief with no source language (`BookBrief.sourceLanguage == null`) means **Auto**: the model identifies the
  language from the text. Auto is the default when the import finds no language to preselect. A language the person
  chooses, or the book declares and the application recognises, is still preselected, so a book that declares one is
  not Auto unless the person asks (`--from auto` on the command line).
- One resolver, `pipeline.SourceLanguage`, answers the two questions. **For a prompt** the language is the chosen tag or
  none: a prompt under Auto never names a language nobody chose, not even the book's declaration. **For the code's own
  analysis** it is the chosen tag, else a declared tag the application recognises (in its normal form), else none.
  Every model-call frame carries both (`CallFrame.sourceLanguage` for prompts, `CallFrame.analysisLanguage` for the
  readers), so a reader cannot take the prompt's answer by mistake.
- With no analysis language the code uses generic rules and assumes no language: the stop-word and title lists are the
  union of every bundled list; no pronoun table is read, so the code produces no gender evidence; no first-name list
  seeds a gender; no plural endings fold a name into its neighbour; the English contraction rule is off; the narrator
  detector gives no hint (the person is asked once at Start). Target-side checks are unchanged.
- A foreign run the book marks is kept verbatim under Auto only when it is written in a script other than the one the
  book is mostly written in, counted by code from the letters. A foreign passage in the same script is left to the
  prompt's foreign-passage rule.
- Auto never blocks the work: Continue, the step locks, the workflow done-mark and Start need only a target language
  that differs from any chosen source.

## Considered options

- **Detect the language from the text with a library.** Rejected again for the reasons in ADR-0037 (size, memory); the
  model already reads the text and identifies the language when asked.
- **Keep asking the person to choose.** Rejected: a book with no metadata could not be translated without a guess, and
  a wrong guess silently applied the wrong rules.

## Consequences

- **Positive:** no book needs a guessed language; no language's rules are applied to a book that is not in it.
- **Negative:** under Auto the name scan, the recurring-term scan and the pair-specific source notes work with less
  information than with a known language, so quality is lower than with a correct choice; the person is nudged to
  choose a language when the book declares one wrongly.
- **Neutral:** `ResidueCheck`'s English exemption is a target-side rule and stays.
- **Known limit:** with no analysis language the "never alone" word list adds language names, and it can only spell
  them in English (`English`, `French`, …), so a book written in another language that names a language in its own
  tongue (`Anglais`) may have it proposed as a name. The model review removes it; a bundled list per language would fix
  it and is not planned.

## What would falsify this decision

The flagged share of a run under Auto is above 1.5 times the flagged share of a run of the same book and model with the
language chosen, or the model misidentifies the language in 2 or more of 20 evaluation books. Either result means the
generic rules or the model's identification are not good enough, and Auto should again ask the person to choose.
