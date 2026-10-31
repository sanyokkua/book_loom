package ua.bookloom.pipeline.glossary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.index.Occurrences;

/**
 * Which mentions of an entry's names give the entry pronoun evidence. A mention whose written word also fits another
 * name the book writes ({@code Велемира}: the man {@code Велемир}'s genitive, or a woman) counts only where the clause
 * decides it for this entry ({@link ClauseEvidence}); an unsure mention gives no entry a vote. A word that fits no
 * other written name counts as before. A mention found by two of the entry's names counts once.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LemmaMentions {

    /**
     * The pronoun evidence of an entry's lemma and forms.
     *
     * @param entry the non-null entry
     * @param texts the non-null visible texts to read
     * @param language the source language tag, or null when it is not known
     * @return the evidence; never null
     */
    static PronounGender.Evidence evidence(
            final GlossaryEntry entry, final List<String> texts, @Nullable final String language) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(texts, "texts");
        if (language == null || entry.term().indexOf(' ') >= 0 || !NameForms.hasParadigm(language)) {
            return PronounGender.of(entry.term(), texts, language);
        }
        final ClauseEvidence clause = ClauseEvidence.of(language);
        final BiPredicate<String, Mention> keep = filter(entry, texts, language, clause);
        PronounGender.Evidence evidence = PronounGender.of(entry.term(), entry.term(), texts, language, keep);
        for (final String form : entry.forms()) {
            evidence = evidence.and(PronounGender.of(form, entry.term(), texts, language, keep));
        }
        log.debug(
                "Pronoun mentions of entry {} read over {} names",
                entry.id(),
                entry.names().size());
        clause.logSummary();
        return evidence;
    }

    private static BiPredicate<String, Mention> filter(
            final GlossaryEntry entry, final List<String> texts, final String language, final ClauseEvidence clause) {
        final String lemmaKey = Occurrences.keyOf(entry.term());
        final IdentityHashMap<String, Set<Integer>> seen = new IdentityHashMap<>();
        final Map<String, Boolean> written = new HashMap<>();
        return (text, mention) -> {
            if (!seen.computeIfAbsent(text, _ -> new HashSet<>()).add(mention.start())) {
                return false;
            }
            final String word = wordAt(text, mention.start());
            final Set<NameForms.Reading> readings = NameForms.candidates(word, language);
            final boolean ofThisName = readings.stream()
                    .anyMatch(reading -> Occurrences.keyOf(reading.lemma()).equals(lemmaKey));
            final boolean contested = ofThisName
                    && readings.stream()
                            .filter(reading -> reading.gender() != Gender.UNKNOWN)
                            .map(reading -> Occurrences.keyOf(reading.lemma()))
                            .filter(key -> !key.equals(lemmaKey))
                            .anyMatch(key -> key.equals(Occurrences.keyOf(word))
                                    || written.computeIfAbsent(key, _ -> isWritten(key, texts)));
            return !contested
                    || clause.decide(text, mention.start(), mention.start() + word.length(), readings)
                            .filter(reading ->
                                    Occurrences.keyOf(reading.lemma()).equals(lemmaKey))
                            .isPresent();
        };
    }

    private static String wordAt(final String text, final int start) {
        int end = start;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    // Whether the book writes the name as a word of its own, in any letter case.
    private static boolean isWritten(final String key, final List<String> texts) {
        final Pattern word = Pattern.compile(
                "(?<![\\p{L}\\p{M}])" + Pattern.quote(key) + "(?![\\p{L}\\p{M}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return texts.stream().anyMatch(text -> word.matcher(text).find());
    }
}
