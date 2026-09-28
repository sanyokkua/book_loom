package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The judge's structured response schema requested from providers. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeSchema {

    /** A response object with the chunk's overall score, advisory verdict, findings and deferrals. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "score":{"type":"number","minimum":0,"maximum":1},\
            "verdict":{"type":"string"},\
            "findings":{"type":"array","items":{"type":"object","properties":{\
            "segmentId":{"type":"string"},"type":{"type":"string"},"severity":{"type":"string"},"note":{"type":"string"}\
            },"required":["segmentId","type","severity","note"],"additionalProperties":false}},\
            "deferrals":{"type":"array","items":{"type":"object","properties":{\
            "segmentId":{"type":"string"},"reason":{"type":"string"}\
            },"required":["segmentId","reason"],"additionalProperties":false}}\
            },"required":["score"],"additionalProperties":false}
            """.strip();
}
