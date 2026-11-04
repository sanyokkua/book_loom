package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.reviewer.ReviewerCall;
import ua.bookloom.pipeline.reviewer.ReviewerSheets;

/**
 * One chunk's quality-loop settings.
 *
 * @param reviewMode the run's review mode; the repair path no longer reads it (it carried the polish window)
 * @param dial the quality dial's mechanics — the repair mode and how many reviewer passes run
 * @param frame the run's language pair, style sheet and foreign-passage policy
 * @param namePolicy the Book Brief's name policy, read by the script and echo checks' name removal
 * @param glossaryTerms every glossary term in the chunk, read by the same name removal
 * @param glossaryPairs the chunk's unlocked glossary renderings, one {@code source → target} line each, which the name
 *     checks hold the candidates to
 * @param characters the chunk's character gender sheet, one {@code name — gender} line each, which the gender checks
 *     read
 * @param reviewerSheets what each reviewer call is shown of the glossary, the lexicon and the characters, cut to its own
 *     pairs; for settings built from ready lines, those lines for every call
 * @param unitRole the role of the chunk's unit, which the Latin-run check reads
 * @param pinnedTargets the renderings of the chunk's pinned glossary entries, which the name-spelling check lets
 *     inflect; empty when none is known
 * @param names the chunk's glossary entries, which the never-worse name vetoes of the one repair hold a repair to; an
 *     entry with no rendering is ignored there; empty when none is known
 * @param repairs whether a draft that fails a check may be given a fixing call — the one repair, or the reviewer's
 *     fix of a refused edit; {@code false} for a review retry, which is one attempt the person asked for
 */
public record LoopSettings(
        ReviewMode reviewMode,
        DialParameters dial,
        CallFrame frame,
        NamePolicy namePolicy,
        List<String> glossaryTerms,
        List<String> glossaryPairs,
        List<String> characters,
        ReviewerSheets reviewerSheets,
        UnitRole unitRole,
        List<String> pinnedTargets,
        List<GlossaryEntry> names,
        boolean repairs) {

    /** Settings whose drafts may be repaired. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewerSheets reviewerSheets,
            final UnitRole unitRole,
            final List<String> pinnedTargets) {
        this(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                unitRole,
                pinnedTargets,
                List.of(),
                true);
    }

    /** Settings that know no pinned entry. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewerSheets reviewerSheets,
            final UnitRole unitRole) {
        this(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                unitRole,
                List.of());
    }

    /** Settings for a chunk of a body unit. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters,
            final ReviewerSheets reviewerSheets) {
        this(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                UnitRole.BODY);
    }

    /** These settings for a chunk of a unit with another role. */
    public LoopSettings withUnitRole(final UnitRole role) {
        return new LoopSettings(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                role,
                pinnedTargets,
                names,
                repairs);
    }

    /** These settings that let the given pinned renderings inflect. */
    public LoopSettings withPinnedTargets(final List<String> pinned) {
        return new LoopSettings(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                unitRole,
                pinned,
                names,
                repairs);
    }

    /** These settings whose one repair is held to the given glossary entries' names. */
    public LoopSettings withNames(final List<GlossaryEntry> entries) {
        return new LoopSettings(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                unitRole,
                pinnedTargets,
                entries,
                repairs);
    }

    /** These settings with no fixing call: a draft is decided by the checks and the acceptance rule as it stands. */
    public LoopSettings withoutRepair() {
        return new LoopSettings(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                reviewerSheets,
                unitRole,
                pinnedTargets,
                names,
                false);
    }

    /** Validates the invariants a caller is entitled to assume and defensively copies the list component. */
    public LoopSettings {
        Objects.requireNonNull(reviewMode, "reviewMode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(reviewerSheets, "reviewerSheets");
        Objects.requireNonNull(unitRole, "unitRole");
        Objects.requireNonNull(pinnedTargets, "pinnedTargets");
        pinnedTargets = List.copyOf(pinnedTargets);
        names = List.copyOf(Objects.requireNonNull(names, "names"));
        glossaryTerms = List.copyOf(glossaryTerms);
        glossaryPairs = List.copyOf(glossaryPairs);
        characters = List.copyOf(characters);
    }

    /** What the soft checks read of these settings. */
    public SoftContext softContext() {
        return new SoftContext(frame, namePolicy, glossaryTerms, glossaryPairs, characters, unitRole, pinnedTargets);
    }

    /** Settings whose reviewer is shown the glossary pairs and the character lines as they stand, in every call. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters) {
        this(
                reviewMode,
                dial,
                frame,
                namePolicy,
                glossaryTerms,
                glossaryPairs,
                characters,
                ReviewerSheets.fixed(new ReviewerCall.Terms(glossaryPairs, List.of()), characters));
    }

    /** Settings for a chunk whose reviewer is shown no character sheet. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs) {
        this(reviewMode, dial, frame, namePolicy, glossaryTerms, glossaryPairs, List.of());
    }

    /** Settings for a chunk whose glossary has no rendering the reviewer needs to check. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms) {
        this(reviewMode, dial, frame, namePolicy, glossaryTerms, List.of(), List.of());
    }
}
