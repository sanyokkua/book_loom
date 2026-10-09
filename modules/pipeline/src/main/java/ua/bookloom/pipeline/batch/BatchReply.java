package ua.bookloom.pipeline.batch;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A batch reply read per id.
 *
 * @param readable whether the reply held any entry at all; a refusal, an apology or an empty reply is not readable
 * @param outcomes one outcome for each expected id in the batch's order, then one for each extra id
 */
public record BatchReply(boolean readable, List<ItemOutcome> outcomes) {

    /** Copies the outcomes. */
    public BatchReply {
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    }

    /**
     * The reply to a batch that got no usable answer — a failed call or an empty one — so every id falls back.
     *
     * @param ids the batch's expected ids in order; never null
     * @return a reply that is not readable, with every id missing
     */
    public static BatchReply unreadable(final List<String> ids) {
        return new BatchReply(
                false,
                ids.stream().map(id -> ItemOutcome.bare(id, ItemStatus.MISSING)).toList());
    }

    /** The outcome of an id, or empty when the reply and the batch never named it. */
    public Optional<ItemOutcome> outcome(final String id) {
        return outcomes.stream().filter(outcome -> outcome.id().equals(id)).findFirst();
    }

    /** The expected ids whose targets may be used, in batch order; never null. */
    public List<String> acceptedIds() {
        return outcomes.stream()
                .filter(ItemOutcome::isAccepted)
                .map(ItemOutcome::id)
                .toList();
    }

    /** The expected ids that must fall back to a single-segment draft, in batch order; never null. */
    public List<String> failingIds() {
        return outcomes.stream()
                .filter(outcome -> outcome.status() != ItemStatus.EXTRA && !outcome.isAccepted())
                .map(ItemOutcome::id)
                .toList();
    }

    /** How many outcomes have {@code status}. */
    public long count(final ItemStatus status) {
        return outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    /** Whether any item of the reply reported a key-term rendering. */
    public boolean hasTerms() {
        return outcomes.stream().anyMatch(outcome -> !outcome.terms().isEmpty());
    }

    /** Whether every expected id is accepted and no id is extra. */
    public boolean isClean() {
        return failingIds().isEmpty() && count(ItemStatus.EXTRA) == 0;
    }
}
