package ua.bookloom.pipeline.index;

/**
 * The text around one mention.
 *
 * @param mention the mention the window is cut around
 * @param firstOrdinal the first segment that contributes a sentence
 * @param lastOrdinal the last segment that contributes a sentence
 * @param text the kept sentences in reading order: those of one segment joined by a space, segments by a line break,
 *     each with its quotation marks and a speaker's dash, so speech can be told from narration in it
 * @param markStart where the mention starts in {@code text}
 * @param markEnd where it ends in {@code text}, exclusive
 */
public record Window(Mention mention, int firstOrdinal, int lastOrdinal, String text, int markStart, int markEnd) {}
