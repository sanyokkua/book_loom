package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The garbled-word check's response schema. Deliberately flat — no limits and no {@code additionalProperties} — like
 * the batch draft's, because a bounded schema once stalled a structured call; the quote and the word are verified in
 * code against the text that was sent.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SuspiciousWordsSchema {

    /** A response object holding one entry per doubtful word. */
    public static final String SCHEMA = """
            {"type":"object","properties":{"words":{"type":"array","items":{"type":"object","properties":{\
            "id":{"type":"string"},"word":{"type":"string"},"quote":{"type":"string"}},\
            "required":["id","word","quote"]}}},"required":["words"]}
            """.strip();
}
