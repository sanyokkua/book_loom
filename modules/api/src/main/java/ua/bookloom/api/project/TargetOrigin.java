package ua.bookloom.api.project;

/**
 * Who chose a glossary entry's target ({@code specs/glossary/spec.md} "Suggest target renderings with the model").
 * A model's suggestion may be replaced by a later suggestion and is offered to the draft only as a hint; a person's
 * choice is never overwritten by the model.
 */
public enum TargetOrigin {

    /** The person typed, imported, accepted or locked the target — or the entry has no target at all. */
    PERSON,

    /** The model suggested the target and nobody has confirmed it yet. */
    SUGGESTED
}
