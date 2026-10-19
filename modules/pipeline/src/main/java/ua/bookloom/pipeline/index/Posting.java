package ua.bookloom.pipeline.index;

/**
 * One occurrence of a word.
 *
 * @param ordinal the reading-order ordinal of the segment holding it
 * @param unitId the id of that segment's unit
 * @param offset where the word starts in the segment's text, each placeholder token read as one space
 * @param sentence the index of the word's sentence within the segment
 * @param capitalised whether it starts with a capital and is not a title such as {@code Mr.}
 * @param sentenceInitial whether it starts a sentence
 * @param standalone whether it is capitalised away from a sentence start and not beside another capitalised word
 */
public record Posting(
        int ordinal,
        String unitId,
        int offset,
        int sentence,
        boolean capitalised,
        boolean sentenceInitial,
        boolean standalone) {}
