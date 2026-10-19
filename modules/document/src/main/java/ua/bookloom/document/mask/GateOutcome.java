package ua.bookloom.document.mask;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The result of comparing a target's placeholder multiset against a segment's masked form
 * ({@link PlaceholderGate#compare(String, String)}).
 *
 * <p>Both token lists are the whole-grammar matches <strong>in appearance order, duplicates included</strong> —
 * not the deduplicated multiset — so a caller reporting a failure (the requirement <em>Report the expected
 * placeholders in full and the observed ones within a bound</em>) names a duplicated token as many times as it
 * actually occurred, rather than collapsing it into one misleading entry. Preserving duplicates is this record's
 * own contract, and is unrelated to the volume bound {@code SafeDetails} applies downstream when rendering.
 *
 * @param matches whether the two multisets are equal, order irrelevant, counts significant
 * @param expected every token in the segment's masked form, in appearance order; never null
 * @param observed every token in the supplied target, in appearance order; never null
 * @param failedRule the rule the target broke, or {@code null} when {@code matches} is {@code true}
 */
public record GateOutcome(
        boolean matches,
        List<String> expected,
        List<String> observed,
        @Nullable GateRule failedRule) {

    /** Defensively copies both token lists into unmodifiable, order-preserving lists. */
    public GateOutcome {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");
        expected = List.copyOf(expected);
        observed = List.copyOf(observed);
    }

    /**
     * An outcome that names the multiset as the only rule checked: passing when {@code matches}, else
     * {@link GateRule#MULTISET}.
     *
     * @param matches whether the two multisets are equal
     * @param expected every token in the segment's masked form, in appearance order
     * @param observed every token in the supplied target, in appearance order
     */
    public GateOutcome(boolean matches, List<String> expected, List<String> observed) {
        this(matches, expected, observed, matches ? null : GateRule.MULTISET);
    }
}
