package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One case's outcome in a stage suite: whether the production class ended where the case says, and what it cost.
 *
 * @param id the case id, with the stage after a colon when a case runs two stages
 * @param passed whether every expectation of the case held
 * @param detail what held and what did not, in a few words
 * @param calls model calls the case sent
 * @param failed calls that came back as an error
 * @param repeated calls that sent a request an earlier call of the case had already sent
 * @param refused answers the production class read and did not use (a refused revision, an undecided batch, a
 *     proposal dropped); zero where the stage has no such count
 */
record StageRow(String id, boolean passed, String detail, int calls, int failed, int repeated, int refused) {

    /** Rejects missing text. */
    StageRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(detail, "detail");
    }

    /** A row for a case whose class answered with an error. */
    static StageRow failed(final String id, final Object code, final StageProbe.Counts counts) {
        return of(id, false, "error " + code, counts, 0);
    }

    /** A row with the call counts of its case. */
    static StageRow of(
            final String id,
            final boolean passed,
            final String detail,
            final StageProbe.Counts counts,
            final int refused) {
        return new StageRow(id, passed, detail, counts.calls(), counts.failed(), counts.repeated(), refused);
    }
}
