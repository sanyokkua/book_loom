package ua.bookloom.llm.knowledge;

/** Where a {@link LanguageResult} came from. */
public enum LanguageSource {
    /** The model's own row. */
    ROW("row"),
    /** The row of the model it is built on. */
    BASE("base"),
    /** The language list the server read from the model file. */
    FACTS("facts"),
    /** Nothing was found. */
    NONE("none");

    private final String label;

    LanguageSource(final String label) {
        this.label = label;
    }

    /**
     * The word the log uses.
     *
     * @return a short lower-case word
     */
    public String label() {
        return label;
    }
}
