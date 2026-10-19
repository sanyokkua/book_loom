package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.KeyTermScan;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * A glossary name the source holds and the target no longer has, found while the segment is drafted so a directed fix can
 * mend it. When exactly one capitalised word of the target is the rendering written another way (Майлс for Майлз) the
 * finding names that word and the exact replacement; otherwise it names the lost rendering. Soft: a sentence may
 * legitimately turn a name into a pronoun, so the finding earns one fix and never flags the segment.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameLossCheck {

    private static final String ARROW = " → ";
    private static final int MIN_NAME_LETTERS = 4;
    private static final String NO_ID = "pair";

    static List<CheckFinding> find(
            final String source,
            final String target,
            final List<String> glossaryPairs,
            @Nullable final String sourceLanguage,
            final String targetLanguage) {
        final List<GlossaryEntry> entries = entriesOf(glossaryPairs, sourceLanguage);
        if (entries.isEmpty()) {
            return List.of();
        }
        final List<GlossaryEntry> lost = NameMissingCheck.lost(entries, source, target, targetLanguage);
        final List<CheckFinding> found = new ArrayList<>();
        for (final GlossaryEntry entry : lost) {
            found.add(findingOf(entry, target, targetLanguage));
        }
        return List.copyOf(found);
    }

    private static CheckFinding findingOf(final GlossaryEntry entry, final String target, final String targetLanguage) {
        final String rendering = Objects.requireNonNull(entry.target(), "target");
        final List<String> candidates = candidates(rendering, target, targetLanguage);
        if (candidates.size() == 1) {
            final String spelled = candidates.getFirst();
            log.debug("Name check: a glossary name is written another way in the target");
            log.trace("Name spelling {} → {} instead of {}", entry.term(), rendering, spelled);
            return new CheckFinding(
                    FindingKind.NAME_SPELLING,
                    new TextSpan(target.indexOf(spelled), target.indexOf(spelled) + spelled.length(), spelled),
                    "The glossary renders \"" + entry.term() + "\" as \"" + rendering + "\", and the translation"
                            + " writes \"" + spelled + "\": replace \"" + spelled + "\" with \"" + rendering
                            + "\", in the form the sentence needs.",
                    false);
        }
        log.debug("Name check: a glossary name is lost from the target");
        log.trace("Name check: {} is lost from the target", entry.term());
        return new CheckFinding(
                FindingKind.NAME_MISSING,
                new TextSpan(0, 0, ""),
                "The source names \"" + entry.term() + "\" and the translation has lost it: put \"" + rendering
                        + "\" back, in the form the sentence needs.",
                false);
    }

    // The capitalised words of the target that are the rendering spelt another way; a common word never is.
    private static List<String> candidates(final String rendering, final String target, final String targetLanguage) {
        if (rendering.length() < MIN_NAME_LETTERS || rendering.chars().anyMatch(Character::isWhitespace)) {
            return List.of();
        }
        final List<String> pairs = LanguageRules.bundled().voicingPairs(targetLanguage);
        final Set<String> lowerCase = NameSpelling.lowerCaseWords(List.of(target));
        final String folded = NameSpelling.fold(rendering, pairs);
        final Matcher words = NameSpelling.CAPITALISED.matcher(target);
        return words.results()
                .map(found -> found.group())
                .filter(word -> !word.equalsIgnoreCase(rendering))
                .filter(word -> NameSpelling.fold(word, pairs).equals(folded))
                .filter(word -> !NameSpelling.isCommonWord(word, targetLanguage, lowerCase))
                .distinct()
                .toList();
    }

    private static List<GlossaryEntry> entriesOf(final List<String> pairs, @Nullable final String sourceLanguage) {
        final List<GlossaryEntry> entries = new ArrayList<>();
        for (final String pair : pairs) {
            final int at = pair.indexOf(ARROW);
            if (at <= 0) {
                continue;
            }
            final String term = pair.substring(0, at).strip();
            final String rendering = pair.substring(at + ARROW.length()).strip();
            if (!term.isEmpty()
                    && Character.isUpperCase(term.codePointAt(0))
                    && !rendering.isEmpty()
                    && !KeyTermScan.isTitle(term, sourceLanguage)) {
                entries.add(
                        new GlossaryEntry(NO_ID, NO_ID, term, rendering, TermType.CHARACTER, Gender.UNKNOWN, false));
            }
        }
        return entries;
    }
}
