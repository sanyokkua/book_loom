package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The consistency check's response schema: either {@code unchanged} or the corrected {@code target}. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConsistencySchema {

    /**
     * Neither key is required, because the answer is one or the other; the reader accepts the object that holds only
     * {@code unchanged: true} or the one that holds a {@code target}. Flat, with no {@code additionalProperties}, as
     * {@link BatchSchema} is, so a constrained decoder is not stalled by it.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{"unchanged":{"type":"boolean"},"target":{"type":"string"}}}
            """.strip();
}
