package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Locale;

/**
 * The probe's scores: one outcome per window, and the counts the gate reads.
 *
 * @param rows one row per window, in window order
 */
record CorefReport(List<Row> rows) {

    /** The suite name the reports and {@code scripts/eval-matrix.sh} use. */
    static final String SUITE = "coref";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double PERCENT = 100.0;

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

    /**
     * The report as JSON, for {@code scripts/eval-matrix.sh}.
     *
     * @param model the model the windows were read by
     * @return the counts, the wrong-vote rate and one outcome per window
     */
    String json(final String model) {
        final ObjectNode root = MAPPER.createObjectNode();
        root.put("suite", SUITE);
        root.put("model", model);
        root.put("windows", rows.size());
        root.put("right", rightReferent());
        root.put("wrong", wrongVotes());
        root.put("abstain", abstentions());
        root.put("wrongVoteRate", wrongVoteRate());
        final ArrayNode outcomes = root.putArray("rows");
        rows.forEach(row -> outcomes.addObject()
                .put("id", row.id())
                .put("outcome", row.outcome().name()));
        return root.toString();
    }

    /**
     * The report as a table a person reads.
     *
     * @param model the model the windows were read by
     * @return one line per window, then the counts and the wrong-vote rate
     */
    String table(final String model) {
        final StringBuilder out = new StringBuilder();
        rows.forEach(row -> out.append(String.format(Locale.ROOT, "%-24s %s%n", row.id(), row.outcome())));
        out.append(String.format(
                Locale.ROOT,
                "%s: %d windows, right %d, wrong %d, abstain %d, wrong votes %.1f %%%n",
                model,
                rows.size(),
                rightReferent(),
                wrongVotes(),
                abstentions(),
                PERCENT * wrongVoteRate()));
        return out.toString();
    }
}
