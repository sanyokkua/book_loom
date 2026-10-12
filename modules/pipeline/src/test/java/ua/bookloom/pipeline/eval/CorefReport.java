package ua.bookloom.pipeline.eval;

import java.util.List;

/**
 * The probe's scores: one outcome per window, and the counts the gate reads.
 *
 * @param rows one row per window, in window order
 */
record CorefReport(List<Row> rows) {

    /** What a window's answer was worth. */
    enum Outcome {
        /** At least one vote, and every vote names a pronoun of the target. */
        RIGHT_REFERENT,
        /** At least one vote names a pronoun of someone or something else, or text that is no gold pronoun. */
        WRONG_VOTE,
        /** No vote: nothing claimed, or every claim was refused or quoted text outside the window. */
        ABSTENTION
    }

    /**
     * One window's result.
     *
     * @param id the window id
     * @param outcome what the answer was worth
     */
    record Row(String id, Outcome outcome) {}

    /** Number of windows scored {@link Outcome#RIGHT_REFERENT}. */
    int rightReferent() {
        return count(Outcome.RIGHT_REFERENT);
    }

    /** Number of windows scored {@link Outcome#WRONG_VOTE}. */
    int wrongVotes() {
        return count(Outcome.WRONG_VOTE);
    }

    /** Number of windows scored {@link Outcome#ABSTENTION}. */
    int abstentions() {
        return count(Outcome.ABSTENTION);
    }

    /**
     * The share of voting windows whose vote was wrong, {@code wrongVotes / (rightReferent + wrongVotes)}; an
     * abstention is neither. This is the number the 5 % gate reads.
     *
     * @return a value from 0 to 1; 0 when no window voted
     */
    double wrongVoteRate() {
        final int voted = rightReferent() + wrongVotes();
        return voted == 0 ? 0.0 : (double) wrongVotes() / voted;
    }

    private int count(final Outcome outcome) {
        return (int) rows.stream().filter(row -> row.outcome() == outcome).count();
    }
}
