package ua.bookloom.document.mask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.PlaceholderRepair;

/**
 * Repairs a target's placeholder tokens without a model: drops a token the source does not hold (or holds fewer
 * times), and puts each missing token back at the position of the target that looks most like the one it held in the
 * source — the same kind of boundary (a word's start or end, a quote, a drop cap's first letter), nearest the source
 * position scaled to the target's length. The result is returned only when it passes {@link PlaceholderGate} as a
 * whole, so the repair can never let through what the gate refuses.
 *
 * <p>This is not the gate: the gate still reports and never fixes. This is the caller's deterministic second chance,
 * tried before (and after) a model repair call, because a small model that drops one token of a pair usually got
 * every word right.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenRepair {

    /**
     * The nearest positions tried per kind of boundary: one far from where the source puts the token is a poor place
     * anyway, and a bound keeps a long paragraph with many refused tokens from costing seconds on the run's thread.
     */
    private static final int MAX_CANDIDATES = 48;

    /** What sits on one side of a position in plain text. */
    private enum Side {
        EDGE,
        SPACE,
        WORD,
        PUNCT
    }

    /**
     * A position between two characters of the plain text, or between a token and its neighbour.
     *
     * @param index the position in the text with tokens
     * @param offset the position in the text without tokens
     * @param before what stands just before {@code offset}
     * @param after what stands just after {@code offset}
     * @param wordBack how many word characters run back from {@code offset}
     * @param wordBefore whether the run of non-space characters ending at {@code offset} holds a word character
     * @param wordAfter whether the run of non-space characters starting at {@code offset} holds a word character
     */
    private record Slot(
            int index, int offset, Side before, Side after, int wordBack, boolean wordBefore, boolean wordAfter) {}

    /**
     * Repairs {@code target} against {@code expectedMasked}.
     *
     * @param expectedMasked the segment's masked form; never null
     * @param target the refused target, the segment's own tokens in place; never null
     * @param pairs the segment's pairs; never null
     * @param lineBreakTokens the segment's line-break tokens; never null
     * @param mode how much of the target's own placement is kept; never null
     * @return the repaired target if one passes the gate, or empty if no placement does
     */
    public static Optional<String> repair(
            String expectedMasked,
            String target,
            List<PlaceholderPair> pairs,
            List<String> lineBreakTokens,
            PlaceholderRepair mode) {
        Objects.requireNonNull(expectedMasked, "expectedMasked");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(lineBreakTokens, "lineBreakTokens");
        Objects.requireNonNull(mode, "mode");
        log.debug(
                "Repairing placeholders mode={} expected={} observed={} targetLength={}",
                mode,
                Placeholders.tokensOf(expectedMasked),
                Placeholders.tokensOf(target),
                target.length());
        final String kept =
                switch (mode) {
                    case RESTORE_MISSING -> dropTokens(target, Placeholders.multisetOf(expectedMasked));
                    case REWRAP_ALL -> dropTokens(target, Map.of());
                };
        final @Nullable String placed = placeMissing(expectedMasked, kept, pairs);
        return verdict(expectedMasked, placed, pairs, lineBreakTokens);
    }

    private static Optional<String> verdict(
            String expectedMasked, @Nullable String placed, List<PlaceholderPair> pairs, List<String> lineBreakTokens) {
        if (placed == null) {
            log.debug("Placeholder repair found no valid position for a missing token");
            return Optional.empty();
        }
        final GateOutcome outcome = PlaceholderGate.compare(expectedMasked, placed, pairs, lineBreakTokens);
        log.debug("Placeholder repair gate passed={} rule={}", outcome.matches(), outcome.failedRule());
        if (log.isTraceEnabled()) {
            log.trace("Placeholder repair result={}", placed);
        }
        return outcome.matches() ? Optional.of(placed) : Optional.empty();
    }

    /**
     * Keeps each token while {@code allowed} still has a count for it and drops every other occurrence, joining the
     * spaces a dropped token stood between into one.
     */
    private static String dropTokens(String text, Map<String, Integer> allowed) {
        final Map<String, Integer> remaining = new HashMap<>(allowed);
        final Matcher matcher = Placeholders.matcher(text);
        final StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            out.append(text, cursor, matcher.start());
            cursor = matcher.end();
            if (remaining.getOrDefault(matcher.group(), 0) > 0) {
                remaining.merge(matcher.group(), -1, Integer::sum);
                out.append(matcher.group());
            } else {
                cursor = joinSpaces(out, text, cursor);
            }
        }
        final String kept = out.append(text, cursor, text.length()).toString();
        log.debug("Placeholder repair kept tokens={}", Placeholders.tokensOf(kept));
        return kept;
    }

    /** Where to continue after a dropped token: past one of two spaces it stood between, or at its end. */
    private static int joinSpaces(StringBuilder out, String text, int cursor) {
        if (!endsWithSpace(out)) {
            return cursor;
        }
        if (cursor == text.length()) {
            out.setLength(out.length() - 1);
            return cursor;
        }
        return Character.isWhitespace(text.charAt(cursor)) ? cursor + 1 : cursor;
    }

    private static boolean endsWithSpace(StringBuilder out) {
        return !out.isEmpty() && Character.isWhitespace(out.charAt(out.length() - 1));
    }

    private static @Nullable String placeMissing(String expectedMasked, String kept, List<PlaceholderPair> pairs) {
        final Map<String, Integer> present = Placeholders.multisetOf(kept);
        String working = kept;
        for (final String token : Placeholders.tokensOf(expectedMasked)) {
            if (present.getOrDefault(token, 0) > 0) {
                present.merge(token, -1, Integer::sum);
                continue;
            }
            final @Nullable String placed = place(expectedMasked, working, token, pairs);
            if (placed == null) {
                return null;
            }
            working = placed;
        }
        return working;
    }

    private static @Nullable String place(
            String expectedMasked, String working, String token, List<PlaceholderPair> pairs) {
        final Slot source = sourceSlot(expectedMasked, token);
        final List<Slot> slots = slotsOf(working);
        final int sourceLength = plainLength(expectedMasked);
        final int targetLength = plainLength(working);
        final int guess = (int) Math.round(source.offset() * (double) targetLength / Math.max(1, sourceLength));
        final boolean opens = pairs.stream().anyMatch(pair -> pair.open().equals(token));
        for (final Predicate<Slot> tier : tiers(source, opens)) {
            final @Nullable String placed =
                    firstValid(expectedMasked, working, token, pairs, slots, tier, guess, opens);
            if (placed != null) {
                return placed;
            }
        }
        return null;
    }

    /**
     * The source's own kind of boundary first, then a word edge of the right side, then any position that does not cut
     * a word — a word is cut only where the source cut one, as a drop cap does.
     */
    private static List<Predicate<Slot>> tiers(Slot source, boolean opens) {
        final boolean midWord = source.before() == Side.WORD && source.after() == Side.WORD;
        final Predicate<Slot> sameShape = slot -> slot.before() == source.before()
                && slot.after() == source.after()
                && slot.wordBefore() == source.wordBefore()
                && slot.wordAfter() == source.wordAfter()
                && (!midWord || slot.wordBack() == source.wordBack());
        final Predicate<Slot> wordEdge = opens
                ? slot -> isText(slot.after()) && slot.before() != Side.WORD
                : slot -> isText(slot.before()) && slot.after() != Side.WORD;
        return List.of(sameShape, wordEdge, slot -> slot.before() != Side.WORD || slot.after() != Side.WORD);
    }

    private static boolean isText(Side side) {
        return side == Side.WORD || side == Side.PUNCT;
    }

    // A closing token prefers the earlier of two equally near positions — nearer its own opening token — and an
    // opening token the later one.
    private static @Nullable String firstValid(
            String expectedMasked,
            String working,
            String token,
            List<PlaceholderPair> pairs,
            List<Slot> slots,
            Predicate<Slot> tier,
            int guess,
            boolean opens) {
        final Comparator<Slot> nearest = Comparator.comparingInt(slot -> Math.abs(slot.offset() - guess));
        final Comparator<Slot> order = nearest.thenComparingInt(slot -> opens ? -slot.index() : slot.index());
        final List<Slot> ordered =
                slots.stream().filter(tier).sorted(order).limit(MAX_CANDIDATES).toList();
        for (final Slot slot : ordered) {
            final String candidate = working.substring(0, slot.index()) + token + working.substring(slot.index());
            if (isValidSoFar(expectedMasked, candidate, pairs)) {
                log.debug("Placed token {} at offset {} (guess {})", token, slot.offset(), guess);
                return candidate;
            }
        }
        return null;
    }

    /**
     * The gate's rules over the pairs whose two tokens are both present already: they nest, each still holds the
     * text it held, and text stays outside them when it did in the source.
     */
    private static boolean isValidSoFar(String expectedMasked, String candidate, List<PlaceholderPair> pairs) {
        final Map<String, Integer> present = Placeholders.multisetOf(candidate);
        final List<PlaceholderPair> complete = pairs.stream()
                .filter(pair -> present.containsKey(pair.open()) && present.containsKey(pair.close()))
                .toList();
        if (!PairStructure.isProperlyNested(candidate, complete)) {
            return false;
        }
        for (final PlaceholderPair pair : complete) {
            if (PairStructure.holdsText(expectedMasked, pair) && !PairStructure.holdsText(candidate, pair)) {
                return false;
            }
        }
        return !PairStructure.holdsTextOutsidePairs(expectedMasked, pairs)
                || PairStructure.holdsTextOutsidePairs(candidate, complete);
    }

    private static Slot sourceSlot(String expectedMasked, String token) {
        final int index = expectedMasked.indexOf(token);
        return slotsOf(expectedMasked).stream()
                .filter(slot -> slot.index() == index)
                .findFirst()
                .orElseThrow();
    }

    private static int plainLength(String text) {
        return Placeholders.matcher(text).replaceAll("").length();
    }

    /** Every position of {@code text} that is not inside a token, with what its plain text holds on either side. */
    private static List<Slot> slotsOf(String text) {
        final String plain = Placeholders.matcher(text).replaceAll("");
        final Map<Integer, Integer> tokenEnds = new HashMap<>();
        final Matcher matcher = Placeholders.matcher(text);
        while (matcher.find()) {
            tokenEnds.put(matcher.start(), matcher.end());
        }
        final List<Slot> slots = new ArrayList<>();
        int index = 0;
        int offset = 0;
        while (index <= text.length()) {
            slots.add(slotAt(plain, index, offset));
            final Integer tokenEnd = tokenEnds.get(index);
            if (tokenEnd != null) {
                index = tokenEnd;
            } else {
                index++;
                offset++;
            }
        }
        return slots;
    }

    private static Slot slotAt(String plain, int index, int offset) {
        final Side before = offset == 0 ? Side.EDGE : sideOf(plain.charAt(offset - 1));
        final Side after = offset >= plain.length() ? Side.EDGE : sideOf(plain.charAt(offset));
        int wordBack = 0;
        while (offset - wordBack > 0 && sideOf(plain.charAt(offset - wordBack - 1)) == Side.WORD) {
            wordBack++;
        }
        return new Slot(
                index,
                offset,
                before,
                after,
                wordBack,
                chunkHasWord(plain, offset, -1),
                chunkHasWord(plain, offset, 1));
    }

    /** Whether the run of non-space characters beside {@code offset}, backwards or forwards, holds a word character. */
    private static boolean chunkHasWord(String plain, int offset, int direction) {
        int at = direction < 0 ? offset - 1 : offset;
        while (at >= 0 && at < plain.length() && sideOf(plain.charAt(at)) != Side.SPACE) {
            if (sideOf(plain.charAt(at)) == Side.WORD) {
                return true;
            }
            at += direction;
        }
        return false;
    }

    private static Side sideOf(char character) {
        if (Character.isWhitespace(character) || Character.isSpaceChar(character)) {
            return Side.SPACE;
        }
        return Character.isLetterOrDigit(character) ? Side.WORD : Side.PUNCT;
    }
}
