package ua.bookloom.api.project;

/** How firmly a glossary entry's target holds in the translation (design D3). */
public enum TargetState {

    /** A suggestion nobody has confirmed; offered to the draft only as a hint. */
    SUGGESTED,

    /** The stem is fixed and every inflected form of it is allowed. */
    PINNED,

    /** The exact text, replaced by a token in the book. */
    LOCKED
}
