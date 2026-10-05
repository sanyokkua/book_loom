package ua.bookloom.pipeline.eval;

/**
 * The reviewer's outcomes for one corpus case.
 *
 * @param id the case id
 * @param kind the defect family
 * @param defective the label
 * @param flagged the production outcome of the first run: the deterministic checks refuse or note the candidate, or
 *     the reviewer asked for a change the app would act on (an edit whose quote is in the candidate, or a rewrite; an
 *     edit that quotes nothing is a harmless hallucination and does not count)
 * @param readable whether every reviewer reply parsed
 * @param stable whether all repeated runs asked for the same change
 * @param runs how many times the case was reviewed
 * @param reviewerFlagged whether the reviewer alone asked for such a change; a candidate the checks refuse never
 *     reaches it in production, so it is not asked and this is false
 * @param tokenBreaks how many applied edits or rewrites, over all runs, changed the candidate's placeholder tokens;
 *     the verifier makes this impossible, so anything but zero is a defect of the verifier
 */
record DefectRow(
        String id,
        String kind,
        boolean defective,
        boolean flagged,
        boolean readable,
        boolean stable,
        int runs,
        int tokenBreaks,
        boolean reviewerFlagged) {

    /** A row whose outcome is the reviewer's alone. */
    DefectRow(
            final String id,
            final String kind,
            final boolean defective,
            final boolean flagged,
            final boolean readable,
            final boolean stable,
            final int runs,
            final int tokenBreaks) {
        this(id, kind, defective, flagged, readable, stable, runs, tokenBreaks, flagged);
    }
}
