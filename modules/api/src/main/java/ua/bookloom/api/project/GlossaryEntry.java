package ua.bookloom.api.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A lemma entry of one project's glossary: a name or term in its base form with the inflected forms the book uses,
 * shown as an editable table on Names &amp; style ({@code specs/glossary/spec.md} "Record where each glossary value came
 * from"; design D3).
 *
 * <p>The canonical constructor normalises the parts that cannot hold together, idempotently, because the store reads
 * through it: an entry with no target has no target origin and is never a suggestion; a locked target is never a guess
 * (a lock confirms it); a gender origin exists exactly while the gender is known; a place kind only on a place; "no
 * gender evidence" only while the gender is unknown; a blank target has no origin, as none, and a blank meaning is
 * none.
 *
 * @param id the entry's stable id
 * @param projectId the owning project's id
 * @param term the base form (the lemma) of the source term
 * @param forms the other spellings the book uses for the term (its inflected forms, a plural, a one-letter
 *     misspelling), which name the same thing and share its target; never null, empty for most entries
 * @param lemmaInferred whether the base form was inferred from the forms rather than seen in the book
 * @param target the term's chosen rendering, or null when unresolved
 * @param targetState how firmly the target holds; an entry with no target is {@link TargetState#PINNED}
 * @param targetOrigin where the target came from; null exactly when the target is null or blank, the person's when not
 *     stated
 * @param type the term's category
 * @param placeKind the kind of place, only for {@link TermType#PLACE}, else null
 * @param typeOrigin where the type came from
 * @param gender the term's grammatical gender
 * @param genderOrigin where the known gender came from; null exactly when the gender is {@link Gender#UNKNOWN}, the
 *     person's when not stated. Only {@link Origin#isEnforced() enforced} origins are held to the text
 * @param genderSeedTried whether the first-name list was already consulted for this entry, or the person set its
 *     gender (even back to {@link Gender#UNKNOWN}); an entry with this flag is never seeded again
 * @param noGenderEvidence whether the names pass read this person and found no evidence for a gender, so the table
 *     can say "not specified — no evidence in the book" (15i.N5); {@code false} whenever the gender is known
 * @param meaning a note on what the term means, or null
 * @param meaningChoice how a name whose meaning the book plays on is rendered, or null when the book does not play on
 *     it (or nobody was asked)
 * @param knowledge what the model answered for the bare name ({@link NameKnowledge}), or null when never asked; every
 *     copy keeps it
 * @param unsureMentions how many mentions of the entry's names also fit another name the book writes and were left
 *     undecided by the clause around them, so the table marks the entry Unsure; zero or more, set by the name scan
 */
public record GlossaryEntry(
        String id,
        String projectId,
        String term,
        List<String> forms,
        boolean lemmaInferred,
        @Nullable String target,
        TargetState targetState,
        @Nullable Origin targetOrigin,
        TermType type,
        @Nullable PlaceKind placeKind,
        Origin typeOrigin,
        Gender gender,
        @Nullable Origin genderOrigin,
        boolean genderSeedTried,
        boolean noGenderEvidence,
        @Nullable String meaning,
        @Nullable MeaningChoice meaningChoice,
        @Nullable NameKnowledge knowledge,
        int unsureMentions) {

    /** Validates the parts and normalises the ones that cannot hold together. */
    public GlossaryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(term, "term");
        forms = List.copyOf(Objects.requireNonNull(forms, "forms"));
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(typeOrigin, "typeOrigin");
        Objects.requireNonNull(gender, "gender");
        meaning = blankAsNull(meaning);
        if (target == null || target.isBlank()) {
            targetOrigin = null;
            targetState = targetState == TargetState.SUGGESTED ? TargetState.PINNED : targetState;
        } else if (targetOrigin == null || (targetState == TargetState.LOCKED && targetOrigin == Origin.LIKELY)) {
            targetOrigin = Origin.USER;
        }
        placeKind = type == TermType.PLACE ? placeKind : null;
        genderOrigin = gender == Gender.UNKNOWN ? null : Objects.requireNonNullElse(genderOrigin, Origin.USER);
        noGenderEvidence = noGenderEvidence && gender == Gender.UNKNOWN;
        unsureMentions = Math.max(0, unsureMentions);
    }

    /**
     * An entry as the person (or a file the person chose) states it — the mapping from the old shape: a locked entry is
     * {@link TargetState#LOCKED}, any other {@link TargetState#PINNED}, and every value is the person's.
     *
     * @param id the entry's stable id
     * @param projectId the owning project's id
     * @param term the source term
     * @param target the rendering, or null when unresolved
     * @param type the term's category
     * @param gender the term's gender
     * @param locked whether the rendering is locked
     * @return the entry with no forms, no place kind and no meaning
     */
    public static GlossaryEntry of(
            final String id,
            final String projectId,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                List.of(),
                false,
                target,
                locked ? TargetState.LOCKED : TargetState.PINNED,
                Origin.USER,
                type,
                null,
                Origin.USER,
                gender,
                Origin.USER,
                false,
                false,
                null,
                null,
                null,
                0);
    }

    /** {@return whether the target is exact text replaced by a token in the book: the state is locked} */
    public boolean locked() {
        return targetState == TargetState.LOCKED;
    }

    /** {@return whether the target is the model's suggestion nobody has confirmed} */
    public boolean isSuggested() {
        return targetState == TargetState.SUGGESTED;
    }

    /** {@return whether the known gender came from a rule or a guess, which the person only has to confirm} */
    public boolean isGenderSuggested() {
        return genderOrigin == Origin.RULE || genderOrigin == Origin.LIKELY;
    }

    /** {@return whether the gender is known and one the run may hold a text to: the person's or the book's} */
    public boolean hasConfirmedGender() {
        return gender != Gender.UNKNOWN && genderOrigin != null && genderOrigin.isEnforced();
    }

    /**
     * This entry with a target the person chose; a locked entry stays locked.
     *
     * @param chosen the person's target, or null to clear it
     * @return a copy whose target is the person's
     */
    public GlossaryEntry withTarget(@Nullable final String chosen) {
        return copyTarget(chosen, locked() ? TargetState.LOCKED : TargetState.PINNED, Origin.USER, genderOrigin);
    }

    /**
     * This entry with the model's suggested target; a locked entry stays the person's.
     *
     * @param suggested the non-blank suggested target
     * @return a copy whose target is a suggestion, or a locked copy whose target is the person's
     */
    public GlossaryEntry withSuggestedTarget(final String suggested) {
        Objects.requireNonNull(suggested, "suggested");
        if (locked()) {
            return copyTarget(suggested, TargetState.LOCKED, Origin.USER, genderOrigin);
        }
        return copyTarget(suggested, TargetState.SUGGESTED, Origin.LIKELY, genderOrigin);
    }

    /**
     * This entry with its lock set; locking confirms the target, and a rule's or a guessed gender, as the person's.
     *
     * @param lock whether the term is locked
     * @return a copy with the lock set; unlocking a locked entry leaves it pinned
     */
    public GlossaryEntry withLocked(final boolean lock) {
        if (lock) {
            return copyTarget(
                    target, TargetState.LOCKED, Origin.USER, isGenderSuggested() ? Origin.USER : genderOrigin);
        }
        return copyTarget(target, locked() ? TargetState.PINNED : targetState, targetOrigin, genderOrigin);
    }

    /**
     * This entry locked by a rule of the code (a brand or a name kept as written, a checked name where names are not
     * declined): the rendering is fixed, but nothing the person did not see is confirmed, so a suggested gender stays a
     * suggestion. Only the person's lock ({@link #withLocked(boolean)}) confirms the gender.
     *
     * @return a locked copy whose target origin is {@link Origin#RULE}, with the gender origin kept
     */
    public GlossaryEntry withRuleLock() {
        return copyTarget(target, TargetState.LOCKED, Origin.RULE, genderOrigin);
    }

    /**
     * This entry with its target confirmed as the person's, unchanged otherwise.
     *
     * @return a copy whose target is the person's; a suggestion becomes pinned
     */
    public GlossaryEntry accepted() {
        return copyTarget(target, locked() ? TargetState.LOCKED : TargetState.PINNED, Origin.USER, genderOrigin);
    }

    /** This entry with another type and its origin ({@link Origin#USER} for the person's edit); the rest is kept. */
    public GlossaryEntry withType(final TermType changed, final Origin origin) {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(origin, "origin");
        return described(forms, changed, origin, meaning, meaningChoice, unsureMentions);
    }

    /**
     * This entry with the gender the person chose, which confirms it, even when it is the one already held or one the
     * book proved, and keeps the first-name list from proposing another later.
     *
     * @param changed the non-null gender
     * @return a copy with the gender set as the person's and marked as tried; without an origin when it is unknown
     */
    public GlossaryEntry withGender(final Gender changed) {
        Objects.requireNonNull(changed, "changed");
        return copyGender(changed, Origin.USER, true, false);
    }

    /**
     * This entry with a gender the book proved (a model answer a window shows, or a pronoun count): an unchanged gender
     * leaves the entry as it is, so a suggested gender stays suggested and a person's stays the person's, and a new one
     * is not marked as tried.
     *
     * @param inferred the non-null gender
     * @return this entry when the gender is the held one, else a copy with the gender set from {@link Origin#BOOK}
     */
    public GlossaryEntry withInferredGender(final Gender inferred) {
        Objects.requireNonNull(inferred, "inferred");
        if (inferred == gender) {
            return this;
        }
        return copyGender(inferred, Origin.BOOK, genderSeedTried, false);
    }

    /**
     * This entry with a gender nobody has verified (the bundled first-name list's, a pronoun count too weak to decide,
     * an unverified model answer); the person can change it, and a lock or an edit confirms it.
     *
     * @param proposed the non-null gender proposed for the term
     * @return a copy whose gender is {@link Origin#LIKELY}
     */
    public GlossaryEntry withSuggestedGender(final Gender proposed) {
        Objects.requireNonNull(proposed, "proposed");
        return copyGender(proposed, Origin.LIKELY, true, false);
    }

    /**
     * This entry with a gender of the stated origin; the seed flag is kept.
     *
     * @param changed the non-null gender
     * @param origin where the gender came from; ignored for an unknown gender
     * @return a copy with the gender and its origin set
     */
    public GlossaryEntry withGenderFrom(final Gender changed, final Origin origin) {
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(origin, "origin");
        return copyGender(changed, origin, genderSeedTried, false);
    }

    /**
     * This entry marked as a person the scan found no evidence of a gender for; an entry with a known gender is
     * returned as it is.
     *
     * @return a copy marked as having no gender evidence when the gender is unknown, else this entry
     */
    public GlossaryEntry withNoGenderEvidence() {
        if (gender != Gender.UNKNOWN || noGenderEvidence) {
            return this;
        }
        return copyGender(gender, null, genderSeedTried, true);
    }

    /** This entry with the other spellings the book uses for its term; never null. */
    public GlossaryEntry withForms(final List<String> folded) {
        return described(folded, type, typeOrigin, meaning, meaningChoice, unsureMentions);
    }

    /** This entry with a note on what its term means; a null or blank note is none. */
    public GlossaryEntry withMeaning(@Nullable final String note) {
        return described(forms, type, typeOrigin, note, meaningChoice, unsureMentions);
    }

    /** This entry with how its meaning-bearing name is rendered; null for a name the book does not play on. */
    public GlossaryEntry withMeaningChoice(@Nullable final MeaningChoice choice) {
        return described(forms, type, typeOrigin, meaning, choice, unsureMentions);
    }

    /** This entry with the count of its mentions the clause left undecided between it and another name. */
    public GlossaryEntry withUnsureMentions(final int count) {
        return described(forms, type, typeOrigin, meaning, meaningChoice, count);
    }

    /**
     * Every spelling that names this entry in the book.
     *
     * @return the term first, then its forms; never null or empty
     */
    public List<String> names() {
        if (forms.isEmpty()) {
            return List.of(term);
        }
        final ArrayList<String> names = new ArrayList<>(forms.size() + 1);
        names.add(term);
        names.addAll(forms);
        return List.copyOf(names);
    }

    // A copy with what describes the term changed; the target and the gender, and their origins, are kept.
    private GlossaryEntry described(
            final List<String> newForms,
            final TermType newType,
            final Origin newTypeOrigin,
            @Nullable final String newMeaning,
            @Nullable final MeaningChoice newChoice,
            final int newUnsure) {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                newForms,
                lemmaInferred,
                target,
                targetState,
                targetOrigin,
                newType,
                placeKind,
                newTypeOrigin,
                gender,
                genderOrigin,
                genderSeedTried,
                noGenderEvidence,
                newMeaning,
                newChoice,
                knowledge,
                newUnsure);
    }

    private GlossaryEntry copyTarget(
            @Nullable final String newTarget,
            final TargetState newState,
            @Nullable final Origin newOrigin,
            @Nullable final Origin newGenderOrigin) {
        return copy(newTarget, newState, newOrigin, gender, newGenderOrigin, genderSeedTried, noGenderEvidence);
    }

    private GlossaryEntry copyGender(
            final Gender newGender,
            @Nullable final Origin newOrigin,
            final boolean newSeedTried,
            final boolean newNoEvidence) {
        return copy(target, targetState, targetOrigin, newGender, newOrigin, newSeedTried, newNoEvidence);
    }

    private GlossaryEntry copy(
            @Nullable final String newTarget,
            final TargetState newState,
            @Nullable final Origin newTargetOrigin,
            final Gender newGender,
            @Nullable final Origin newGenderOrigin,
            final boolean newSeedTried,
            final boolean newNoEvidence) {
        return new GlossaryEntry(
                id,
                projectId,
                term,
                forms,
                lemmaInferred,
                newTarget,
                newState,
                newTargetOrigin,
                type,
                placeKind,
                typeOrigin,
                newGender,
                newGenderOrigin,
                newSeedTried,
                newNoEvidence,
                meaning,
                meaningChoice,
                knowledge,
                unsureMentions);
    }

    private static @Nullable String blankAsNull(@Nullable final String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
