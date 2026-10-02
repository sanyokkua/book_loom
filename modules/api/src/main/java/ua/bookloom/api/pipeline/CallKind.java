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

    /** A judge scoring call. */
    JUDGE,

    /** A directed-fix call applying the judge's feedback. */
    DIRECTED_FIX,

    /** A reflection call over the draft before repair. */
    REFLECT,

    /** An improvement pass over an accepted draft. */
    IMPROVE,

    /** A polish pass over an accepted draft. */
    POLISH,

    /** A glossary prescan call. */
    PRESCAN,

    /** A glossary review call: the model judges held terms as a name, a term or not a name. */
    REVIEW_TERMS,

    /** A glossary suggestion call: the model proposes a target rendering and a gender for held terms. */
    SUGGEST_TARGETS,

    /** A rolling-summary update call. */
    SUMMARY,

    /** A backward-revision call re-rendering an already-decided segment. */
    REVISION
}
