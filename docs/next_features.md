# Next features and known gaps

The independent review of `add-translation-engine-and-cli` (2026-09-15) ran the command line with the pseudo model on
real books from `.temporary_context/Books_Examples`: *Building Microservices*, `pg2760-images.epub`,
`Леся Українка - Дим.epub`, two FB2 books and a zipped copy, two TXT books and a Markdown document. The pipeline did
what its specification says. The run also showed gaps outside that change, and a few inside it that were deliberately
left for later. They are recorded here so none is forgotten when real models and the translation screen arrive;
`docs/implementation_plan/CHANGE_BACKLOG.md#decision-debt` points to this file.

Each entry gives the owning module and specification clause, how to reproduce it, the evidence the review measured,
and a suggested fix. An entry that is already planned points to its backlog item instead of repeating its design.

Reproductions translate a book beside itself. Quote the path inside `--args`, because an unquoted path containing
spaces is split into two arguments:

```bash
./gradlew -q :app:translate --args="'.temporary_context/Books_Examples/pg2760-images.epub' --overwrite"
```

The output is `<name>.<to><suffix>` beside the book, `pg2760-images.uk.epub` here.

## Not planned anywhere yet

### 1. EPUB language metadata stays in the source language

- **Owner:** `:document`, `EpubWriter` (the language replacement at `EpubWriter.java:108-123`).
- **Specification:** `openspec/specs/document-round-trip/spec.md`, "Set the target language by replacing the first
  dc:language". No clause, ADR or backlog item mentions `xml:lang` or `dcterms:language`.
- **What is wrong:** only the first `<dc:language>` is replaced. The OPF `<meta property="dcterms:language">`, the
  `<package xml:lang>` attribute, and `xml:lang` or `lang` on each XHTML `<html>` and `<body>` keep the source language.
  Reading systems choose hyphenation, fonts and the text-to-speech voice from these attributes, so a translated book
  would be hyphenated and read aloud as the source language.
- **Reproduce:** translate `pg2760-images.epub`, then
  `unzip -p .temporary_context/Books_Examples/pg2760-images.uk.epub OEBPS/5731650613811440565_2760-h-1.htm.html | head -c 300`
  shows `xml:lang="en"`. For the meta, translate *Building Microservices* and look for `dcterms:language` in
  `OEBPS/content.opf`.
- **Evidence:** in `pg2760-images.uk.epub`, 116 content documents keep `xml:lang="en"` and one keeps `lang="en"`
  while `dc:language` says `uk`. *Building Microservices* keeps `<meta property="dcterms:language">en</meta>` beside
  `<dc:language>uk</dc:language>`. From reading the writer: when a book has no `dc:language`, the one it appends has no
  `dc:` prefix, and a `dc:language` nested in the legacy `<dc-metadata>` wrapper is not reached.
- **Suggested fix:** when the target language is written, also rewrite `xml:lang` and `lang` on XHTML roots and bodies
  that carry the source language, `<package xml:lang>`, and the `dcterms:language` meta; add scenarios to the
  document-round-trip specification.

### 2. The OPF is re-serialized with a new declaration and CRLF line ends

- **Owner:** `:document`, `EpubWriter.java:216-225` (`new XMLOutputter(Format.getRawFormat())`).
- **Specification:** `docs/specification/02_Architecture/03_DOCUMENT_MODEL.md#xml-round-trip-config` (the declared
  encoding is echoed on output); DD-43 (canonical-equal output).
