package ua.bookloom.api.project;

/**
 * Why a segment's decision is deferred rather than final
 * ({@code specs/translation-pipeline/spec.md} "Record deferrals and revise backwards on Max").
 */
public enum DeferralReason {

    /** A character's grammatical gender could not yet be resolved. */
    GENDER_UNKNOWN,

    /** A name proposed by the pipeline is waiting on the person's decision. */
    NAME_UNRESOLVED,

    /** A backward-revision candidate raised by the judge/reflect stage. */
    JUDGE,

    /** A locked rendering the person changed after decided segments used the old one (task 9.7). */
    TERM
}
