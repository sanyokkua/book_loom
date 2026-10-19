package ua.bookloom.pipeline.reviewer;

import java.util.Optional;
import java.util.Set;

/**
 * Runs the deterministic checks on a whole candidate, so an edit is judged by what its result does and not by what
 * the reviewer says it does. Supplied by the quality loop, which owns the placeholder gate and the checks.
 */
@FunctionalInterface
public interface CandidateChecker {

    /**
     * Checks one candidate in masked form.
     *
     * @param maskedCandidate the whole candidate text, protected tokens in place; never null
     * @return the names of the checks that block acceptance of it, empty when none does; or empty when a hard gate
     *     refused the candidate outright
     */
    Optional<Set<String>> blockersOf(String maskedCandidate);
}
