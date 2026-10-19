package ua.bookloom.api.project;

/**
 * Where a glossary entry's known gender came from, which decides who may change it and how much the run trusts it
 * (15i.N0). An entry whose gender is {@link Gender#UNKNOWN} has no origin at all.
 */
public enum GenderOrigin {

    /**
     * The person typed, imported, confirmed or locked the gender: it is fixed for the model and no later verdict from
     * a model or from code overwrites it.
     */
    PERSON,

    /**
     * A model's answer most of its cited windows show and the book's tally allows, or a pronoun case the code counted: enforced in the run, and replaced by
     * a stronger verified verdict.
     */
    VERIFIED,

    /**
     * A first-name list's or an unverified model's guess nobody has confirmed: shown as "(suggested)", not enforced,
     * and replaced by any verified verdict.
     */
    SUGGESTED
}
