package ua.bookloom.pipeline.setup;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The strict response schema of the book-knowledge question. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookKnowledgeSchema {

    /**
     * An object of the ten fields the answer holds, all required so a model that knows nothing still fills each with
     * an empty value; a model cannot leave out the {@code known} flag the verdict starts from.
     */
    public static final String SCHEMA = """
            {"type":"object","properties":{\
            "known":{"type":"boolean"},\
            "title":{"type":"string"},\
            "author":{"type":"string"},\
            "year":{"type":"integer"},\
            "genre":{"type":"string"},\
            "audience":{"type":"string"},\
            "era":{"type":"string"},\
            "narration":{"type":"string"},\
            "summary":{"type":"string"},\
            "confidence":{"type":"number"}},\
            "required":["known","title","author","year","genre","audience","era","narration","summary","confidence"],\
            "additionalProperties":false}""";
}
