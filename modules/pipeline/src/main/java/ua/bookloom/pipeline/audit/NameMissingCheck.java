package ua.bookloom.pipeline.audit;

import java.util.List;
import java.util.Locale;
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
import ua.bookloom.pipeline.prompt.LanguageRules;

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
    private static final int MIN_VARIANT_LETTERS = 4;

    /**
     * Looks for names the target lost.
     *
     * @param glossary the non-null glossary entries of the project
     * @param source the non-null source display text
     * @param target the non-null target display text
     * @param targetLanguage the non-null target language tag, whose stem data lets a declined or derived name pass
     * @return one low finding naming every lost name, or empty when none is lost
     */
    static Optional<QaFinding> find(
            final List<GlossaryEntry> glossary, final String source, final String target, final String targetLanguage) {
        final List<String> alternations = LanguageRules.bundled().stemAlternations(targetLanguage);
        final List<GlossaryEntry> inSource = glossary.stream()
                .filter(NameMissingCheck::isCheckable)
                .filter(entry -> TermMatch.occursIn(entry.term(), source))
                .toList();
        final List<GlossaryEntry> missing = inSource.stream()
                .filter(entry -> isMissing(entry, source, target, alternations))
                .toList();
        final List<String> lost = missing.stream()
                .filter(entry -> !hasPresentVariant(entry, inSource, missing))
                .map(entry -> "\"" + entry.term() + "\" → \"" + entry.target() + "\"")
                .toList();
        if (lost.isEmpty()) {
            return Optional.empty();
        }
        log.debug("Name check: {} glossary name(s) are in the source and not in the target: {}", lost.size(), lost);
        return Optional.of(new QaFinding(
                "glossary",
                Severity.LOW,
                "A name of the glossary is in the source but its target form is not in the target: "
                        + String.join(", ", lost) + ".",
                NAME));
    }

    // Two entries that spell one name nearly alike (Моріс, Морис) are one name: it is not lost when the other is there.
    private static boolean hasPresentVariant(
            final GlossaryEntry entry, final List<GlossaryEntry> inSource, final List<GlossaryEntry> missing) {
        final String target = Objects.requireNonNull(entry.target(), "target");
        return inSource.stream()
                .filter(other -> !missing.contains(other))
                .anyMatch(other -> isNearlyAlike(target, Objects.requireNonNull(other.target(), "target")));
    }

    private static boolean isNearlyAlike(final String first, final String second) {
        final String a = first.strip().toLowerCase(Locale.ROOT);
        final String b = second.strip().toLowerCase(Locale.ROOT);
        if (Math.abs(a.length() - b.length()) > 1 || a.length() < MIN_VARIANT_LETTERS) {
            return false;
        }
        final int common = commonPrefix(a, b);
        if (common == a.length() || common == b.length()) {
            return true;
        }
        final int skipA = a.length() >= b.length() ? 1 : 0;
        final int skipB = b.length() >= a.length() ? 1 : 0;
        return a.substring(common + skipA).equals(b.substring(common + skipB));
    }

    private static int commonPrefix(final String a, final String b) {
        int index = 0;
        while (index < a.length() && index < b.length() && a.charAt(index) == b.charAt(index)) {
            index++;
        }
        return index;
    }

    private static boolean isCheckable(final GlossaryEntry entry) {
        final boolean isName = entry.type() == TermType.CHARACTER || entry.type() == TermType.PLACE;
        final String target = entry.target();
        return isName && !entry.locked() && target != null && !target.isBlank();
    }

    private static boolean isMissing(
            final GlossaryEntry entry, final String source, final String target, final List<String> alternations) {
        final String rendering = Objects.requireNonNull(entry.target(), "target");
        return TermMappingVerifier.verify(
                        Map.of(entry.term(), rendering), List.of(entry.term()), source, target, alternations)
                .isEmpty();
    }
}
