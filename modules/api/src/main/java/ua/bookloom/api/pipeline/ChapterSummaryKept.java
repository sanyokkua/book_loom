package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * A finished chapter's summary passed the code gate and was stored, so the window can list it. Its text is book text:
 * {@link #toString()} leaves it out, so an event logged whole never carries it above TRACE.
 *
 * @param chapterKey the chapter's key, its first segment's unit id and position; never null
 * @param chapter the chapter's place in the book, from 1
 * @param sentences how many sentences were kept, 1 to 3
 * @param text the kept summary in the target language; never blank
 */
public record ChapterSummaryKept(String chapterKey, int chapter, int sentences, String text) implements JobEvent {

    /** The most sentences a kept summary holds. */
    public static final int MAX_SENTENCES = 3;

    /** Rejects a summary no chapter could have kept. */
    public ChapterSummaryKept {
        Objects.requireNonNull(chapterKey, "chapterKey");
        Objects.requireNonNull(text, "text");
        if (chapter < 1) {
            throw new IllegalArgumentException("chapter must be 1 or more, but was " + chapter);
        }
        if (sentences < 1 || sentences > MAX_SENTENCES) {
            throw new IllegalArgumentException("sentences must be within 1.." + MAX_SENTENCES + ": " + sentences);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    @Override
    public String toString() {
        return "ChapterSummaryKept[chapterKey=" + chapterKey + ", chapter=" + chapter + ", sentences=" + sentences
                + "]";
    }
}
