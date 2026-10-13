package ua.bookloom.api.pipeline;

/**
 * What a single model call is for, distinguishing the several calls one segment can produce.
 */
public enum CallKind {

    /** The initial translation attempt. */
    DRAFT,

    /** A repair call fixing a structural (tag/placeholder-count) hard-gate failure. */
    STRUCTURAL_REPAIR,

    /** A repair call fixing a placeholder-multiset hard-gate failure. */
    PLACEHOLDER_REPAIR,

    /** A reviewer call: reads a batch of candidates and answers with find-and-replace edits. */
    REVIEW,

    /** A directed-fix call repairing the findings a check or the reviewer named. */
    DIRECTED_FIX,

    /** A glossary prescan call. */
    PRESCAN,

    /** A glossary review call: the model judges held terms as a name, a term or not a name. */
    REVIEW_TERMS,

    /** A glossary suggestion call: the model proposes a target rendering and a gender for held terms. */
    SUGGEST_TARGETS,

    /** A rolling-summary update call. */
    SUMMARY,

    /** A backward-revision call re-rendering an already-decided segment. */
    REVISION,

    /** A setup call proposing the translated book's file name. */
    FILE_NAME,

    /** A setup call reading one passage of the book's story for the Book Brief's tone and style. */
    BRIEF_SUGGESTION,

    /** A setup call asking what the model knows of the book by its title and author, checked against the metadata. */
    BOOK_KNOWLEDGE,

    /** A setup call deciding the brief's genre, audience and voice from the facts, the knowledge and the readings. */
    BRIEF_SYNTHESIS,

    /** A names call: the model reads one glossary term in windows from across the book (15i.N4). */
    TERM_READING,

    /** A recurring-term call: the model chooses which candidate words a translator must render the same way. */
    TERM_CHOICE
}
