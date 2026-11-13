package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The chapter-summary call's reply schema (design D10). Its {@code title} repeats the format name, because an Ollama
 * request carries the schema alone as {@code format} and names no response format, so the name still shows on both
 * dialects' wire.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChapterSummarySchema {

    /** The response-format name, the pseudo model's switch and the schema's title. */
    public static final String FORMAT_NAME = "chapter_summary";

    /** One object holding the summary as one string of two or three sentences. */
    public static final String SCHEMA = """
            {"title":"chapter_summary","type":"object","properties":{\
            "summary":{"type":"string"}\
            },"required":["summary"]}
            """.strip();
}
