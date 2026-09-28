package ua.bookloom.document.mask;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderPair;

/**
 * The placeholder-multiset hard gate — the requirement <em>Compare the placeholder multiset as a hard gate before
 * restoring anything</em>. Compares the multiset of {@code ⟦gN⟧} tokens in a target against the multiset in a
 * segment's masked form; a missing, added, or duplicated token fails it.
 *
 * <p>Token order matters only for the paired tokens the segment records (a pair opens before it closes, pairs nest,
 * a pair that held text still holds text, a line break keeps its pair); every other token may move freely.
 *
 * <p>This is the one check restoring performs before touching a placeholder — it reports, it never repairs. A
 * caller that sees {@link GateOutcome#matches()} {@code false} restores nothing and alters nothing.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlaceholderGate {

    /**
     * Compares {@code target}'s placeholder multiset against {@code expectedMasked}'s, with no pair or line-break
     * rule to apply.
     *
     * @param expectedMasked the segment's masked form, the source of truth for which tokens may appear; never null
     * @param target the text to validate — a translated target, supplied back to restore; never null
     * @return the comparison outcome, carrying both token lists in full; never null
     */
    public static GateOutcome compare(String expectedMasked, String target) {
        return compare(expectedMasked, target, List.of(), List.of());
    }

    /**
     * Compares {@code target} against {@code expectedMasked}: the token multiset first, then — when the multisets
     * match — that every pair opens before it closes and nests with the others, that a pair which held text still
     * holds text, and that a line-break token keeps its innermost pair. Atomic tokens stay free to move.
     *
     * @param expectedMasked the segment's masked form; never null
     * @param target the translated text to validate; never null
     * @param pairs the segment's paired tokens; never null
     * @param lineBreakTokens the segment's line-break tokens; never null
     * @return the outcome, naming the first rule the target broke; never null
     */
    public static GateOutcome compare(
            String expectedMasked, String target, List<PlaceholderPair> pairs, List<String> lineBreakTokens) {
        Objects.requireNonNull(expectedMasked, "expectedMasked");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pairs, "pairs");
        Objects.requireNonNull(lineBreakTokens, "lineBreakTokens");
        log.debug("gate: {} pairs, {} line-break tokens", pairs.size(), lineBreakTokens.size());
        final List<String> expected = Placeholders.tokensOf(expectedMasked);
        final List<String> observed = Placeholders.tokensOf(target);
        final GateRule broken = firstBrokenRule(expectedMasked, target, pairs, lineBreakTokens);
        log.debug("gate: rule broken = {}", broken);
        return new GateOutcome(broken == null, expected, observed, broken);
    }

    private static @Nullable GateRule firstBrokenRule(
            String expectedMasked, String target, List<PlaceholderPair> pairs, List<String> lineBreakTokens) {
        if (!Placeholders.multisetOf(expectedMasked).equals(Placeholders.multisetOf(target))) {
            return GateRule.MULTISET;
        }
        if (!PairStructure.isProperlyNested(target, pairs)) {
            return GateRule.PAIR_ORDER;
        }
        for (final PlaceholderPair pair : pairs) {
            if (PairStructure.holdsText(expectedMasked, pair) && !PairStructure.holdsText(target, pair)) {
                log.debug("gate: pair {} {} was emptied", pair.open(), pair.close());
                return GateRule.EMPTIED_PAIR;
            }
        }
        final var before = PairStructure.enclosingPairs(expectedMasked, pairs, lineBreakTokens);
        final var after = PairStructure.enclosingPairs(target, pairs, lineBreakTokens);
        return before.equals(after) ? null : GateRule.LINE_BREAK;
    }
}
