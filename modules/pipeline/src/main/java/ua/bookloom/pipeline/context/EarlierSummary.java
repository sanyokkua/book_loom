package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * One earlier chapter's kept summary as block 8 carries it.
 *
 * @param chapterKey the chapter's key, which the call's snapshot stores in place of the text
 * @param chapter the chapter's number in the book, counting from 1
 * @param text the summary in the target language; never blank
 */
public record EarlierSummary(String chapterKey, int chapter, String text) {

    /** Rejects a missing part. */
    public EarlierSummary {
        Objects.requireNonNull(chapterKey, "chapterKey");
        Objects.requireNonNull(text, "text");
        if (chapter < 1) {
            throw new IllegalArgumentException("chapter must be one or more, but was " + chapter);
        }
    }
}
