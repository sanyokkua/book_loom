package ua.bookloom.pipeline;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.checks.QuoteConventions;
import ua.bookloom.pipeline.checks.QuotePair;

/**
 * Puts the quote marks and the dialogue dash back where a model wrote them as control codes (U+001C-U+001F and other
 * C0 codes: its way of copying « » and — that the 15g gate refused whole). A code is mapped only when its place says
 * what it is: a dash stands alone between spaces, a quote opens where a word begins and closes where one ends, and
 * the quotes pair up exactly. Anything else is not guessed — stripping the codes once deleted real quote marks — and
 * the caller keeps refusing the reply. The mapped text is not trusted: the checks and guards that read any candidate
 * still read it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ControlCharacterMapper {

    private static final QuotePair FALLBACK_QUOTES = new QuotePair('«', '»');
    private static final char EM_DASH = '—';
    private static final String OPENERS = "([{—–";
    private static final String CLOSERS = ".,;:!?)]}…—–";

    private enum Side {
        OPEN,
        CLOSE,
        NONE
    }

    /**
     * Maps the control codes of a reply text.
     *
     * @param source the non-null source text; a source that holds control characters itself makes the codes
     *     ambiguous, so none is mapped
     * @param text the non-null reply text
     * @param targetLanguage the BCP 47 tag whose quote convention the marks take, or null for the guillemets
     * @return the text with every control code written as a quote mark or a dash, or empty when some code has no
     *     clear place or the quotes do not pair up
     */
    public static Optional<String> map(final String source, final String text, @Nullable final String targetLanguage) {
        if (source.chars().anyMatch(ControlCharacters::isControl)) {
            log.debug("Control codes not mapped: the source holds control characters itself");
            return Optional.empty();
        }
        return translated(text, pairFor(targetLanguage));
    }

    private static Optional<String> translated(final String text, final QuotePair pair) {
        final StringBuilder out = new StringBuilder(text.length());
        boolean open = false;
        int quotes = 0;
        int dashes = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!ControlCharacters.isControl(c)) {
                out.append(c);
            } else if (isDash(text, i)) {
                out.append(EM_DASH);
                dashes++;
            } else {
                final Side side = sideOf(text, i, open);
                if (side == Side.NONE) {
                    log.debug("Control code at {} of {} has no clear place; not mapped", i, text.length());
                    return Optional.empty();
                }
                open = side == Side.OPEN;
                out.append(open ? pair.open() : pair.close());
                quotes++;
            }
        }
        log.debug("Control codes mapped quotes={} dashes={} pairedUp={}", quotes, dashes, !open);
        return open ? Optional.empty() : Optional.of(out.toString());
    }

    private static QuotePair pairFor(@Nullable final String targetLanguage) {
        return targetLanguage == null
                ? FALLBACK_QUOTES
                : QuoteConventions.ownLine(targetLanguage)
                        .filter(pairs -> !pairs.isEmpty())
                        .map(pairs -> pairs.getFirst())
                        .orElse(FALLBACK_QUOTES);
    }

    private static boolean isDash(final String text, final int at) {
        return isGap(before(text, at)) && isGap(after(text, at));
    }

    private static Side sideOf(final String text, final int at, final boolean open) {
        final char previous = before(text, at);
        final char next = after(text, at);
        if (!open && (isGap(previous) || OPENERS.indexOf(previous) >= 0) && !isGap(next)) {
            return Side.OPEN;
        }
        if (open && !isGap(previous) && (isGap(next) || CLOSERS.indexOf(next) >= 0)) {
            return Side.CLOSE;
        }
        return Side.NONE;
    }

    private static char before(final String text, final int at) {
        return at == 0 ? ' ' : text.charAt(at - 1);
    }

    private static char after(final String text, final int at) {
        return at + 1 == text.length() ? ' ' : text.charAt(at + 1);
    }

    private static boolean isGap(final char c) {
        return !ControlCharacters.isControl(c) && (Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
