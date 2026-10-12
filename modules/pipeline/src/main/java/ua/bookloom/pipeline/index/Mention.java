package ua.bookloom.pipeline.index;

/**
 * One mention of a name.
 *
 * @param name the name asked for, as given
 * @param ordinal the reading-order ordinal of the segment
 * @param start where the mention starts in the segment's text
 * @param end where it ends, exclusive and before a possessive ending
 * @param sentence the index of its sentence within the segment
 * @param narration {@code false} when it lies in speech: inside quotation marks, or in a dash-led line before the
 *     second dash
 */
public record Mention(String name, int ordinal, int start, int end, int sentence, boolean narration) {}
