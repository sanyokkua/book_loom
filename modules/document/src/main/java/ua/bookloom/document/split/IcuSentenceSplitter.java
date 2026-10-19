package ua.bookloom.document.split;

import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.document.mask.Placeholders;

/**
 * Splits a masked text at ICU sentence boundaries. Every placeholder token is neutralized to one object-replacement
 * character before ICU sees it — read raw, ICU takes {@code ⟦} followed by {@code g} for a sentence that continues,
 * so a sentence that begins or ends with inline markup would never split — and each boundary is then moved to
 * where a token belongs: after the closing or atomic tokens that end a sentence, before an opening token that
 * starts the next, and never inside a pair, whose half a piece could not restore.
 */
@Slf4j
public final class IcuSentenceSplitter implements SentenceSplitter {

    private static final char NEUTRAL = '￼';

    @Override
    public Result<List<String>> split(String masked, Segment segment, String languageTag) {
        Objects.requireNonNull(masked, "masked");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(languageTag, "languageTag");
        log.debug("Splitting segment={} language={} maskedLength={}", segment.id(), languageTag, masked.length());
        try {
            final List<String> pieces = pieces(masked, segment.pairs(), languageTag, segment.id());
            log.trace("Pieces of segment {}: {}", segment.id(), pieces);
            return Result.ok(pieces);
        } catch (Throwable t) {
            log.error("Unexpected failure splitting segment {}", segment.id(), t);
            return Result.err(AppError.of(
                    ErrorCode.internal,
                    "Something went wrong",
                    "An unexpected error occurred while splitting a paragraph into sentences.",
                    SafeDetails.empty().render(),
                    t));
        }
    }

    private static List<String> pieces(String masked, List<PlaceholderPair> pairs, String languageTag, String id) {
        final List<int[]> tokens = tokenSpans(masked);
        final List<Integer> raw = icuBoundaries(masked, tokens, languageTag);
        final Set<String> opening = new HashSet<>();
        final Set<String> closing = new HashSet<>();
        for (final PlaceholderPair pair : pairs) {
            opening.add(pair.open());
            closing.add(pair.close());
        }
        final List<Integer> kept = new ArrayList<>();
        int moved = 0;
        int inPair = 0;
        for (final int boundary : raw) {
            final int at = moveAcrossTokens(masked, tokens, boundary, opening);
            moved += at == boundary ? 0 : 1;
            if (isInsidePair(masked, tokens, at, opening, closing)) {
                inPair++;
            } else if (at > 0 && at < masked.length() && (kept.isEmpty() || at > kept.get(kept.size() - 1))) {
                kept.add(at);
            }
        }
        log.debug(
                "segment {}: boundaries found={} moved past tokens={} skipped inside pairs={} pieces={}",
                id,
                raw.size(),
                moved,
                inPair,
                kept.size() + 1);
        return cut(masked, kept);
    }

    /** Each token's start (inclusive) and end (exclusive) in {@code masked}. */
    private static List<int[]> tokenSpans(String masked) {
        final List<int[]> spans = new ArrayList<>();
        int from = 0;
        for (final String token : Placeholders.tokensOf(masked)) {
            final int start = masked.indexOf(token, from);
            spans.add(new int[] {start, start + token.length()});
            from = start + token.length();
        }
        return spans;
    }

    /** Runs ICU over the neutralized text and maps each boundary back to an offset in {@code masked}. */
    private static List<Integer> icuBoundaries(String masked, List<int[]> tokens, String languageTag) {
        final StringBuilder neutral = new StringBuilder();
        final List<Integer> maskedOffsetOf = new ArrayList<>();
        int cursor = 0;
        for (final int[] token : tokens) {
            for (; cursor < token[0]; cursor++) {
                neutral.append(masked.charAt(cursor));
                maskedOffsetOf.add(cursor);
            }
            neutral.append(NEUTRAL);
            maskedOffsetOf.add(token[0]);
            cursor = token[1];
        }
        for (; cursor < masked.length(); cursor++) {
            neutral.append(masked.charAt(cursor));
            maskedOffsetOf.add(cursor);
        }
        maskedOffsetOf.add(masked.length());
        final BreakIterator iterator = BreakIterator.getSentenceInstance(ULocale.forLanguageTag(languageTag));
        iterator.setText(neutral.toString());
        final List<Integer> boundaries = new ArrayList<>();
        for (int b = iterator.next(); b != BreakIterator.DONE; b = iterator.next()) {
            boundaries.add(maskedOffsetOf.get(b));
        }
        return boundaries;
    }

    /** Moves a boundary past closing and atomic tokens that follow it, and the whitespace after them. */
    private static int moveAcrossTokens(String masked, List<int[]> tokens, int boundary, Set<String> opening) {
        int at = boundary;
        boolean advanced = true;
        while (advanced) {
            advanced = false;
            for (final int[] token : tokens) {
                if (token[0] == at && !opening.contains(masked.substring(token[0], token[1]))) {
                    at = token[1];
                    advanced = true;
                    break;
                }
            }
        }
        if (at != boundary) {
            while (at < masked.length() && Character.isWhitespace(masked.charAt(at))) {
                at++;
            }
        }
        return at;
    }

    private static boolean isInsidePair(
            String masked, List<int[]> tokens, int offset, Set<String> opening, Set<String> closing) {
        int depth = 0;
        for (final int[] token : tokens) {
            if (token[1] > offset) {
                break;
            }
            final String text = masked.substring(token[0], token[1]);
            depth += opening.contains(text) ? 1 : closing.contains(text) ? -1 : 0;
        }
        return depth > 0;
    }

    private static List<String> cut(String masked, List<Integer> boundaries) {
        final List<String> pieces = new ArrayList<>(boundaries.size() + 1);
        int from = 0;
        for (final int boundary : boundaries) {
            pieces.add(masked.substring(from, boundary));
            from = boundary;
        }
        pieces.add(masked.substring(from));
        return pieces;
    }
}
