package ua.bookloom.api.project;

import java.util.ArrayList;
import java.util.List;
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
 * @param genderOrigin who or what the known gender came from (15i.N0); null exactly when the gender is
 *     {@link Gender#UNKNOWN}. A known gender stated with no origin is {@link GenderOrigin#PERSON}'s; the person's lock
 *     ({@link #withLocked(boolean)}) turns a {@link GenderOrigin#SUGGESTED} gender into the person's because it
 *     confirms what the entry holds, while a rule's lock ({@link #withRuleLock()}) confirms nothing
 * @param genderSeedTried whether the first-name list was already consulted for this entry, or the person set its
 *     gender (even back to {@link Gender#UNKNOWN}); an entry with this flag is never seeded again
 * @param aliases the other spellings the name scan folded into the term (a plural, a one-letter misspelling), which
 *     name the same thing and share its target; never null, empty for most entries
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
        @Nullable GenderOrigin genderOrigin,
        boolean genderSeedTried,
        List<String> aliases) {

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
        genderOrigin = originOf(gender, genderOrigin);
        aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
    }

    /** An entry with no alias. */
    public GlossaryEntry(
            final String id,
            final String projectId,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked,
            final TargetOrigin origin,
            final boolean genderSuggested,
            final boolean genderSeedTried) {
        this(
                id,
                projectId,
                term,
                target,
                type,
                gender,
                locked,
                origin,
                suggestedOrigin(genderSuggested && !locked),
                genderSeedTried,
                List.of());
    }

    /** An entry whose gender was never seeded or edited. */
    public GlossaryEntry(
            final String id,
            final String projectId,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked,
            final TargetOrigin origin,
            final boolean genderSuggested) {
        this(id, projectId, term, target, type, gender, locked, origin, genderSuggested, false);
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
     * Whether the target is the model's suggestion nobody has confirmed.
     *
     * @return {@code true} if the target was suggested and not yet accepted, {@code false} otherwise
     */
    public boolean isSuggested() {
        return origin == TargetOrigin.SUGGESTED;
    }

    /**
     * Whether the gender is one the run may hold a text to: the person's or a verified one, not a guess.
     *
     * @return {@code true} when the gender is known and not a suggestion, {@code false} otherwise
     */
    public boolean hasConfirmedGender() {
        return gender != Gender.UNKNOWN && genderOrigin != GenderOrigin.SUGGESTED;
    }

    /**
     * This entry with a target the person chose.
     *
     * @param chosen the person's target, or null to clear it
     * @return a copy whose target is the person's
     */
    public GlossaryEntry withTarget(@Nullable final String chosen) {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                chosen,
                type,
                gender,
                locked,
                TargetOrigin.PERSON,
                genderOrigin,
                genderSeedTried,
                aliases);
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
                id,
                projectId,
                term,
                suggested,
                type,
                gender,
                locked,
                TargetOrigin.SUGGESTED,
                genderOrigin,
                genderSeedTried,
                aliases);
    }

    /**
     * This entry with its lock set; locking confirms the target as the person's.
     *
     * @param lock whether the term is locked
     * @return a copy with the lock set
     */
    public GlossaryEntry withLocked(final boolean lock) {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                target,
                type,
                gender,
                lock,
                lock ? TargetOrigin.PERSON : origin,
                lock && genderOrigin == GenderOrigin.SUGGESTED ? GenderOrigin.PERSON : genderOrigin,
                genderSeedTried,
                aliases);
    }

    /**
     * This entry locked by a rule of the code (a brand or a name kept as written, a checked name where names are not
     * declined): the rendering is fixed, but nothing the person did not see is confirmed, so a suggested gender stays a
     * suggestion. Only the person's lock ({@link #withLocked(boolean)}) confirms the gender.
     *
     * @return a locked copy with the gender origin kept
     */
    public GlossaryEntry withRuleLock() {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                target,
                type,
                gender,
                true,
                TargetOrigin.PERSON,
                genderOrigin,
                genderSeedTried,
                aliases);
    }

    /**
     * This entry with another type; the target's origin is kept.
     *
     * @param changed the non-null type
     * @return a copy with the type set
     */
    public GlossaryEntry withType(final TermType changed) {
        return new GlossaryEntry(
                id, projectId, term, target, changed, gender, locked, origin, genderOrigin, genderSeedTried, aliases);
    }

    /**
     * This entry with the gender the person chose, which confirms it, even when it is the one already held or one a
     * model verified, and keeps the first-name list from proposing another later.
     *
     * @param changed the non-null gender
     * @return a copy with the gender set as the person's and marked as tried; without an origin when it is unknown
     */
    public GlossaryEntry withGender(final Gender changed) {
        Objects.requireNonNull(changed, "changed");
        return new GlossaryEntry(
                id, projectId, term, target, type, changed, locked, origin, GenderOrigin.PERSON, true, aliases);
    }

    /**
     * This entry with a gender a model answer a window shows, or a pronoun count, verified: an unchanged gender leaves
     * the entry as it is, so a suggested gender stays suggested and a person's stays the person's, and a new one is
     * not marked as tried.
     *
     * @param inferred the non-null gender
     * @return this entry when the gender is the held one, else a copy with the gender set as verified
     */
    public GlossaryEntry withInferredGender(final Gender inferred) {
        Objects.requireNonNull(inferred, "inferred");
        if (inferred == gender) {
            return this;
        }
        return new GlossaryEntry(
                id,
                projectId,
                term,
                target,
                type,
                inferred,
                locked,
                origin,
                GenderOrigin.VERIFIED,
                genderSeedTried,
                aliases);
    }

    /**
     * This entry with a gender nobody has verified (the bundled first-name list's, a pronoun count too weak to decide,
     * an unverified model answer); the person can change it, and a lock or an edit confirms it.
     *
     * @param proposed the non-null gender proposed for the term
     * @return a copy whose gender is marked as a suggestion
     */
    public GlossaryEntry withSuggestedGender(final Gender proposed) {
        Objects.requireNonNull(proposed, "proposed");
        return new GlossaryEntry(
                id, projectId, term, target, type, proposed, locked, origin, GenderOrigin.SUGGESTED, true, aliases);
    }

    /**
     * This entry with its target confirmed as the person's, unchanged otherwise.
     *
     * @return a copy whose target is the person's
     */
    public GlossaryEntry accepted() {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                target,
                type,
                gender,
                locked,
                TargetOrigin.PERSON,
                genderOrigin,
                genderSeedTried,
                aliases);
    }

    /**
     * This entry with the spellings folded into its term.
     *
     * @param folded the non-null other spellings of the term
     * @return a copy holding them as its aliases
     */
    public GlossaryEntry withAliases(final List<String> folded) {
        return new GlossaryEntry(
                id, projectId, term, target, type, gender, locked, origin, genderOrigin, genderSeedTried, folded);
    }

    /**
     * Every spelling that names this entry in the book.
     *
     * @return the term first, then its aliases; never null or empty
     */
    public List<String> names() {
        if (aliases.isEmpty()) {
            return List.of(term);
        }
        final ArrayList<String> names = new ArrayList<>(aliases.size() + 1);
        names.add(term);
        names.addAll(aliases);
        return List.copyOf(names);
    }

    private static @Nullable GenderOrigin suggestedOrigin(final boolean suggested) {
        return suggested ? GenderOrigin.SUGGESTED : null;
    }

    private static @Nullable GenderOrigin originOf(final Gender gender, @Nullable final GenderOrigin stated) {
        if (gender == Gender.UNKNOWN) {
            return null;
        }
        return stated == null ? GenderOrigin.PERSON : stated;
    }
}
