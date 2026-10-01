package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The judge's structured response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeSchema {

    /**
     * A response object with the chunk's overall score, advisory verdict, findings and deferrals. Every list and every
     * text field is bounded, so a provider that enforces the schema cannot let a reply grow without end; the reader
     * stays tolerant of a provider that ignores the bounds.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "score":{"type":"number","minimum":0,"maximum":1},\
            "verdict":{"type":"string","maxLength":40},\
            "findings":{"type":"array","maxItems":12,"items":{"type":"object","properties":{\
            "segmentId":{"type":"string","maxLength":8},"type":{"type":"string","maxLength":40},\
            "severity":{"type":"string","maxLength":8},"note":{"type":"string","maxLength":240}\
            },"required":["segmentId","type","severity","note"],"additionalProperties":false}},\
            "deferrals":{"type":"array","maxItems":8,"items":{"type":"object","properties":{\
            "segmentId":{"type":"string","maxLength":8},"reason":{"type":"string","maxLength":240}\
            },"required":["segmentId","reason"],"additionalProperties":false}}\
            },"required":["score"],"additionalProperties":false}
            """.strip();
}
