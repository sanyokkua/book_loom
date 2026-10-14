package ua.bookloom.pipeline.checks;

import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a chapter's paragraphs are story: at least {@value #MIN_CHAPTER_SENTENCES} sentences and a paragraph of two
 * full sentences, each of three words or more and closed by its terminal mark. A converter's page, a contents list, a
 * page of praise (a blurb's attribution is a sentence with no mark) and a heading-only unit are not. The brief's samples
 * read only story chapters; the name triage asks only for the prose paragraph ({@link #hasProse}), since sections
 * between headings are often short (15i.N5: a converter's credit).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoryMeasure {

    /** A chapter with fewer sentences than this is not story. */
    public static final int MIN_CHAPTER_SENTENCES = 20;

    private static final int STORY_PARAGRAPH_SENTENCES = 2;
    private static final Pattern CLOSED = Pattern.compile("[.!?…。！？][»”\"’)]*$");

    /**
     * Whether one chapter is story.
     *
     * @param paragraphs the chapter's paragraphs as display text; never null
     * @return {@code true} if it is story, {@code false} otherwise
     */
    public static boolean isStory(final List<String> paragraphs) {
        final int sentences = paragraphs.stream()
                .mapToInt(text -> Sentences.split(text).size())
                .sum();
        final boolean hasStoryParagraph = hasProse(paragraphs);
        log.trace("Story measure: {} sentences, a story paragraph {}", sentences, hasStoryParagraph);
        return sentences >= MIN_CHAPTER_SENTENCES && hasStoryParagraph;
    }

    /**
     * Whether some paragraph holds two full sentences, each of three words or more and closed by its terminal mark —
     * what a credit page, a contents list or a title page never has, however long it is.
     *
     * @param paragraphs the paragraphs as display text; never null
     * @return {@code true} if one does, {@code false} otherwise
     */
    public static boolean hasProse(final List<String> paragraphs) {
        return paragraphs.stream().anyMatch(text -> fullSentences(text) >= STORY_PARAGRAPH_SENTENCES);
    }

    private static long fullSentences(final String paragraph) {
        return Sentences.significant(paragraph).stream()
                .filter(sentence -> CLOSED.matcher(sentence).find())
                .count();
    }
}
