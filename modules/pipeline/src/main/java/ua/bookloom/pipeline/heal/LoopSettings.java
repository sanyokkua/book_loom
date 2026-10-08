package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * One chunk's quality-loop settings.
 *
 * @param reviewMode the run's review mode; the repair path no longer reads it (it carried the polish window)
 * @param dial the quality dial's mechanics — the repair budget and how many reviewer passes run
 * @param frame the run's language pair, style sheet and foreign-passage policy
 * @param namePolicy the Book Brief's name policy, read by the script and echo checks' name removal
 * @param glossaryTerms every glossary term in the chunk, read by the same name removal
 * @param glossaryPairs the chunk's unlocked glossary renderings, one {@code source → target} line each, which the
 *     reviewer checks the candidates against
 * @param characters the chunk's character gender sheet, one {@code name — gender} line each, shown to the reviewer
 * @param usualRenderings the lexicon's established renderings of the chunk's recurring terms, one
 *     {@code source → target} line each; the reviewer sees them as usage that may differ, never as a rule
 */
public record LoopSettings(
        ReviewMode reviewMode,
        DialParameters dial,
        CallFrame frame,
        NamePolicy namePolicy,
        List<String> glossaryTerms,
        List<String> glossaryPairs,
        List<String> characters,
        List<String> usualRenderings) {

    /** Validates the invariants a caller is entitled to assume and defensively copies the list component. */
    public LoopSettings {
        Objects.requireNonNull(reviewMode, "reviewMode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(glossaryPairs, "glossaryPairs");
        Objects.requireNonNull(characters, "characters");
        Objects.requireNonNull(usualRenderings, "usualRenderings");
        usualRenderings = List.copyOf(usualRenderings);
        glossaryTerms = List.copyOf(glossaryTerms);
        glossaryPairs = List.copyOf(glossaryPairs);
        characters = List.copyOf(characters);
    }

    /** Settings for a chunk with no usual renderings to show the reviewer. */
    public LoopSettings(
            final ReviewMode reviewMode,
            final DialParameters dial,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<String> glossaryPairs,
            final List<String> characters) {
        this(reviewMode, dial, frame, namePolicy, glossaryTerms, glossaryPairs, characters, List.of());
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
