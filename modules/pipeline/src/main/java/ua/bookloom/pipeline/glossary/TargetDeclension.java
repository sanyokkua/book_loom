package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;

/**
 * The forward half of {@link NameForms}: a target's case forms and vocative from the target language's table, its
 * vocative rows and its vowel changes. A target of several words declines each word, and the forms combine up to
 * {@link NameForms#MAX_FORMS}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TargetDeclension {

    static NameForms.TargetForms forms(
            final String target, final Gender gender, final String language, final NameForms.Table table) {
        final List<String> words = List.of(NameForms.SPACES.split(target.strip()));
        final List<Set<String>> cases = new ArrayList<>();
        final List<Set<String>> vocatives = new ArrayList<>();
        for (final String word : words) {
            final Set<String> wordCases = new LinkedHashSet<>(List.of(word));
            final Set<String> wordVocatives = new LinkedHashSet<>();
            rowsFor(word, gender, table).forEach(row -> decline(word, row, table, wordCases, wordVocatives));
            cases.add(wordCases);
            vocatives.add(wordVocatives.isEmpty() ? Set.of(word) : wordVocatives);
        }
        final Set<String> caseForms = combined(cases);
        caseForms.remove(target.strip());
        final Set<String> vocativeForms = combined(vocatives);
        vocativeForms.remove(target.strip());
        log.debug(
                "Target forms language={} gender={} words={} cases={} vocatives={}",
                language,
                gender,
                words.size(),
                caseForms.size(),
                vocativeForms.size());
        log.trace("Target forms of {}: {} / {}", target, caseForms, vocativeForms);
        return new NameForms.TargetForms(target, caseForms, vocativeForms);
    }

    // The most specific row of each gender asked for: the longest ending, a consonant row counting as none.
    private static List<NameForms.Row> rowsFor(final String word, final Gender gender, final NameForms.Table table) {
        final String lower = word.toLowerCase(Locale.ROOT);
        final List<Gender> genders = gender == Gender.UNKNOWN || gender == Gender.NEUTER
                ? List.of(Gender.MALE, Gender.FEMALE)
                : List.of(gender);
        final List<NameForms.Row> chosen = new ArrayList<>();
        for (final Gender wanted : genders) {
            final List<NameForms.Row> fitting = table.rows().stream()
                    .filter(row -> row.gender() == wanted || row.gender() == Gender.UNKNOWN)
                    .filter(row -> fits(row, table, lower))
                    .toList();
            final int best = fitting.stream()
                    .mapToInt(TargetDeclension::specificity)
                    .max()
                    .orElse(-1);
            fitting.stream().filter(row -> specificity(row) == best).forEach(chosen::add);
        }
        return chosen;
    }

    private static boolean fits(final NameForms.Row row, final NameForms.Table table, final String lower) {
        return switch (row.kind()) {
            case INDECLINABLE -> false;
            case CONSONANT ->
                lower.length() >= NameForms.MIN_STEM_LETTERS
                        && table.isConsonant(lower.codePointBefore(lower.length()));
            case ENDING -> NameForms.endsWith(lower, row.ending());
        };
    }

    private static int specificity(final NameForms.Row row) {
        return row.kind() == NameForms.Kind.ENDING ? row.ending().length() : 0;
    }

    private static void decline(
            final String word,
            final NameForms.Row row,
            final NameForms.Table table,
            final Set<String> cases,
            final Set<String> vocatives) {
        final String stem = row.kind() == NameForms.Kind.CONSONANT
                ? word
                : word.substring(0, word.length() - row.ending().length());
        final Set<String> stems = new LinkedHashSet<>(List.of(stem));
        if (row.kind() == NameForms.Kind.CONSONANT) {
            stems.addAll(alternated(stem, table));
        }
        final List<String> vocativeEndings = table.vocatives().stream()
                .filter(vocative -> vocative.ending().equals(row.ending()) && vocative.gender() == row.gender())
                .flatMap(vocative -> vocative.obliques().stream())
                .toList();
        for (final String each : stems) {
            row.obliques().forEach(ending -> cases.add(each + ending));
            vocativeEndings.forEach(ending -> vocatives.add(each + ending));
        }
    }

    // The stems the vowel changes give: the vowel of the last closed syllable swapped as the file lists.
    private static List<String> alternated(final String stem, final NameForms.Table table) {
        final String lower = stem.toLowerCase(Locale.ROOT);
        int vowel = lower.length() - 1;
        while (vowel > 0 && table.isConsonant(lower.codePointAt(vowel))) {
            vowel--;
        }
        if (vowel <= 0 || vowel == lower.length() - 1) {
            return List.of();
        }
        final String letter = lower.substring(vowel, vowel + 1);
        final String head = stem.substring(0, vowel);
        final String tail = stem.substring(vowel + 1);
        final List<String> stems = new ArrayList<>();
        for (final NameForms.Alternation alternation : table.alternations()) {
            if (alternation.fleeting() && alternation.from().contains(letter)) {
                stems.add(head + alternation.to() + tail);
            } else if (!alternation.fleeting() && alternation.to().equals(letter)) {
                alternation.from().forEach(from -> stems.add(head + from + tail));
            }
        }
        return stems;
    }

    // Every way of taking one form of each word, in order, up to MAX_FORMS.
    private static Set<String> combined(final List<Set<String>> perWord) {
        List<String> joined = List.of("");
        for (final Set<String> options : perWord) {
            final List<String> heads = joined;
            joined = heads.stream()
                    .flatMap(head -> options.stream().map(option -> head.isEmpty() ? option : head + " " + option))
                    .limit(NameForms.MAX_FORMS)
                    .toList();
        }
        return new LinkedHashSet<>(joined);
    }
}
