package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The rolling-summary update's structured response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SummarySchema {

    /** A response object holding the bilingual summary and, optionally, the facts it rests on. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "summary":{"type":"object","properties":{\
            "source":{"type":"string"},"target":{"type":"string"}\
            },"required":["source","target"],"additionalProperties":false},\
            "facts":{"type":"array","items":{"type":"string"}}\
            },"required":["summary"],"additionalProperties":false}
            """.strip();
}
