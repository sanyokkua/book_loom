package ua.bookloom.api.project;

/** What the Proofread stage decided about a segment a window read (design D15). */
public enum ProofreadOutcome {

    /** The model's minimal edit passed the never-worse gate and is the segment's text now. */
    KEPT,

    /** The edit passed the gate but is held as a proposal for the person, the text unchanged. */
    PROPOSED,

    /** The edit was refused by the gate; the reason names the check that got worse. */
    REJECTED,

    /** The model answered that the text needs no change. */
    UNCHANGED
}
