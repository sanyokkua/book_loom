**Status:** Final **Owner:** architect **Audience:** product, architect, engineering, QA, UX **Last Updated:**
2026-07-18 **Cross-references:** `docs/specification/00_Foundation/01_VISION_AND_SCOPE.md`,
`docs/specification/01_Product/02_TRANSLATION_WORKFLOW.md`, `docs/specification/01_Product/08_UI_SCREENS_AND_STATES.md`

# Personas and Use Cases

## personas {#personas}

### persona-hobbyist-reader {#persona-hobbyist-reader}

**Priya, the hobbyist reader.** Reads for pleasure and wants foreign-language books in her own language. Non-technical;
installs a desktop app but will not run command lines. Has a local model running via a one-click LM Studio install.
Cares about: a readable, natural result; not being asked to make decisions; the book "just working" on her e-reader
afterward. Success = she drops in an EPUB, picks languages, and gets a finished EPUB she can side-load, with the cover
and layout intact.

### persona-indie-translator {#persona-indie-translator}

**Marco, the indie / volunteer translator.** Translates fan works and out-of-copyright novels. Comfortable with tools;
wants a strong machine first draft he can refine, with full control over terminology and final wording. Cares about:
name/term consistency, faithful register, a flagged-segment queue, and being able to override any decision. Success = a
high-quality draft plus an efficient review queue and an exportable glossary he can carry between books.

### persona-language-learner {#persona-language-learner}

**Aiko, the language learner.** Studies a target language and wants faithful bilingual material. Cares about: accuracy
over fluency, a faithful rather than heavily localized rendering, and a bilingual export that pairs source and target.
Success = a bilingual output she can study, with faithful handling of idioms and preserved foreign passages where
relevant.

### persona-self-publisher {#persona-self-publisher}

**Sofia, the self-publisher.** Wrote her own book and wants consistent translations into several languages without
paying per word. Cares about: structural fidelity (her formatting, images, and fonts must survive), consistent
terminology across the whole book, and a repeatable process across target languages. Success = each target-language EPUB
preserves her structure, images, fonts, and IDs with only the text translated (a structure-and-text-preserving,
canonical-equal round trip — exact bytes may differ under re-serialization, DD-43), with stable names and headings.

## primary-use-cases {#primary-use-cases}

### uc-translate-unattended {#uc-translate-unattended}

**Translate a book unattended.** The user opens a supported book, confirms detected source language and format, sets a
short Book Brief (languages, genre, register, quality dial), optionally reviews the auto-proposed glossary, and starts
translation. The pipeline runs the whole book automatically; the user can leave. On return, typically ~99% of segments
are accepted (a non-gating aspiration) and the book is ready to export. Covers `FR-IMPORT-*`, `FR-BRIEF-*`, `FR-ALGO-*`,
`FR-QA-*`, `FR-EXPORT-*`.

### uc-resume {#uc-resume}

**Resume an interrupted job.** After a crash, quit, or explicit pause, the user relaunches (or reopens the project) and
the application offers to resume from the last crash-safe per-chunk checkpoint; already-translated segments are not
redone. Covers `FR-RESUME-*`, `FR-PERSIST-*`.

### uc-review-flagged {#uc-review-flagged}

**Review flagged segments.** The user opens the Review queue, sees the small flagged list, inspects each segment side by
side (source vs target, no diff), and accepts, edits, retries, retries-with-note, or skips it. Export remains available
throughout. Covers `FR-REVIEW-*`.

### uc-manage-glossary {#uc-manage-glossary}

**Manage the glossary.** The user reviews auto-proposed names/terms, edits target renderings, sets type and gender for
agreement, locks entries, and adds/imports/exports glossary entries. Locked terms are enforced during translation.
Covers `FR-GLOSS-*`.

### uc-configure-provider {#uc-configure-provider}

**Configure a provider and models.** The user adds a provider (choosing a preset kind — Ollama uses the native client,
everything else the OpenAI-compatible client — or a custom endpoint), supplies an endpoint and, if needed, a credential
reference (never a stored secret), runs the three-stage verification (connection → models → inference) on the draft
config, selects the translator and judge/helper models from live discovery or types them manually when the server has no
discovery endpoint, and sets it current. Covers `FR-PROV-*`, `FR-MODEL-*`, `FR-INFER-*`.

## use-case-persona-matrix {#use-case-persona-matrix}

| Use case             | Hobbyist  | Indie translator | Language learner | Self-publisher |
|----------------------|-----------|------------------|------------------|----------------|
| Translate unattended | Primary   | Primary          | Primary          | Primary        |
| Resume               | Secondary | Secondary        | Secondary        | Secondary      |
| Review flagged       | Rare      | Primary          | Secondary        | Secondary      |
| Manage glossary      | Rare      | Primary          | Secondary        | Primary        |
| Configure provider   | One-time  | Primary          | One-time         | One-time       |
