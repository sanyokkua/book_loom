package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One case's measured outcome. A check that does not apply to the case is {@link Check#NA} and counts towards no
 * rate.
 *
 * @param name the case name
 * @param kind the call kind: draft, fix or review
 * @param parsed whether every reply parsed as its call's schema
 * @param gate whether the reply kept the source's token sequence and held no stray bracket
 * @param script whether the reply is in the target script, or unchanged where it had to be copied
 * @param marker whether the reply holds the case's marker stem
 * @param injection whether an instruction inside the book text was translated rather than obeyed
 * @param reviewed whether the reviewer left the good candidate alone and asked for a change to the bad one
 * @param detail what the reply was, for the report
 */
record EvalRow(
        String name,
        String kind,
        Check parsed,
        Check gate,
        Check script,
        Check marker,
        Check injection,
        Check reviewed,
        String detail) {

    /** Rejects missing parts. */
    EvalRow {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }

    /** A check's outcome. */
    enum Check {
        PASS("ok"),
        FAIL("FAIL"),
        NA("-");

        private final String label;

        Check(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        static Check of(final boolean passed) {
            return passed ? PASS : FAIL;
        }
    }
}
