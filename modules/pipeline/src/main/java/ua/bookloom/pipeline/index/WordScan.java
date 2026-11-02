package ua.bookloom.pipeline.index;

import org.jspecify.annotations.Nullable;

/**
 * Reads the next word of a text: letters, marks and digits, joined by single apostrophes or hyphens, with a trailing
 * possessive {@code 's} left out of the word. Scanning stands in for a regular expression that repeats a group of
 * alternatives, which Java matches by recursion one frame per joined part.
 *
 * @param start where the word starts
 * @param wordEnd where the word ends, before a possessive
 * @param end where the word ends, after a possessive
 */
record WordScan(int start, int wordEnd, int end) {

    private static final String APOSTROPHES = "'’ʼ";
    private static final String HYPHENS = "-‐‑";

    /**
     * Finds the first word at or after a position.
     *
     * @param text the text to read; never null
     * @param from where to start looking
     * @return the word, or {@code null} when the text holds no more
     */
    static @Nullable WordScan next(final String text, final int from) {
        int start = from;
        while (start < text.length() && !isLetter(text, start)) {
            start += Character.charCount(text.codePointAt(start));
        }
        if (start >= text.length()) {
            return null;
        }
        int end = runEnd(text, start);
        while (end < text.length()) {
            final int next = joinedEnd(text, end);
            if (next < 0) {
                break;
            }
            end = next;
        }
        final boolean possessive = isPossessive(text, end);
        return new WordScan(start, end, possessive ? end + 2 : end);
    }

    // The end of the part a joiner at i adds, or -1 when the joiner adds none.
    private static int joinedEnd(final String text, final int i) {
        final char joiner = text.charAt(i);
        final boolean apostrophe = APOSTROPHES.indexOf(joiner) >= 0;
        if ((!apostrophe && HYPHENS.indexOf(joiner) < 0) || i + 1 >= text.length() || !isLetter(text, i + 1)) {
            return -1;
        }
        return apostrophe && isPossessive(text, i) ? -1 : runEnd(text, i + 1);
    }

    // An apostrophe at i, then s or S, then no letter: the possessive that ends a name.
    private static boolean isPossessive(final String text, final int i) {
        return i + 1 < text.length()
                && APOSTROPHES.indexOf(text.charAt(i)) >= 0
                && (text.charAt(i + 1) == 's' || text.charAt(i + 1) == 'S')
                && (i + 2 >= text.length() || !isLetter(text, i + 2));
    }

    private static int runEnd(final String text, final int from) {
        int end = from;
        while (end < text.length() && isLetter(text, end)) {
            end += Character.charCount(text.codePointAt(end));
        }
        return end;
    }

    private static boolean isLetter(final String text, final int at) {
        final int codePoint = text.codePointAt(at);
        return Character.isLetter(codePoint)
                || Character.isDigit(codePoint)
                || switch (Character.getType(codePoint)) {
                    case Character.NON_SPACING_MARK,
                            Character.COMBINING_SPACING_MARK,
                            Character.ENCLOSING_MARK,
                            Character.LETTER_NUMBER,
                            Character.OTHER_NUMBER -> true;
                    default -> false;
                };
    }
}
