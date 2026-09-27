package ua.bookloom.api.project;

/**
 * A glossary entry's grammatical gender, needed to render some target languages correctly
 * ({@code specs/glossary/spec.md} "Show the glossary as an editable table on Names & style").
 */
public enum Gender {

    /** Grammatically/referentially female. */
    FEMALE,

    /** Grammatically/referentially male. */
    MALE,

    /** Grammatically neuter, or a non-person term. */
    NEUTER,

    /** Not yet resolved. */
    UNKNOWN
}
