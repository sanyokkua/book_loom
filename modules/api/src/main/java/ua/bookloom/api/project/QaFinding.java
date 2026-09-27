package ua.bookloom.api.project;

import java.util.Objects;

/**
 * One quality-gate finding recorded against a segment, for review and repair
 * ({@code specs/quality-gates/spec.md} "Record each segment's findings for review and repair").
 *
 * @param kind the finding's kind (e.g. a hard-gate check name)
 * @param severity how serious the finding is
 * @param note a human-readable explanation of the finding
 */
public record QaFinding(String kind, Severity severity, String note) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public QaFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(note, "note");
    }
}
