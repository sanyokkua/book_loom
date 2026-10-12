package ua.bookloom.pipeline.setup;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** An invented long book for the brief's windows: every sentence carries the same quotable opening. */
final class BriefBooks {

    /** The words every sentence of the book begins with, so a quote of them is found in any window. */
    static final String QUOTE = "The rain had not stopped for three days";

    private BriefBooks() {}

    /** {@code chapters} chapters of {@code perChapter} numbered sentences each, four to a paragraph. */
    static String story(final int chapters, final int perChapter) {
        return IntStream.range(0, chapters)
                        .mapToObj(chapter -> "# Chapter " + (chapter + 1) + "\n\n" + paragraphs(chapter, perChapter))
                        .collect(Collectors.joining("\n\n"))
                + "\n";
    }

    private static String paragraphs(final int chapter, final int perChapter) {
        return IntStream.range(0, perChapter)
                .mapToObj(n -> QUOTE + ", and line " + (chapter * perChapter + n + 1) + " of the long road went on."
                        + (n % 4 == 3 ? "\n\n" : " "))
                .collect(Collectors.joining())
                .strip();
    }
}
