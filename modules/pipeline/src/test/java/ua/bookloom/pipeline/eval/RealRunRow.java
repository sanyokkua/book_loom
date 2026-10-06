package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One measured row of the real-run suite: a draft, a reviewer separation, a pair of a reviewer batch, a batch draft or a
 * repair of one case.
 *
 * @param id the case id, with {@code #n} for the n-th pair of a reviewer batch
 * @param kind the defect family the case belongs to, the grouping of the report
 * @param call which call it measured: draft, review, review-batch, batch or repair
 * @param outcome what the row came to
 * @param stable whether repeated runs of a reviewer call asked for the same changes; true for a call made once
 * @param truncated whether the call's reply was cut by its output cap; set on the first row of a call only
 * @param items for a batch draft, how many items it held; zero otherwise
 * @param tooShort for a batch draft, the items whose target is far shorter than the source
 * @param leaked for a batch draft, the items whose target carries the protocol around the text
 * @param detail what the reply was, for the report
 */
record RealRunRow(
        String id,
        String kind,
        String call,
        Outcome outcome,
        boolean stable,
        boolean truncated,
        int items,
        int tooShort,
        int leaked,
        String detail) {

    /** Rejects missing parts. */
    RealRunRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(detail, "detail");
    }

    /** What a row came to. */
    enum Outcome {
        /** The case came out as its labels say. */
        PASS("ok"),
        /** The case came out otherwise. */
        FAIL("FAIL"),
        /** A known failure that failed as expected: reported, never counted in a rate. */
        KNOWN_RED("known"),
        /** A known failure that came out right: the flag is to be dropped. */
        KNOWN_GREEN("NOW-OK");

        private final String label;

        Outcome(final String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** A row of a plain pass or fail, which a known failure turns into {@link Outcome#KNOWN_RED} or {@link Outcome#KNOWN_GREEN}. */
    static RealRunRow of(
            final String id,
            final String kind,
            final String call,
            final boolean passed,
            final boolean knownFailure,
            final String detail) {
        return new RealRunRow(id, kind, call, outcomeOf(passed, knownFailure), true, false, 0, 0, 0, detail);
    }

    private static Outcome outcomeOf(final boolean passed, final boolean knownFailure) {
        if (knownFailure) {
            return passed ? Outcome.KNOWN_GREEN : Outcome.KNOWN_RED;
        }
        return passed ? Outcome.PASS : Outcome.FAIL;
    }

    /** This row with the repeat and cap facts of a reviewer call. */
    RealRunRow withCall(final boolean repeatsAgreed, final boolean cut) {
        return new RealRunRow(id, kind, call, outcome, repeatsAgreed, cut, items, tooShort, leaked, detail);
    }

    /** A batch draft's row: the counts the batch report adds up. */
    static RealRunRow batch(
            final String id,
            final String kind,
            final boolean passed,
            final int items,
            final int tooShort,
            final int leaked,
            final String detail) {
        return new RealRunRow(
                id, kind, "batch", outcomeOf(passed, false), true, false, items, tooShort, leaked, detail);
    }

    boolean isCounted() {
        return outcome == Outcome.PASS || outcome == Outcome.FAIL;
    }
}
