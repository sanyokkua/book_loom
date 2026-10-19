package ua.bookloom.api.pipeline;

import java.util.Map;
import java.util.Objects;

/**
 * What the model steps of a consistency pass came to, beyond the segments they changed, so a report can say how much
 * was checked and why an answer was not kept. Each retried segment lands in exactly one of improved, kept, refused or
 * skipped; each paragraph checked against its neighbours in exactly one of fixed (counted by the pass's own fix
 * count), unchanged, refused or skipped.
 *
 * @param retriedImproved flagged or doubted segments whose fresh draft was better and replaced the old text
 * @param retriedKept segments whose fresh draft passed but was no better, so the old text stayed
 * @param neighbourUnchanged paragraphs the check against their neighbours found nothing to change in
 * @param refused answers of either step that broke a rule, counted by the rule's name ({@code quotes},
 *     {@code sentences}, {@code worse}, {@code checks}…); never null, empty when none was refused
 * @param skipped segments whose call failed (a timeout, an unreachable provider), so they were left as they were
 */
public record ConsistencyChecks(
        int retriedImproved, int retriedKept, int neighbourUnchanged, Map<String, Integer> refused, int skipped) {

    /** The checks of a pass with no model step. */
    public static final ConsistencyChecks NONE = new ConsistencyChecks(0, 0, 0, Map.of(), 0);

    /** Rejects a negative count and copies the refusals. */
    public ConsistencyChecks {
        refused = Map.copyOf(Objects.requireNonNull(refused, "refused"));
        if (retriedImproved < 0 || retriedKept < 0 || neighbourUnchanged < 0 || skipped < 0) {
            throw new IllegalArgumentException("no count may be negative");
        }
        if (refused.values().stream().anyMatch(count -> count < 1)) {
            throw new IllegalArgumentException("a refusal count must be positive: " + refused);
        }
    }

    /**
     * How many answers were refused, whatever the rule.
     *
     * @return the sum of {@link #refused()}; zero when none was
     */
    public int refusedTotal() {
        return refused.values().stream().mapToInt(Integer::intValue).sum();
    }
}
