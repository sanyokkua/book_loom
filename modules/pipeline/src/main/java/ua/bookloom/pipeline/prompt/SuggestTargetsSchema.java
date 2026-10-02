package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The glossary suggestion call's structured response schema. The item count is capped at one batch and a target at a
 * length no name reaches, so a looping reply is cut off; an empty target means "no suggestion".
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SuggestTargetsSchema {

    /** A response object holding one suggestion per listed term. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "suggestions":{"type":"array","maxItems":20,"items":{"type":"object","properties":{\
            "term":{"type":"string","maxLength":120},\
            "target":{"type":"string","maxLength":80},\
            "gender":{"type":"string","enum":["male","female","neuter","unknown"]}\
            },"required":["term","target","gender"],"additionalProperties":false}}\
            },"required":["suggestions"],"additionalProperties":false}
            """.strip();
}
