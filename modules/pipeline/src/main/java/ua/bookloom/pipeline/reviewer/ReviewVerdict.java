package ua.bookloom.pipeline.reviewer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * One batch's reviewer outcome: an answer per segment, or the fact that the reviewer gave none.
 *
 * @param items the answers whose label fell inside {@code s1}..{@code sk}, each segment at most once
 * @param readable false when the reply could not be read at all
 * @param unavailableBecause the error a call that never answered ended with — a timeout after the provider's own
 *     retries and the retry without a response format — or null when the reviewer answered
 */
public record ReviewVerdict(
        List<ReviewItem> items, boolean readable, @Nullable AppError unavailableBecause) {

    /** Copies the items and rejects a missing list. */
    public ReviewVerdict {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    /**
     * A reply the reviewer wrote and the app could read.
     *
     * @param items the per-segment answers
     * @return a readable verdict
     */
    public static ReviewVerdict answered(final List<ReviewItem> items) {
        return new ReviewVerdict(items, true, null);
    }

    /**
     * A reply that could not be read.
     *
     * @return a verdict with no answers that is not readable
     */
    public static ReviewVerdict unreadable() {
        return new ReviewVerdict(List.of(), false, null);
    }

    /**
     * A call that never answered, so its segments are decided by the quality checks alone and flagged.
     *
     * @param cause the error the call ended with; never null
     * @return an unreadable verdict that names {@code cause}
     */
    public static ReviewVerdict unavailable(final AppError cause) {
        return new ReviewVerdict(List.of(), false, Objects.requireNonNull(cause, "cause"));
    }

    /**
     * Whether the reviewer never answered.
     *
     * @return {@code true} when this verdict stands for a call that timed out
     */
    public boolean isUnavailable() {
        return unavailableBecause != null;
    }

    /**
     * The answer about one segment. A reader that listed only the segments needing work is read as saying nothing
     * about the others, which is the same as {@code ok}.
     *
     * @param segmentId the segment
     * @return its item, or empty when the reply held none for it
     */
    public Optional<ReviewItem> itemFor(final String segmentId) {
        return items.stream().filter(item -> item.segmentId().equals(segmentId)).findFirst();
    }

    /**
     * Joins the answers of a second pass to this first pass's. A segment keeps a rewrite either pass asked for, the
     * first pass's before the second's; otherwise it gets the first pass's edits followed by the second's, which the
     * applier verifies one after another against the text the earlier ones left.
     *
     * @param second the second pass's verdict; never null
     * @return the joined verdict, or this one when the second pass did not answer or could not be read
     */
    public ReviewVerdict followedBy(final ReviewVerdict second) {
        Objects.requireNonNull(second, "second");
        if (!second.readable) {
            return this;
        }
        final Set<String> segmentIds = new LinkedHashSet<>();
        items.forEach(item -> segmentIds.add(item.segmentId()));
        second.items.forEach(item -> segmentIds.add(item.segmentId()));
        final List<ReviewItem> joined = new ArrayList<>();
        for (final String segmentId : segmentIds) {
            joined.add(join(
                    segmentId,
                    itemFor(segmentId).orElse(null),
                    second.itemFor(segmentId).orElse(null)));
        }
        return answered(joined);
    }

    private static ReviewItem join(
            final String segmentId, @Nullable final ReviewItem first, @Nullable final ReviewItem second) {
        if (first != null && first.status() == ReviewStatus.REWRITE) {
            return first;
        }
        if (second != null && second.status() == ReviewStatus.REWRITE) {
            return second;
        }
        final List<ReviewEdit> edits = new ArrayList<>();
        if (first != null) {
            edits.addAll(first.edits());
        }
        if (second != null) {
            edits.addAll(second.edits());
        }
        return edits.isEmpty() ? ReviewItem.ok(segmentId) : new ReviewItem(segmentId, ReviewStatus.EDITS, edits, null);
    }
}
