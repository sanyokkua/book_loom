package ua.bookloom.api.project;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A glossary term for one project, shown as an editable table on Names &amp; style
 * ({@code specs/glossary/spec.md} "Show the glossary as an editable table on Names & style").
 *
 * <p>Only an unlocked entry with a target can carry {@link TargetOrigin#SUGGESTED}: a locked entry is replaced by its
 * target in the book, so its target is always the person's, and an entry with no target has nothing to have suggested.
 * The constructor reads any other combination as {@link TargetOrigin#PERSON}, so a suggestion can never be masked as
 * a locked name.
 *
 * @param id the entry's stable id
 * @param projectId the owning project's id
 * @param term the source term
 * @param target the term's chosen rendering, or null when unresolved
 * @param type the term's category
 * @param gender the term's grammatical gender
 * @param locked whether the term's rendering is locked against automatic revision
 * @param origin who chose the target; {@link TargetOrigin#PERSON} unless the entry is unlocked and has a target
 * @param genderSuggested whether the gender came from the bundled first-name list and nobody has confirmed it; false
 *     whenever the gender is {@link Gender#UNKNOWN} or the entry is locked, because a lock confirms what it holds
 */
public record GlossaryEntry(
        String id,
        String projectId,
        String term,
        @Nullable String target,
        TermType type,
        Gender gender,
        boolean locked,
        TargetOrigin origin,
        boolean genderSuggested) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public GlossaryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(gender, "gender");
        Objects.requireNonNull(origin, "origin");
        if (locked || target == null || target.isBlank()) {
            origin = TargetOrigin.PERSON;
        }
        genderSuggested = genderSuggested && !locked && gender != Gender.UNKNOWN;
    }

    /** An entry whose gender, if any, is the person's or the model's. */
    public GlossaryEntry(
            final String id,
            final String projectId,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked,
            final TargetOrigin origin) {
        this(id, projectId, term, target, type, gender, locked, origin, false);
    }

    /** An entry whose target, if any, is the person's. */
    public GlossaryEntry(
            final String id,
            final String projectId,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        this(id, projectId, term, target, type, gender, locked, TargetOrigin.PERSON, false);
    }

    /**
     * Whether the gender is the first-name list's guess nobody has confirmed.
     *
     * @return {@code true} if the gender was seeded from a given name and not yet confirmed, {@code false} otherwise
     */
    public boolean isGenderSuggested() {
        return genderSuggested;
    }

    /**
     * Whether the target is the model's suggestion nobody has confirmed.
     *
     * @return {@code true} if the target was suggested and not yet accepted, {@code false} otherwise
     */
    public boolean isSuggested() {
        return origin == TargetOrigin.SUGGESTED;
    }

    /**
     * This entry with a target the person chose.
     *
     * @param chosen the person's target, or null to clear it
     * @return a copy whose target is the person's
     */
    public GlossaryEntry withTarget(@Nullable final String chosen) {
        return new GlossaryEntry(
                id, projectId, term, chosen, type, gender, locked, TargetOrigin.PERSON, genderSuggested);
    }

    /**
     * This entry with the model's suggested target; a locked entry stays the person's.
     *
     * @param suggested the non-blank suggested target
     * @return a copy whose target is a suggestion
     */
    public GlossaryEntry withSuggestedTarget(final String suggested) {
        Objects.requireNonNull(suggested, "suggested");
        return new GlossaryEntry(
                id, projectId, term, suggested, type, gender, locked, TargetOrigin.SUGGESTED, genderSuggested);
    }

    /**
     * This entry with its lock set; locking confirms the target as the person's.
     *
     * @param lock whether the term is locked
     * @return a copy with the lock set
     */
    public GlossaryEntry withLocked(final boolean lock) {
        return new GlossaryEntry(
                id, projectId, term, target, type, gender, lock, lock ? TargetOrigin.PERSON : origin, genderSuggested);
    }

    /**
     * This entry with another type; the target's origin is kept.
     *
     * @param changed the non-null type
     * @return a copy with the type set
     */
    public GlossaryEntry withType(final TermType changed) {
        return new GlossaryEntry(id, projectId, term, target, changed, gender, locked, origin, genderSuggested);
    }

    /**
     * This entry with another gender; the target's origin is kept.
     *
     * @param changed the non-null gender
     * @return a copy with the gender set
     */
    public GlossaryEntry withGender(final Gender changed) {
        return new GlossaryEntry(id, projectId, term, target, type, changed, locked, origin, false);
    }

    /**
     * This entry with a gender the bundled first-name list proposes; the person can change it, and a lock or an edit
     * confirms it.
     *
     * @param proposed the non-null gender the list holds for the term's first name
     * @return a copy whose gender is marked as a suggestion
     */
    public GlossaryEntry withSuggestedGender(final Gender proposed) {
        Objects.requireNonNull(proposed, "proposed");
        return new GlossaryEntry(id, projectId, term, target, type, proposed, locked, origin, true);
    }

    /**
     * This entry with its target confirmed as the person's, unchanged otherwise.
     *
     * @return a copy whose target is the person's
     */
    public GlossaryEntry accepted() {
        return new GlossaryEntry(
                id, projectId, term, target, type, gender, locked, TargetOrigin.PERSON, genderSuggested);
    }
}