- **What is wrong:** the package document gains `<?xml version="1.0" encoding="UTF-8"?>` when the source had no
  declaration, every line feed in it becomes CRLF (JDOM2's default line separator), and it always declares UTF-8.
- **Reproduce:** translate *Building Microservices* and compare the first bytes of `OEBPS/content.opf` in the source
  and the output.
- **Evidence:** the source OPF has no declaration and no carriage return; the output has the declaration and two
  CRLF line ends. Other JDOM2 writers in the module pin LF (`Jdom2TreeNode.java:31-48`) or follow the source
  (`Fb2Writer.java:125-130`); backlog D11 covers only FB2. The golden comparator re-serializes both sides through the
  same JDOM2 format, so it cannot see the difference (`EpubCanonicalAssert.java:119-124`).
- **Suggested fix:** give the OPF outputter the LF separator `Jdom2TreeNode` uses, record in `ParsedOpf` whether the
  source had a declaration and which encoding it named, and write back the same.

### 3. XHTML attribute values lose a `&#10;` reference

- **Owner:** `:document`, the EPUB spine writer (jsoup with `syntax(xml)`, `XhtmlParser.java:73-80`,
  `EpubWriter.java:195-214`).
- **Specification:** DD-43 and `.claude/rules/document-roundtrip.md` (only text nodes change).
- **What is wrong:** an attribute value holding `&#10;` is written with a literal line feed. An XML parser normalizes
  a literal line feed in an attribute value to a space, so the value read back differs from the source.
- **Reproduce:** translate *Building Microservices* and compare the `data-pdf-bookmark` attribute of the section
  "Technology for Inter-Process Communication" in `OEBPS/ch04.html`.
- **Evidence:** the source holds `…Communication: &#10;So Many Choices`; the output holds a raw line feed at that
  place. It is the only such attribute in that book, but any `title` or `alt` carrying a line-feed reference is
  affected the same way.
- **Suggested fix:** escape `\n`, `\r` and `\t` in attribute values when serializing, and make the canonical
  comparison apply attribute-value normalization so the golden test sees this class of change.

### 4. An XHTML XML declaration becomes a comment

- **Owner:** `:document`, the EPUB spine writer (jsoup). Already noted in
  `docs/implementation_plan/notes-corpus-verification.md`; recorded here for its encoding consequence.
- **What is wrong:** `<?xml version='1.0' encoding='utf-8'?>` is written as `<!--?xml version='1.0' encoding='utf-8'?-->`,
  and the DOCTYPE's quotes and line breaks are normalized. A spine document declared in an encoding other than UTF-8
  is still written in that encoding but no longer says so, and an XML reader then decodes it as UTF-8.
- **Reproduce:** translate `pg2760-images.epub`, then
  `unzip -p .temporary_context/Books_Examples/pg2760-images.uk.epub OEBPS/5731650613811440565_2760-h-0.htm.html | head -c 120`.
- **Evidence:** every content document with a declaration in `pg2760-images.epub` and `Леся Українка - Дим.epub`.
  Both are UTF-8, so neither book is damaged; a legacy-encoded EPUB would be.
- **Suggested fix:** keep the source prolog and write it back ahead of the serialized document, or transcode such a
  document to UTF-8 as ADR-0029 decides for unrepresentable text.

### 5. An FB2 byte-order mark is not written back

- **Owner:** `:document`, `Fb2Reader.java:175-181` (skips the mark), `ParsedFb2.java:26-33` (no field for it),
  `Fb2Writer.java:182-186`.
- **Specification:** `docs/specification/01_Product/03_DOCUMENT_FORMATS.md#encoding-and-bom` says the presence of a
  byte-order mark is recorded and re-emitted.
- **Reproduce:** found by reading the code; no sample book starts with a mark. Prefix one to a copy of
  `Forrest_Gump-John_Escott.fb2` (`printf '\xef\xbb\xbf' | cat - Forrest_Gump-John_Escott.fb2 > Bom.fb2`), translate
  it, and check the first three bytes of `Bom.uk.fb2`.
- **Suggested fix:** record the mark in `ParsedFb2` and write it back, as `TxtReader` and `TxtWriter` already do.

### 6. A TXT export refused by its charset says "This file could not be opened"

- **Owner:** `:document`. `TxtWriter.java:104-117` throws `CorruptContainerException`, and `DocumentService.write`
  maps it to the message meant for a book that cannot be opened (`DocumentService.java:127-130, 327-336`).
- **Specification:** the shipped requirement "Refuse a plain-text export the source encoding cannot represent";
  ADR-0029, accepted, decides to transcode to UTF-8 instead, and is not built yet (backlog D1,
  `settle-writer-policy-and-document-lifetime`).
- **What is wrong:** when a translation holds a character the book's charset cannot represent, the command line
  prints `This file could not be opened: This file could not be read as a book…` for a book that opened fine. Even the
  pseudo model can cause it: upper-casing `µ` gives `Μ` and `ÿ` gives `Ÿ`, and ISO-8859-1 holds neither.
- **Reproduce:** not run in the review, because every sample TXT is ASCII or UTF-8. Translate a Latin-1 text of a few
  pages that contains `µ` or `ÿ`, long enough for charset detection to settle on ISO-8859-1.
- **Suggested fix:** implement ADR-0029, or until then give the refusal its own title and message ("This translation
  cannot be saved in the book's text encoding").

### 7. ADR-0029 and the backlog describe EPUB's unencodable characters wrongly

- **Owner:** documentation.
- **What is wrong:** ADR-0029 and the D1 detail in `CHANGE_BACKLOG.md` say the EPUB writer writes `?` for a character
  the output charset cannot encode. jsoup's escaper writes a numeric character reference instead
  (`org/jsoup/nodes/Entities.java` in jsoup 1.23.1). Found by reading the jsoup source, not by running it.
- **Suggested fix:** pin the behaviour with a test, then correct the ADR's consequences and the backlog text.

## Already planned

### 8. The table of contents, page titles, metadata and alt text stay untranslated

- **Planned in:** DD-47 in `docs/specification/00_Foundation/04_DESIGN_DECISIONS.md` (translate metadata, ToC and nav
  labels, and image alt text, behind the "also translate" toggles) and the backlog change
  `add-metadata-units-and-language-detection`.
- **Reproduce:** translate *Building Microservices*. `OEBPS/toc01.html`, the navigation document, carries
  `properties="nav"` but is not in the spine, so it is copied byte for byte; `toc.ncx` is never parsed for text; every
  XHTML `<head><title>` is skipped because segmentation starts at `<body>`; `dc:title` and `dc:description` stay in the
  source language. Translating `Rouling_Harry_Potter_1_Harry_Potter_and_the_Sorcerers_Stone_RuLit_Net.fb2` leaves the
  `annotation`, `book-title` and `history` in `description` unchanged, and translating `POSTMORTEM_THREE_ATTEMPTS.md`
  leaves the alt text of its 11 images unchanged.
- **Evidence:** in *Building Microservices*, 494 table-of-contents labels and 30 page titles stay in English, so a
  reader's table-of-contents panel shows the source language beside translated chapter headings.

### 9. A reordered placeholder pair slips through the gate

- **Planned in:** backlog D7 and D8, owned by change 12 (`add-chunking-and-context-assembly`).
- **What is wrong:** the gate compares only the multiset of `⟦gN⟧` tokens (`PlaceholderGate.java:29-34`), so a model
  that swaps or concatenates a masked pair passes it. EPUB's lenient fragment parse then repairs the broken markup
  silently (`JsoupTreeNode.java:85-99`), while FB2 refuses it at write. The corpus saw one EPUB re-open with 4,733
  segments instead of 5,096.
- **Why it matters now:** since `add-translation-engine-and-cli`, the export re-opens the written book and compares
  segment counts, so such a book fails the whole job at its very end with "The exported book failed validation". No
  file is written, no segment is named, and without persistence every decision of a long real-model run is lost.
- **Also:** `.claude/rules/document-roundtrip.md` says reordered placeholders must fail the gate, while the shipped
  specification says token order must not affect the comparison ("Reordered tokens pass the gate"). One of them has to
  change.
- **Reproduce:** the pseudo model never reorders tokens. A scripted model can: answer an EPUB segment masked as
  `⟦g0⟧old⟦g1⟧` with `⟦g1⟧OLD⟦g0⟧`.
- **Suggested fix:** check the restored segment's structure when it is unmasked, so the one segment is flagged and the
  export no longer fails.

### 10. Markdown bare URLs are translated, and unencodable characters become `?`

- **Planned in:** change 15 ("bare URLs in running prose") and backlog D1.
- **What is wrong:** only the tables extension is loaded (`MarkdownReader.java:51-56`), so a bare URL in running text
  is translatable text that a model may rewrite; the pseudo model upper-cases it. The Markdown writer encodes with
  `getBytes(charset)` (`MarkdownWriter.java:81`), which silently writes `?` for a character the charset cannot hold.
- **Reproduce:** translate a Markdown book with the line `See https://example.com/path`; the output reads
  `SEE HTTPS://EXAMPLE.COM/PATH`.

## Deferred from `add-real-llm-clients`

### 11. Real-client follow-ups

- **Structured output and repair:** a structured-output rejection is still a normal mapped failure. The silent
  downgrade to a plain request, JSON extraction from surrounding prose and one bounded JSON repair call remain future
  work.
- **Interactive gate behavior:** the shared gate supports blocking `run`; `tryRun` and its `busy` outcome belong with
  the first interactive screen.
- **Authentication:** the future Bearer path must store only an environment-variable name and resolve it at request
  time. Keychain support is not included.
- **Reasoning and context controls:** `think`, `reasoning_effort`, `num_ctx`, `/api/show` context sizing and
  `keep_alive` are intentionally not sent by the current clients.
- **Now reachable with a real model:** backlog D7, D9 and D15 can now occur in an actual provider-backed translation,
  rather than only in scripted or pseudo-model investigation.

## Deferred from `add-translation-engine-and-cli`

### 12. Log volume

- **Owner decision, 2026-09-15:** kept as built.
- **Evidence:** *Building Microservices* at TRACE wrote 42 lines per segment, 37 of them DEBUG: about 53 MB for 5,096
  segments, rolled over five 10 MB files. Every `./gradlew :app:translate` run is a development run, where DEBUG is the
  default. Many lines repeat per segment: `JobProgressTracker` logs `hasPending` four times, `next` three times and
  each progress snapshot twice, and `SegmentTranslator` writes paired "Building"/"Built" lines. `TranslationJob.state()`
  logs on every call, which would flood the log of a screen that polls it. The `segment` MDC key is set while a segment
  is decided, and `logback-test.xml` prints it, but the production pattern (`LoggingBootstrap.java:34-35`) does not.
- **Workaround:** `BOOKLOOM_LOG_LEVEL=INFO` for large books.
- **Suggested fix, if revisited:** no DEBUG line in pure query methods, one line per decision step, and `%X{segment}`
  in the production pattern.

### 13. Command line: Ctrl+C, folder mode, and Guice outside the `translate` task

- **Ctrl+C:** there is no shutdown hook, so a killed run can leave the hidden `.<destination file name>` beside the
  output. The next export to the same destination overwrites it. A hook that cancels the job and waits briefly would
  let the export delete it.
- **Folder mode:** the original feature description translated every book in a folder into `<folder>.<to>/` and
  printed totals. The OpenSpec change dropped both as a non-goal.
- **Guice and `sun.misc.Unsafe`:** only the `translate` task sets `guice_bytecode_gen_option=DISABLED`.
  `./gradlew :app:run` and a packaged image still make Guice 7.0.0 use `sun.misc.Unsafe`, which JDK 25 reports with a
  warning and a later JDK may refuse outright. Set the property for `run` and the jpackage launcher too, or move to a
  Guice release that no longer needs it.

### 14. For the translation screen

- **An interrupt while a job runs is not a cancellation.** `JobControl.awaitPause` treats an interrupt as cancel only
  while the job is paused; a background task cancelled during a model call keeps going until the next pause. Checking
  the thread's interrupt flag at each boundary would fix it.
- **A refused start sends no event.** When the destination exists without overwrite, or the source does not open,
  `run()` returns the error and the job ends Failed without a `Finished` event, so a screen that only listens to events
  never hears the end.
- **Export verification counts segments only** (`BookExporter.validateCountAndPublish`). A restored segment whose
  structure changed but whose count did not still passes; comparing each re-opened segment's placeholders would not.
- **FB2 inline `<code>` text is translatable**, masked as a pair, while EPUB's inline `<code>` is one atomic
  placeholder. This follows the shipped document-round-trip specification but not the wording of DD-49, which protects
  inline `<code>`; confirm which is intended.
