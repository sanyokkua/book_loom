package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The name pre-scan's structured response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PrescanSchema {

    /** A response object listing proposed terms; only the term itself is required of each. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "terms":{"type":"array","items":{"type":"object","properties":{\
            "term":{"type":"string"},"type":{"type":"string"},"gender":{"type":"string"},\
            "note":{"type":"string"},"confidence":{"type":"number","minimum":0,"maximum":1}\
            },"required":["term"],"additionalProperties":false}}\
            },"required":["terms"],"additionalProperties":false}
            """.strip();
}
