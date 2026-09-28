package ua.bookloom.document.mask;

/** The rule of the placeholder gate a target broke. */
public enum GateRule {

    /** A token was dropped, duplicated or invented. */
    MULTISET,

    /** A pair's closing token stands before its opening token, or two pairs overlap instead of nesting. */
    PAIR_ORDER,

    /** A pair that held text in the source holds none in the target. */
    EMPTIED_PAIR,

    /** A line-break token now sits in a different innermost pair than in the source. */
    LINE_BREAK
}
