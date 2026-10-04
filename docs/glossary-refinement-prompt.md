# Prompt: refine a BookLoom glossary CSV with a large model

How to use: scan the book in BookLoom, export the glossary CSV (Names & style → export), attach that CSV to a chat
with a strong model (Claude, ChatGPT, …) together with the book if the model can read it, fill the `{{…}}` values and
paste the prompt below. Import the CSV it returns back into Names & style.

---

```text
You are a senior literary translator and terminologist. I am translating the book "{{BOOK_TITLE}}" by {{AUTHOR}}
from {{SOURCE_LANGUAGE}} into {{TARGET_LANGUAGE}} with a desktop app. The app scanned the book automatically and
produced the attached CSV of names and terms. The scan is mechanical, so it contains mistakes. Your job: (a) research the book and
give me the context for the app's Book Brief, then (b) review the CSV, remove what does not belong, correct and
complete it, and give me a CSV the app can import without errors.

NAME POLICY: {{NAME_POLICY}}
(Example values: "transliterate names into {{TARGET_LANGUAGE}} spelling", "keep names in the original spelling",
"translate names that have a meaning". Follow it for characters and places.)

== 0. Book Brief (do this first) ==
If you can search the web, look the book up (publisher pages, reviews, encyclopedias) and base the answers on what
you find and on the book itself; if you cannot, use what you know and say so. Give me values for these three fields
of the app's Book Brief, each with a one-line justification and your confidence (high/medium/low):
- GENRE: one value; prefer one of the app's list: Literary fiction, Classic literature, Historical fiction, Gothic
  novel, Romance, Historical romance, Mystery, Detective fiction, Crime fiction, Thriller, Psychological thriller,
  Horror, Science fiction, Fantasy, Epic fantasy, Dystopian fiction, Adventure, Western, War fiction, Humor, Satire,
  Young adult, Children's literature, Fairy tale, Short stories, Poetry, Drama, Memoir, Biography, Autobiography,
  Essay, Travel writing, History, Philosophy, Religion and spirituality, Popular science, Self-help, Business,
  True crime, Graphic novel. If none fits, give a short free-text genre (e.g. "Gothic novel").
- REGISTER: exactly one of: Formal / literary | Neutral | Casual. It is how formal the {{TARGET_LANGUAGE}} translation
  should sound, judged from the book's prose and its readers.
- NARRATIVE VOICE / ERA NOTES: short free text (at most about 200 characters) that the translating model will read as
  an instruction: person and tense of the narration (for example "third person, past tense"), the narrator's manner
  (ironic, intimate, detached), the era or setting and any period diction or dialect to keep. Write it as a note to a
  translator, not as a review.
Also mention, in one line, anything about the book that affects names or terms (invented language, titles used as
forms of address, nicknames, a series where established translations exist). Use this context when you judge the CSV.

== 1. What to do ==
1. Review every row of the attached CSV against the book (use the attached book text if you have it; otherwise use
   what you know about this book and tell me which rows you could not verify).
2. REMOVE rows that are not a real name or recurring in-world term. Typical scan mistakes:
   - ordinary words that were capitalised because they start a sentence, or appear in headings or all-caps lines;
   - interjections, titles of chapters, page headers/footers, "Chapter", "Part", Roman numerals;
   - pronoun- or article-led fragments ("The Boy", "His Mother") that are not a title;
   - OCR or markup debris, single letters, numbers, URLs, file names;
   - the same entity twice that differs only by case, possessive ('s) or punctuation (keep one row);
   - generic nouns and common words that any translator would translate without a glossary.
3. KEEP and fix real entries: characters (full names, nicknames, short forms that occur alone), places, organisations,
   in-world objects/concepts/spells/creatures, titles and honorifics.
   A short form that is used on its own in the book (for example a first name or surname) gets its own row with the
   same target as the full name, so the app can find it.
4. ADD important names/terms that the scan missed, but only if they really occur in the book. Do not invent anything.
5. For every kept row, fill all columns as specified below.

== 2. CSV specification (the app rejects anything else) ==
- UTF-8, RFC 4180 CSV, comma separator, header line exactly:
  term,target,type,gender,locked
- Exactly 5 fields on every line. Put a field in double quotes if it contains a comma, a double quote or a line
  break, and double any double quote inside it. No extra columns, no comments, no blank rows.
- term: the SOURCE spelling exactly as it appears in the book, one row per term, no duplicates. Never blank.
- target: the {{TARGET_LANGUAGE}} rendering in its DICTIONARY FORM (nominative, singular unless the term is always
  plural), uninflected, following the name policy. Never empty for a row with locked=true.
- type: one of (lower case) character | place | term | title | other
  character = a person or creature with a name; place = a location; term = an in-world object, concept, spell,
  organisation, species; title = a title or honorific; other = anything else that must stay in the glossary.
- gender: one of (lower case) female | male | neuter | unknown
  For a character: the gender of the person. For other types: the grammatical gender of the TARGET word, so
  adjectives and verbs agree. Use unknown only when it truly cannot be decided.
- locked: true or false (lower case). true means the app writes the target into the text exactly as given and never
  inflects it, so use true only for names that must stay identical in every sentence (indeclinable or deliberately
  fixed spellings). Default false: the translating model may then decline the name as {{TARGET_LANGUAGE}} grammar needs.

== 3. Quality rules ==
- Use one consistent rendering per entity across the whole list; related entries must agree
  (for example family surname forms, a title plus a name).
- Follow {{TARGET_LANGUAGE}} spelling conventions and any established published translation of this book if one
  exists; say which one in the report.
- Do not change the term column of a kept row except to fix an obvious scan error (then mention it in the report).
- Sort the CSV by type (character, place, term, title, other), then alphabetically by term.

== 4. What to give me back ==
A. BOOK BRIEF block, first, in this exact shape so I can copy each value into the app:
   Genre: <value>        (why / confidence)
   Register: <value>     (why / confidence)
   Narrative voice / era notes: <text>   (why / confidence)
   Sources: the pages or works you relied on, or "from memory, no web access".
B. The refined CSV as a downloadable file named glossary_refined.csv (and also the full text in a code block if you
   cannot attach files). Only the CSV, nothing else inside it.
C. A short report, in a second code block or file glossary_report.md, with:
   - REMOVED: each removed term and a few words why (I will delete these in the app myself, so the list must be
     complete);
   - ADDED: the new rows;
   - CHANGED: rows whose target/type/gender/locked you changed, old → new;
   - UNSURE: rows you could not verify or where two renderings are plausible, with your suggestion.
D. Before replying, check your own CSV: header exact, 5 fields per line, no duplicate terms, every type/gender/locked
   value from the allowed lists, no locked=true row with an empty target. Fix problems first.

Attached: glossary.csv (the app's scan){{ and the book text}}.
```

---

Notes
- The Book Brief values are typed by hand into the app: Genre (pick from the list or type), Register (three
  choices), Narrative voice / era notes (free text). The CSV import does not carry them. The Brief also has an
  Audience field, which this prompt does not ask about.
- Import adds or updates rows by term and reports malformed rows by line number. I have not verified that rows missing
  from the file are deleted on import, so delete the REMOVED list in the table yourself (or re-scan with an empty
  glossary and import only the refined file).
- Filling `{{NAME_POLICY}}`: use the same choice as the Book Brief name policy so the glossary and the prompts agree.
