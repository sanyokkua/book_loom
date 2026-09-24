package ua.bookloom.pipeline.prompt;

/** The strict single-segment response schema requested from draft translation providers. */
public final class DraftSchema {

    /** A response object with only the required translated target. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"target":{"type":"string"}},"required":["target"],"additionalProperties":false}
            """.strip();

    private DraftSchema() {
        // Constants only.
    }
}
