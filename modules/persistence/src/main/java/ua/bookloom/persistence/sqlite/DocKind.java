package ua.bookloom.persistence.sqlite;

/** The kinds of per-project JSON document kept in {@code project_docs}, one row per project and kind. */
enum DocKind {

    /** The glossary with the terms removed from it ({@link GlossaryDocument}). */
    GLOSSARY("glossary"),

    /** The recurring-term lexicon with the terms removed from it ({@link LexiconDocument}). */
    LEXICON("lexicon"),

    /** The rolling summary. */
    SUMMARY("summary"),

    /** The run started last. */
    RUN("run"),

    /** What the last export wrote. */
    LAST_EXPORT("lastExport");

    private final String key;

    DocKind(final String key) {
        this.key = key;
    }

    /** The value of the {@code kind} column. */
    String key() {
        return key;
    }
}
