package ua.bookloom.pipeline.setup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What one brief suggestion call reads: a few contiguous passages of the book's story, and the share of their letters
 * that characters speak, measured by the code so the model does not have to guess it.
 *
 * @param passages the passages in reading order, paragraphs separated by a line break; never null, none blank
 * @param dialoguePercent the share of the passages' letters inside quoted speech or dash dialogue, from 0 to 100
 */
record BookSample(List<String> passages, int dialoguePercent) {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ELISION = Pattern.compile("\\.\\.\\.|…");
    private static final Pattern EDGES = Pattern.compile("^[\\s\"'.,;:!?-]+|[\\s\"'.,;:!?-]+$");
    private static final int MAX_PERCENT = 100;

    /** Copies the passages and rejects a share that is no percentage. */
    BookSample {
        passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
        if (dialoguePercent < 0 || dialoguePercent > MAX_PERCENT) {
            throw new IllegalArgumentException("dialoguePercent " + dialoguePercent);
        }
    }

    /** The passages as the prompt shows them, each under its number. */
    String text() {
        final StringBuilder text = new StringBuilder();
        for (int index = 0; index < passages.size(); index++) {
            text.append("Passage ").append(index + 1).append(": ").append(passages.get(index));
            if (index + 1 < passages.size()) {
                text.append("\n\n");
            }
        }
        return text.toString();
    }

    /**
     * Whether a quote the model gave is the book's own words in this sample. The check forgives what a model changes
     * when it copies: letter case, runs of spaces, curly against straight quote marks and dashes, the punctuation at
     * either end, and an elision ({@code ...}) between two parts that each stand in the sample in that order.
     *
     * @param quote the model's quote; may be blank
     * @return {@code true} if every part of the quote is found in the passages, {@code false} for a blank quote or one
     *     the passages do not hold
     */
    boolean holds(final String quote) {
        Objects.requireNonNull(quote, "quote");
        final String haystack = normalized(String.join("\n", passages));
        int from = 0;
        boolean anyPart = false;
        for (final String raw : ELISION.splitAsStream(quote).toList()) {
            final String part = EDGES.matcher(normalized(raw)).replaceAll("");
            if (part.codePoints().noneMatch(Character::isLetter)) {
                continue;
            }
            final int at = haystack.indexOf(part, from);
            if (at < 0) {
                return false;
            }
            from = at + part.length();
            anyPart = true;
        }
        return anyPart;
    }

    private static String normalized(final String text) {
        final String marks =
                text.replaceAll("[‘’‚‛′`]", "'").replaceAll("[“”„‟″«»]", "\"").replaceAll("[‐‑‒–—―]", "-");
        return SPACES.matcher(marks).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
