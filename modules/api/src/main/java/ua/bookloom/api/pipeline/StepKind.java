package ua.bookloom.api.pipeline;

/** The kinds of work step a run counts, so progress and the time left are measured per kind. */
public enum StepKind {
    /** One segment's draft; a segment kept verbatim is a draft step done at once with no call. */
    DRAFT,
    /** A chunk's reviewer pass (Fast 0, Balanced 1, Max 2 per chunk). */
    CHECK,
    /** A repair round or a directed fix started after a draft or check. */
    REPAIR,
    /** A batch re-ask for ids the reply left out. */
    RE_ASK,
    /** A resend after a timed-out call. */
    RESEND,
    /** One of Max's chapter summaries. */
    SUMMARY,
    /** Max's backward revision. */
    REVISION,
    /** One stage of the names pass: a group row of its progress dialog that no percentage counts. */
    STAGE,
    /** One model call of a names pass, of the brief suggestion or of a card action. */
    REQUEST
}
