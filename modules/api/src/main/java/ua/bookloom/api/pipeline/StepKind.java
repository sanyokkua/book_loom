package ua.bookloom.api.pipeline;

/** The kinds of work step a run counts, so progress and the time left are measured per kind. */
public enum StepKind {
    /** One segment's draft; a segment kept verbatim is a draft step done at once with no call. */
    DRAFT,
    /** The one repair, or the directed fix of a refused edit, started after a draft. */
    REPAIR,
    /** A batch re-ask for ids the reply left out. */
    RE_ASK,
    /** A resend after a timed-out call. */
    RESEND,
    /** One of Max's chapter summaries. */
    SUMMARY,
    /** The Proofread stage's work (and, until group 14, the export pass's calls). */
    PROOFREAD,
    /** One stage of the names pass: a group row of its progress dialog that no percentage counts. */
    STAGE,
    /** One model call of a names pass, of the brief suggestion or of a card action. */
    REQUEST
}
