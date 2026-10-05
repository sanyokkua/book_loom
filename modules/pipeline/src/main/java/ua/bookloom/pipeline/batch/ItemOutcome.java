package ua.bookloom.pipeline.batch;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a batch reply made of one id.
 *
 * @param id the id, expected or extra
 * @param status what the reply did with the id
 * @param target the text under the id, empty when the id is missing or duplicated
 * @param problems what the per-item checks found, empty when none ran or none failed; only an {@link ItemStatus#OK}
 *     id is checked
 * @param terms the key-term renderings the model reported for the id, as it wrote them and not yet verified; empty
 *     when it reported none
 */
public record ItemOutcome(
        String id, ItemStatus status, String target, List<ItemProblem> problems, Map<String, String> terms) {

    /** Rejects missing parts and copies the problems and terms. */
    public ItemOutcome {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(target, "target");
        problems = List.copyOf(problems);
        terms = Map.copyOf(terms);
    }

    /** An outcome that reported no terms. */
    public ItemOutcome(
            final String id, final ItemStatus status, final String target, final List<ItemProblem> problems) {
        this(id, status, target, problems, Map.of());
    }

    /** An outcome that carries no text and no problem. */
    static ItemOutcome bare(final String id, final ItemStatus status) {
        return new ItemOutcome(id, status, "", List.of());
    }

    /** This outcome with the renderings the model reported for its id. */
    ItemOutcome withTerms(final Map<String, String> reported) {
        return new ItemOutcome(id, status, target, problems, reported);
    }

    /** Whether the target may be taken for this item: its id came back once and every check passed. */
    public boolean isAccepted() {
        return status == ItemStatus.OK && problems.isEmpty();
    }
}
