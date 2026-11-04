package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;

/**
 * One target text as {@link NeverWorse} compares it: the text and what the code found in it. The caller evaluates
 * it, so the gate itself runs no check twice and reads no book.
 *
 * @param masked the target text with the document's tokens still in it
 * @param qa the text's evaluation by {@link QaEvaluator}, hard gates included
 * @param auditChecks the names of the checks the final audit doubts the text by; never null, empty when none does
 */
public record Candidate(String masked, QaResult qa, List<String> auditChecks) {

    /** Rejects a missing component and copies the list. */
    public Candidate {
        Objects.requireNonNull(masked, "masked");
        Objects.requireNonNull(qa, "qa");
        auditChecks = List.copyOf(Objects.requireNonNull(auditChecks, "auditChecks"));
    }
}
