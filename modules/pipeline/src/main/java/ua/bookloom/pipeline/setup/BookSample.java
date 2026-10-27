package ua.bookloom.pipeline.setup;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.narrator.NarratorDetector;

/**
 * What one brief suggestion call reads: one contiguous window of the book's story, and the share of its letters that
 * characters speak, measured by the code so the model does not have to guess it.
 *
 * @param passages the window's text, paragraphs separated by a line break; never null, none blank
 * @param dialoguePercent the share of the letters inside quoted speech or dash dialogue, from 0 to 100
 * @param wholeStory whether the window holds every line of the story, so one reading of it is the whole book's
 * @param languageTag the source language whose quote marks tell the characters' speech from the narration, or null for
 *     the general table
 * @param location where the window lies in the book, as the review desk names places (the first and last paragraph);
 *     empty when not known
 */
record BookSample(
        List<String> passages,
        int dialoguePercent,
        boolean wholeStory,
        @Nullable String languageTag,
        String location) {

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern LINES = Pattern.compile("\\R");
    private static final Pattern ELISION = Pattern.compile("\\.\\.\\.|…");
    private static final Pattern EDGES = Pattern.compile("^[\\s\"'.,;:!?-]+|[\\s\"'.,;:!?-]+$");
    private static final int MAX_PERCENT = 100;
    private static final String PASSAGE_LABEL = "Passage 1: ";

    /** Copies the passages and rejects a share that is no percentage. */
    BookSample {
        passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
        Objects.requireNonNull(location, "location");
        if (dialoguePercent < 0 || dialoguePercent > MAX_PERCENT) {
            throw new IllegalArgumentException("dialoguePercent " + dialoguePercent);
        }
    }

    /** A window that is not known to hold the whole story, its speech told by the general quote marks. */
    BookSample(final List<String> passages, final int dialoguePercent) {
        this(passages, dialoguePercent, false, null, "");
    }

    /** A window whose speech is told by the general quote marks. */
    BookSample(final List<String> passages, final int dialoguePercent, final boolean wholeStory) {
        this(passages, dialoguePercent, wholeStory, null, "");
    }

    /** A window whose place in the book is not known. */
    BookSample(
            final List<String> passages,
            final int dialoguePercent,
            final boolean wholeStory,
            final @Nullable String languageTag) {
        this(passages, dialoguePercent, wholeStory, languageTag, "");
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
        return holds(passages, quote);
    }

    /**
     * Whether a quote is the narrator's own words in this sample, as {@link #holds(String)} finds them once every
     * character's speech is blanked: a register is the narration's, and a line of dialogue says how a character talks.
     *
     * @param quote the model's quote; may be blank
     * @return {@code true} if every part of the quote is found in the narration, {@code false} for a blank quote, one
     *     the passages do not hold or one that is (partly) a character's speech
     */
    boolean holdsInNarration(final String quote) {
        Objects.requireNonNull(quote, "quote");
        final List<String> narration = passages.stream()
                .map(passage -> LINES.splitAsStream(passage)
                        .map(line -> NarratorDetector.narration(line, languageTag))
                        .collect(Collectors.joining("\n")))
                .toList();
        return holds(narration, quote);
    }

    private static boolean holds(final List<String> texts, final String quote) {
        final String haystack = normalized(String.join("\n", texts));
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
