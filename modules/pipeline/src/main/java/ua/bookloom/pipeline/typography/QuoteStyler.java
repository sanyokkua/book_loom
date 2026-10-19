package ua.bookloom.pipeline.typography;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * Rewrites straight {@code "} marks as the language's quote pairs: the primary pair at the top level, the nested pair
 * inside it. Which side a mark is comes from its neighbours and from the pairs already open, so a mark whose side
 * cannot be told — a stray one, an inch sign — stays as it is rather than be guessed. Marks that are already
 * typographic are only counted, never changed, which makes a second pass change nothing.
 */
@Slf4j
final class QuoteStyler {

    private static final char STRAIGHT = '"';
    private static final String OPENING_PUNCTUATION = "([{";
    private static final int NONE = -1;

    private final String text;
    private final List<QuotePair> pairs;
    private final boolean[] isToken;
    private final Deque<QuotePair> open = new ArrayDeque<>();
    private final StringBuilder out;
    private int lastVisible = NONE;
    private int changed;

    private QuoteStyler(final String text, final List<QuotePair> pairs) {
        this.text = text;
        this.pairs = pairs;
        this.isToken = tokenMap(text);
        this.out = new StringBuilder(text.length());
    }

    static Edit apply(final String text, final List<QuotePair> pairs) {
        if (pairs.isEmpty() || text.indexOf(STRAIGHT) < 0) {
            return new Edit(text, 0);
        }
        final QuoteStyler styler = new QuoteStyler(text, pairs);
        styler.run();
        log.debug("Quote marks restyled straight={} pairs={}", styler.changed, pairs);
        return new Edit(styler.out.toString(), styler.changed);
    }

    private static boolean[] tokenMap(final String text) {
        final boolean[] map = new boolean[text.length()];
        final var tokens = Tokens.matcher(text);
        while (tokens.find()) {
            Arrays.fill(map, tokens.start(), tokens.end(), true);
        }
        return map;
    }

    private void run() {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (isToken[i]) {
                out.append(c);
            } else {
                final char written = c == STRAIGHT ? styled(i) : track(c);
                out.append(written);
                lastVisible = written;
            }
        }
    }

    private char track(final char c) {
        for (final QuotePair pair : pairs) {
            if (pair.open() == c) {
                open.push(pair);
            } else if (pair.close() == c && pair.equals(open.peek())) {
                open.pop();
            }
        }
        return c;
    }

    private char styled(final int index) {
        final int next = visibleAfter(index);
        if (isOpeningContext(next)) {
            return opened();
        }
        if (isClosingContext(next) && !open.isEmpty()) {
            changed++;
            return open.pop().close();
        }
        log.trace("Straight quote at {} left: its side is not clear", index);
        return STRAIGHT;
    }

    private char opened() {
        final int depth = open.size();
        if (depth >= pairs.size()) {
            return STRAIGHT;
        }
        final QuotePair pair = pairs.get(depth);
        open.push(pair);
        changed++;
        return pair.open();
    }

    private boolean isOpeningContext(final int next) {
        return (lastVisible == NONE || isOpener(lastVisible)) && next != NONE && !Character.isWhitespace(next);
    }

    private boolean isClosingContext(final int next) {
        return lastVisible != NONE && !isOpener(lastVisible) && (next == NONE || !Character.isLetterOrDigit(next));
    }

    private boolean isOpener(final int c) {
        return Character.isWhitespace(c)
                || OPENING_PUNCTUATION.indexOf(c) >= 0
                || Character.getType(c) == Character.DASH_PUNCTUATION
                || pairs.stream().anyMatch(pair -> pair.open() == c);
    }

    private int visibleAfter(final int index) {
        for (int i = index + 1; i < text.length(); i++) {
            if (!isToken[i]) {
                return text.charAt(i);
            }
        }
        return NONE;
    }
}
