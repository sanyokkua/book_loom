package ua.bookloom.api.project;

/**
 * How serious a {@link QaFinding} is
 * ({@code specs/quality-gates/spec.md} "Record each segment's findings for review and repair").
 */
public enum Severity {

    /** A minor issue that does not by itself flag the segment. */
    LOW,

    /** A notable issue worth a reviewer's attention. */
    MEDIUM,

    /** A serious issue that typically flags the segment. */
    HIGH
}
