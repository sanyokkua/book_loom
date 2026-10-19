package ua.bookloom.pipeline.typography;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The typography pass's answer for one text.
 *
 * @param text the normalised text; the input itself when nothing needed fixing
 * @param apostrophes how many straight apostrophes inside words became typographic
 * @param ellipses how many three-dot ellipses became one character
 * @param quotes how many straight quote marks became the language's marks
 * @param spaces how many spaces before a punctuation mark were removed
 * @param speech the source-aware speech rewrites that fired, in the order they ran; each kind once with its count
 */
public record Normalisation(String text, int apostrophes, int ellipses, int quotes, int spaces, List<Tally> speech) {

    /**
     * One kind of speech rewrite and how often it fired.
     *
     * @param label the words that name the rewrite in the note, printed after the count
     * @param count how many times it fired; positive
     */
    public record Tally(String label, int count) {}

    /** Rejects a missing text or tally list. */
    public Normalisation {
        Objects.requireNonNull(text, "text");
        speech = List.copyOf(speech);
    }

    /**
     * Whether the pass changed anything.
     *
     * @return {@code true} when at least one mark or space was changed
     */
    public boolean isChanged() {
        return apostrophes + ellipses + quotes + spaces > 0 || !speech.isEmpty();
    }

    /**
     * The one-line note a review shows beside a normalised segment.
     *
     * @return the note naming each kind of change with its count; empty when nothing changed
     */
    public String note() {
        if (!isChanged()) {
            return "";
        }
        final List<String> parts = new ArrayList<>();
        add(parts, apostrophes, "apostrophe", "apostrophes");
        add(parts, ellipses, "ellipsis", "ellipses");
        add(parts, quotes, "quote mark", "quote marks");
        add(parts, spaces, "space", "spaces");
        speech.forEach(tally -> parts.add(tally.count() + " " + tally.label()));
        return "Typography normalised: " + String.join(", ", parts) + ".";
    }

    private static void add(final List<String> parts, final int count, final String one, final String many) {
        if (count > 0) {
            parts.add(count + " " + (count == 1 ? one : many));
        }
    }
}
