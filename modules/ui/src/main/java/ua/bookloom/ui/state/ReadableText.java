package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Cuts a masked text into the pieces the review panel draws: plain words, a formatting token as one piece, and the
 * words a finding quotes as evidence. Kept free of JavaFX so the cutting rules are tested without a scene.
 *
 * <p>A token is never split, even inside evidence; a quote that is not in the text marks nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReadableText {

    private static final Pattern TOKEN = Pattern.compile("⟦(g\\d+)⟧");

    /** What a piece is. */
    public enum Kind {
        /** Words of the book. */
        PLAIN,
        /** A formatting token; the piece's text is its name without the brackets, such as {@code g0}. */
        TOKEN,
        /** Words a finding quoted, found in the text. */
        EVIDENCE
    }

    /**
     * One run of the cut text.
     *
     * @param kind what the run is
     * @param text the run's words, or a token's bare name
     */
    public record Piece(Kind kind, String text) {

        /** Rejects a missing component. */
        public Piece {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * Cuts a text.
     *
     * @param text the masked text; never null
     * @param quotes the words findings quote; never null, each marked at its first occurrence
     * @return the pieces in order; empty for an empty text; their texts, with each token put back in brackets, join
     *     to the original
     */
    public static List<Piece> cut(final String text, final List<String> quotes) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quotes, "quotes");
        final boolean[] marked = marks(text, quotes);
        final List<Piece> pieces = new ArrayList<>();
        final Matcher token = TOKEN.matcher(text);
        int at = 0;
        while (token.find()) {
            addPlain(pieces, text, marked, at, token.start());
            pieces.add(new Piece(Kind.TOKEN, token.group(1)));
            at = token.end();
        }
        addPlain(pieces, text, marked, at, text.length());
        return pieces;
    }

    /**
     * Whether a text holds anything the readable view draws differently from the plain text.
     *
     * @param text the masked text; never null
     * @param quotes the quoted words; never null
     * @return {@code true} if it holds a token or one of the quotes
     */
    public static boolean needsView(final String text, final List<String> quotes) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(quotes, "quotes");
        return TOKEN.matcher(text).find()
                || quotes.stream().anyMatch(quote -> !quote.isEmpty() && text.contains(quote));
    }

    private static boolean[] marks(final String text, final List<String> quotes) {
        final boolean[] marked = new boolean[text.length()];
        for (final String quote : quotes) {
            final int from = quote.isEmpty() ? -1 : text.indexOf(quote);
            for (int i = Math.max(from, 0); from >= 0 && i < from + quote.length(); i++) {
                marked[i] = true;
            }
        }
        return marked;
    }

    private static void addPlain(
            final List<Piece> pieces, final String text, final boolean[] marked, final int from, final int to) {
        int start = from;
        for (int i = from; i <= to; i++) {
            if (i == to || (i > start && marked[i] != marked[start])) {
                if (i > start) {
                    pieces.add(new Piece(marked[start] ? Kind.EVIDENCE : Kind.PLAIN, text.substring(start, i)));
                }
                start = i;
            }
        }
    }
}
