package ua.bookloom.pipeline.audit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.NameSpelling;
import ua.bookloom.pipeline.lexicon.TermMatch;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * One glossary name written two ways across the decided targets (Майлз, Майлс; Боббі, Бобі). A single segment cannot
 * show it, so the scan reads every target in order and reports each name once, on the first segment that holds an odd
 * spelling. Two words are the same name when they are equal once doubled letters are collapsed and the language's
 * voiced and voiceless consonant pairs are folded, so a declined form (Майлза) or another name (Майла) is never a
 * variant.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameVariants {

    static final String NAME = "name-variants";
    private static final int MIN_NAME_LETTERS = 4;

    /**
     * Finds the names spelled more than one way.
     *
     * @param glossary the non-null glossary entries of the project
     * @param targets the non-null display text of each audited segment's target, by segment id, in document order
     * @param targetLanguage the non-null target language tag, whose voicing pairs fold the spellings
     * @return the findings by the segment that carries them; never null, empty when every name has one spelling
     */
    static Map<String, List<QaFinding>> find(
            final List<GlossaryEntry> glossary, final Map<String, String> targets, final String targetLanguage) {
        return find(glossary, targets, Map.of(), targetLanguage);
    }

    /**
     * Finds the names spelled more than one way, counting a word only in a segment whose source names the term and
     * never when it is a common word the targets also write in lower case.
     *
     * @param glossary the non-null glossary entries of the project
     * @param targets the non-null display text of each audited segment's target, by segment id, in document order
     * @param sources the non-null display text of each segment's source, by segment id; a segment with no source here
     *     is counted as if it named every term
     * @param targetLanguage the non-null target language tag, whose voicing pairs fold the spellings
     * @return the findings by the segment that carries them; never null, empty when every name has one spelling
     */
    static Map<String, List<QaFinding>> find(
            final List<GlossaryEntry> glossary,
            final Map<String, String> targets,
            final Map<String, String> sources,
            final String targetLanguage) {
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(sources, "sources");
        final List<String> pairs = LanguageRules.bundled().voicingPairs(targetLanguage);
        final Set<String> lowerCase = NameSpelling.lowerCaseWords(targets.values());
        final Common common = new Common(targetLanguage, lowerCase, sources);
        final Map<String, List<QaFinding>> found = new LinkedHashMap<>();
        glossary.stream()
                .filter(NameVariants::isCheckable)
                .forEach(entry -> variantOf(entry, targets, pairs, common)
                        .ifPresent(variant -> found.computeIfAbsent(variant.segmentId(), id -> new ArrayList<>())
                                .add(variant.finding())));
        log.debug("Name variants: {} name(s) are spelled more than one way", found.size());
        return found;
    }

    private record Variant(String segmentId, QaFinding finding) {}

    private record Common(String language, Set<String> lowerCase, Map<String, String> sources) {}

    private static boolean isCheckable(final GlossaryEntry entry) {
        final String target = entry.target();
        return (entry.type() == TermType.CHARACTER || entry.type() == TermType.PLACE)
                && target != null
                && target.strip().length() >= MIN_NAME_LETTERS
                && target.strip().chars().noneMatch(Character::isWhitespace);
    }

    private static java.util.Optional<Variant> variantOf(
            final GlossaryEntry entry,
            final Map<String, String> targets,
            final List<String> pairs,
            final Common common) {
        final String rendering =
                Objects.requireNonNull(entry.target(), "target").strip();
        final String folded = NameSpelling.fold(rendering, pairs);
        final Map<String, Integer> spellings = new HashMap<>();
        String firstSegment = null;
        String firstSpelling = null;
        for (final Map.Entry<String, String> target : targets.entrySet()) {
            if (!namesTerm(entry.term(), target.getKey(), common.sources())) {
                continue;
            }
            final Matcher words = NameSpelling.CAPITALISED.matcher(target.getValue());
            while (words.find()) {
                final String word = words.group();
                if (!word.equalsIgnoreCase(rendering)
                        && NameSpelling.fold(word, pairs).equals(folded)
                        && !NameSpelling.isCommonWord(word, common.language(), common.lowerCase())) {
                    spellings.merge(word, 1, Integer::sum);
                    firstSegment = firstSegment == null ? target.getKey() : firstSegment;
                    firstSpelling = firstSpelling == null ? word : firstSpelling;
                }
            }
        }
        if (firstSegment == null) {
            return Optional.empty();
        }
        log.trace("Name variants {} -> {}: {}", entry.term(), rendering, spellings);
        return Optional.of(new Variant(firstSegment, finding(entry, rendering, spellings)));
    }

    private static QaFinding finding(
            final GlossaryEntry entry, final String rendering, final Map<String, Integer> spellings) {
        final String others = spellings.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(spelling -> "\"" + spelling.getKey() + "\" (" + spelling.getValue() + ")")
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
        return new QaFinding(
                "glossary",
                Severity.LOW,
                "The glossary renders \"" + entry.term() + "\" as \"" + rendering + "\", and the book also spells it "
                        + others + ": keep one spelling.",
                NAME);
    }

    // Without sources (the caller has none) every segment counts; with them, only a segment whose source names the
    // term.
    private static boolean namesTerm(final String term, final String segmentId, final Map<String, String> sources) {
        return sources.isEmpty()
                || (sources.containsKey(segmentId) && TermMatch.occursIn(term, sources.get(segmentId)));
    }
}
