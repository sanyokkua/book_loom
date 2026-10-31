package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.WordCounts;

/**
 * Whether a capitalised word at a sentence start counts for the name scan: the book writes it with a capital away
 * from a sentence start at least {@link FrequencyScan#CREDIT_MIN_MID_SENTENCE} times — counting, in a language with a
 * name-ending table, every form of the names it could be a form of ({@link NameForms}), so a name written once at a
 * sentence start ({@code Велемир вернулся.}) and twice mid-sentence in another case ({@code Велемира}) is counted.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MidSentenceCredit {

    /**
     * The test for one scan.
     *
     * @param counts the scanned segments' word counts; never null
     * @param language the source language tag, or null when it is not known (each word counts alone)
     * @return whether a word's key earns the credit; never null
     */
    static Predicate<String> of(final WordCounts counts, @Nullable final String language) {
        if (language == null || !NameForms.hasParadigm(language)) {
            return key -> counts.midSentence(key) >= FrequencyScan.CREDIT_MIN_MID_SENTENCE;
        }
        final Map<String, Integer> byLemma = new HashMap<>();
        final Map<String, Integer> capitalised = counts.capitalisedMidSentence();
        capitalised.forEach((key, times) -> NameForms.candidates(key, language)
                .forEach(reading -> byLemma.merge(Occurrences.keyOf(reading.lemma()), times, Integer::sum)));
        log.debug(
                "Mid-sentence credit language={} capitalisedForms={} nominatives={}",
                language,
                capitalised.size(),
                byLemma.size());
        final Map<String, Boolean> asked = new HashMap<>();
        return key -> counts.midSentence(key) >= FrequencyScan.CREDIT_MIN_MID_SENTENCE
                || asked.computeIfAbsent(
                        key,
                        _ -> NameForms.candidates(key, language).stream()
                                .anyMatch(reading -> byLemma.getOrDefault(Occurrences.keyOf(reading.lemma()), 0)
                                        >= FrequencyScan.CREDIT_MIN_MID_SENTENCE));
    }
}
