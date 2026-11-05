package ua.bookloom.persistence.sqlite;

/** The kinds of per-project JSON document kept in {@code project_docs}, one row per project and kind (a kind with a key suffix,
 * such as {@link #PROMPT_FRAME}, holds one row per suffix). */
enum DocKind {

    /** The glossary with the terms removed from it ({@link GlossaryDocument}). */
    GLOSSARY("glossary"),

    /** The recurring-term lexicon with the terms removed from it ({@link LexiconDocument}). */
    LEXICON("lexicon"),

    /** The run started last. */
    RUN("run"),

    /** What the last export wrote. */
    LAST_EXPORT("lastExport"),

    /**
     * One fixed prompt frame ({@link ua.bookloom.api.project.PromptFrame}); a project holds one row per distinct frame,
     * its kind this key, a colon and the frame's hash.
     */
    PROMPT_FRAME("promptFrame"),

    /**
     * One chapter's summary ({@link ua.bookloom.api.project.ChapterSummary}); a project holds one row per chapter, its
     * kind this key, a colon and the chapter key.
     */
    CHAPTER_SUMMARY("chapterSummary");

    private final String key;

    DocKind(final String key) {
        this.key = key;
    }

    /** The value of the {@code kind} column. */
    String key() {
        return key;
    }
}
