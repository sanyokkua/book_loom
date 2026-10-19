package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The glossary review's structured response schema. Every field is required and every value is from a closed list, so
 * a reply is either a verdict per term or unreadable, and the item count is capped at one batch so a looping reply
 * cannot run on. {@code evidence} stays a flat string: the quote that backs a "not-a-name" verdict, empty for the
 * others, and checked against the windows in code; {@code windows} are the numbers of the windows a type and gender
 * rest on, and a gender stands only when one of them shows it (15h.A2).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewTermsSchema {

    /** A response object holding one verdict per reviewed term. */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "verdicts":{"type":"array","maxItems":40,"items":{"type":"object","properties":{\
            "term":{"type":"string","maxLength":120},\
            "verdict":{"type":"string","enum":["name","term","not-a-name"]},\
            "type":{"type":"string","enum":["person","place","org","term","title","other"]},\
            "gender":{"type":"string","enum":["male","female","neuter","unknown"]},\
            "windows":{"type":"array","maxItems":6,"items":{"type":"integer"}},\
            "evidence":{"type":"string","maxLength":240}\
            },"required":["term","verdict","type","gender","windows","evidence"],"additionalProperties":false}}\
            },"required":["verdicts"],"additionalProperties":false}
            """.strip();
}
