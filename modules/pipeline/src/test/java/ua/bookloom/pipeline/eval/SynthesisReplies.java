package ua.bookloom.pipeline.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Genre;

/**
 * The brief-synthesis answer the offline eval models give (15i.B4): a genre of the forty that holds one of the gold's
 * class words, and a plain voice and an adult audience, each citing every passage the book was read in, so the code
 * keeps them on the windows' quoted readings. A book read in one window cites passage 1, which is enough for it.
 */
final class SynthesisReplies {

    // More passages than any dial reads; the code ignores a passage that does not exist.
    private static final String ALL_PASSAGES = "[1,2,3,4,5]";

    private SynthesisReplies() {}

    /** The first of the forty genres whose English name holds one of the class words, or null when none does. */
    static @Nullable Genre inClass(final List<String> classWords) {
        return Arrays.stream(Genre.values())
                .filter(genre -> classWords.stream()
                        .anyMatch(word ->
                                genre.english().toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))))
                .findFirst()
                .orElse(null);
    }

    /**
     * What a good offline model's window reads as the genre: the listed genre the synthesis will name, so the code finds
     * the window's reading and the synthesized value saying the same.
     */
    static String readingOf(final List<String> classWords, final String fallback) {
        final Genre genre = inClass(classWords);
        return genre == null ? fallback : genre.english();
    }

    /** The synthesis reply naming {@code genre}, or an empty genre when there is none. */
    static String of(final @Nullable Genre genre) {
        return "{" + field("genre", genre == null ? "" : genre.english()) + "," + field("audience", "adult") + ","
                + field("voice", "plain") + "}";
    }

    private static String field(final String name, final String value) {
        return "\"" + name + "\":{\"basis\":{\"knowledge\":false,\"passages\":" + ALL_PASSAGES + "},\"value\":\""
                + value + "\"}";
    }
}
