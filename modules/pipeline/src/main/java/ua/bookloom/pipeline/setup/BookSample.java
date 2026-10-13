package ua.bookloom.pipeline.setup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What one brief suggestion call reads: one contiguous window of the book's story, and the share of its letters that
 * characters speak, measured by the code so the model does not have to guess it.
 *
 * @param passages the window's text, paragraphs separated by a line break; never null, none blank
 * @param dialoguePercent the share of the letters inside quoted speech or dash dialogue, from 0 to 100
 * @param wholeStory whether the window holds every line of the story, so one reading of it is the whole book's
 */
record BookSample(List<String> passages, int dialoguePercent, boolean wholeStory) {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern ELISION = Pattern.compile("\\.\\.\\.|…");
    private static final Pattern EDGES = Pattern.compile("^[\\s\"'.,;:!?-]+|[\\s\"'.,;:!?-]+$");
    private static final int MAX_PERCENT = 100;
    private static final String PASSAGE_LABEL = "Passage 1: ";

    /** Copies the passages and rejects a share that is no percentage. */
    BookSample {
        passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
        if (dialoguePercent < 0 || dialoguePercent > MAX_PERCENT) {
            throw new IllegalArgumentException("dialoguePercent " + dialoguePercent);
        }
    }

    /** A window that is not known to hold the whole story. */
    BookSample(final List<String> passages, final int dialoguePercent) {
        this(passages, dialoguePercent, false);
    }

    /** The window as the prompt shows it, under the label the prompt's rules name; empty for no window. */
    String text() {
        return passages.isEmpty() ? "" : PASSAGE_LABEL + String.join("\n", passages);
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
