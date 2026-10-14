package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The term reading's structured response schemas (15i.N4, 15i.N5): the quote first, then its labels from closed lists,
 * so the model copies its evidence before it judges and cannot answer outside the lists the code reads.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermReadingSchema {

    /**
     * A response object holding the readings of one term's windows, at most six as a call shows, each quote at most
     * 200 characters so a looping reply cannot run on, and the term's two flags after its windows.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "windows":{"type":"array","maxItems":6,"items":{"type":"object","properties":{\
            "window":{"type":"integer"},\
            "quote":{"type":"string","maxLength":200},\
            "usage":{"type":"string","enum":["name","word","part-of-name","title"]},\
            "kind":{"type":"string","enum":["person","place","organisation","thing","other"]},\
            "gender":{"type":"string","enum":["male","female","none"]}},\
            "required":["window","quote","usage","kind","gender"],\
            "additionalProperties":false}},\
            "speaking":{"type":"boolean"},\
            "brand":{"type":"boolean"}},\
            "required":["windows","speaking","brand"],\
            "additionalProperties":false}""";

    /**
     * The summary's reply at Max (15i.N5): the words that show best how the book uses the term, quoted from one window
     * the call showed, then the usage and the kind they show.
     */
    public static final String SUMMARY = """
            {"type":"object","properties":{\
            "quote":{"type":"string","maxLength":200},\
            "usage":{"type":"string","enum":["name","word","part-of-name","title"]},\
            "kind":{"type":"string","enum":["person","place","organisation","thing","other"]}},\
            "required":["quote","usage","kind"],\
            "additionalProperties":false}""";
}
