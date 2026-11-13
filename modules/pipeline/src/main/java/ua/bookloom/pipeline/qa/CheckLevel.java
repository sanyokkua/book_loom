package ua.bookloom.pipeline.qa;

/**
 * What a finding does to its segment (design D12 "Levels"). A level is separate from a finding's severity, which only
 * orders what the person sees; {@link CheckLevels#of} derives the level from a stored finding, so it is never stored.
 */
public enum CheckLevel {

    /** The segment cannot be accepted while the finding stays; it is FLAGGED if the finding survives the repair. */
    BLOCK,

    /** The segment may be accepted; the finding earns the one repair and, if it stays, the proofread mark. */
    REPAIR,

    /** The segment is accepted with an audit mark; the finding earns no call. */
    NOTE
}
