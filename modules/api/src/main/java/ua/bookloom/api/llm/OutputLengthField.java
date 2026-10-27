package ua.bookloom.api.llm;

/** The request field that caps a reply's length on an OpenAI-compatible endpoint. */
public enum OutputLengthField {
    /** The classic field. */
    MAX_TOKENS("max_tokens"),
    /** The field newer models require in place of {@code max_tokens}. */
    MAX_COMPLETION_TOKENS("max_completion_tokens");

    private final String wireName;

    OutputLengthField(final String wireName) {
        this.wireName = wireName;
    }

    /**
     * Returns the JSON field name written to the wire.
     *
     * @return the non-empty field name
     */
    public String wireName() {
        return wireName;
    }
}
