package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The recurring-term reading's strict response schema (15i.N7): per window the quote first, then its usage from a
 * closed list; per term the number of senses the windows show and the one rendering that fits every window. The quote
 * comes before the label so the model copies its evidence before it judges, as the name reading does.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LexiconReadingSchema {

    /**
     * At most four windows (the most the dial's plan shows), each quote at most 200 characters so a looping reply
     * cannot run on.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "windows":{"type":"array","maxItems":4,"items":{"type":"object","properties":{\
            "window":{"type":"integer"},\
            "quote":{"type":"string","maxLength":200},\
            "usage":{"type":"string","enum":["term","word","name"]}},\
            "required":["window","quote","usage"],\
            "additionalProperties":false}},\
            "senses":{"type":"integer"},\
            "rendering":{"type":"string"}},\
            "required":["windows","senses","rendering"],\
            "additionalProperties":false}""";
}
