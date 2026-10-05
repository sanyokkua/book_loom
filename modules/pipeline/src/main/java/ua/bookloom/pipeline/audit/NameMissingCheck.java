package ua.bookloom.pipeline.audit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.lexicon.TermMappingVerifier;
import ua.bookloom.pipeline.lexicon.TermMatch;

/**
 * A name the glossary renders that the source holds and the target has lost — the case the draft-time glossary check
 * cannot see for an unlocked name, since only a locked one travels as a token. Conservative on purpose: only a person
 * or place entry with a non-empty target counts, the source form must stand as a whole word, and the target is held to
 * the same stem match the lexicon uses for an inflected rendering, so a declined name is not a loss.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameMissingCheck {

    static final String NAME = "name-missing";

    /**
     * Looks for names the target lost.
     *
     * @param glossary the non-null glossary entries of the project
     * @param source the non-null source display text
     * @param target the non-null target display text
     * @return one low finding naming every lost name, or empty when none is lost
     */
    static Optional<QaFinding> find(final List<GlossaryEntry> glossary, final String source, final String target) {
        final List<String> lost = glossary.stream()
                .filter(NameMissingCheck::isCheckable)
                .filter(entry -> TermMatch.occursIn(entry.term(), source))
                .filter(entry -> isMissing(entry, source, target))
                .map(entry -> "\"" + entry.term() + "\" → \"" + entry.target() + "\"")
                .toList();
        if (lost.isEmpty()) {
            return Optional.empty();
        }
        log.debug("Name check: {} glossary name(s) are in the source and not in the target", lost.size());
        return Optional.of(new QaFinding(
                "glossary",
                Severity.LOW,
                "A name of the glossary is in the source but its target form is not in the target: "
                        + String.join(", ", lost) + ".",
                NAME));
    }

    private static boolean isCheckable(final GlossaryEntry entry) {
        final boolean isName = entry.type() == TermType.CHARACTER || entry.type() == TermType.PLACE;
        final String target = entry.target();
        return isName && !entry.locked() && target != null && !target.isBlank();
    }

    private static boolean isMissing(final GlossaryEntry entry, final String source, final String target) {
        final String rendering = Objects.requireNonNull(entry.target(), "target");
        return TermMappingVerifier.verify(Map.of(entry.term(), rendering), List.of(entry.term()), source, target)
                .isEmpty();
    }
}
