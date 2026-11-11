package ua.bookloom.api.project;

import java.util.Objects;

/**
 * One quality-gate finding recorded against a segment, for review and repair
 * ({@code specs/quality-gates/spec.md} "Record each segment's findings for review and repair").
 *
 * @param kind what kind of defect it is — {@code language}, {@code fluency}, {@code omission}, {@code glossary},
 *     {@code meaning}, {@code markup}, or the criterion a stored applied edit names
 * @param severity how serious the finding is
 * @param note a human-readable explanation of the finding
 * @param raisedBy which check raised the finding — one of {@code script}, {@code echo},
 *     {@code repetition}, {@code length}, {@code glossary}, {@code refusal}, {@code placeholder},
 *     {@code locked-term}, {@code kept-run}, {@code script-purity}, {@code quote-balance}, {@code language-identity}, {@code duplicate-word}, {@code spacing}, {@code normalised}, or {@code audit:} and a check's name when the final audit raised it; a record stored before the per-chunk reviewer was removed (ADR-0046) may also hold {@code reviewer} or {@code reviewer-edit}
 */
public record QaFinding(String kind, Severity severity, String note, String raisedBy) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public QaFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(note, "note");
        Objects.requireNonNull(raisedBy, "raisedBy");
    }
}
