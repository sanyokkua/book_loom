package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.NameParadigms;

/**
 * The source language's name-ending table read the other way round: a written form gives every nominative (dictionary
 * form) it could be a case of. A row {@code ending|gender: endings} says a name ending in {@code ending} takes the
 * other {@code endings}; {@code C} stands for a consonant and {@code indeclinable} for the endings a name never
 * changes. Only the longest oblique ending a form matches is read, so {@code Алісою} is the {@code -ою} of
 * {@code Аліса} and not also the {@code -ю} of a name in {@code -й}. A language whose file has no table (or no known
 * language) gets the identity table: the form is its own nominative. Vocative rows are not read.
 *
 * <p>Which letters a {@code C} may stand for is read from the data too, never listed in code: a letter is not a
 * consonant when it is a one-letter ending of the language's {@code nounEndings} or of any row, or the last letter of
 * a row's ending that is also a noun ending ({@code -ой} makes {@code й} no consonant).
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NameForms {

    /** The fewest letters left of a form once an ending is taken off. */
    static final int MIN_STEM_LETTERS = 2;

    private static final Pattern ROW = Pattern.compile("(\\S+)\\|(m|f|any)(>voc)?:\\s*(\\S+(?:\\s+\\S+)*)");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final String CONSONANT = "C";
    private static final String INDECLINABLE = "indeclinable";
    private static final Map<String, Table> TABLES = new ConcurrentHashMap<>();

    /**
     * One nominative a written form may belong to.
     *
     * @param lemma the nominative, spelled with the form's own letters up to the ending
     * @param gender the row's gender; {@link Gender#UNKNOWN} for a row of any gender or the identity table
     * @param nominative whether the form is this nominative itself rather than one of its other cases
     */
    public record Reading(String lemma, Gender gender, boolean nominative) {

        /** Validates the components. */
        public Reading {
            Objects.requireNonNull(lemma, "lemma");
            Objects.requireNonNull(gender, "gender");
        }
    }

    private enum Kind {
        CONSONANT,
        INDECLINABLE,
        ENDING
    }

    private record Row(Kind kind, String ending, Gender gender, List<String> obliques) {}

    private record Table(List<Row> rows, Set<Integer> nonConsonants) {

        boolean isConsonant(final int letter) {
            return Character.isLetter(letter) && !nonConsonants.contains(letter);
        }
    }

    /**
     * Whether the language has a name-ending table, so its forms can be grouped.
     *
     * @param language the language tag, or null when it is not known
     * @return {@code true} if its file holds at least one row, {@code false} otherwise
     */
    public static boolean hasParadigm(@Nullable final String language) {
        return language != null && !tableOf(language).rows().isEmpty();
    }

    /**
     * The nominatives a written form could belong to.
     *
     * @param form the non-null form as written
     * @param language the source language tag, or null when it is not known (the identity table)
     * @return never empty; the form itself as a nominative of unknown gender when the table gives nothing
     */
    public static Set<Reading> candidates(final String form, @Nullable final String language) {
        Objects.requireNonNull(form, "form");
        final Set<Reading> identity = Set.of(new Reading(form, Gender.UNKNOWN, true));
        if (language == null) {
            return identity;
        }
        final Table table = tableOf(language);
        final String lower = form.toLowerCase(Locale.ROOT);
        final Set<Reading> nominatives = new LinkedHashSet<>();
        final List<Oblique> obliques = new ArrayList<>();
        for (final Row row : table.rows()) {
            read(row, table, form, lower, nominatives, obliques);
        }
        final int longest = obliques.stream().mapToInt(Oblique::length).max().orElse(0);
        obliques.stream()
                .filter(oblique -> oblique.length() == longest)
                .forEach(oblique -> nominatives.add(oblique.reading()));
        log.trace("Name form {} of {} reads as {}", form, language, nominatives);
        return nominatives.isEmpty() ? identity : Set.copyOf(nominatives);
    }

    private record Oblique(int length, Reading reading) {}

    private static void read(
            final Row row,
            final Table table,
            final String form,
            final String lower,
            final Set<Reading> nominatives,
            final List<Oblique> obliques) {
        final boolean nominative =
                switch (row.kind()) {
                    case INDECLINABLE -> row.obliques().stream().anyMatch(ending -> endsWith(lower, ending));
                    case CONSONANT ->
                        lower.length() >= MIN_STEM_LETTERS && table.isConsonant(lower.codePointBefore(lower.length()));
                    case ENDING -> endsWith(lower, row.ending());
                };
        if (nominative) {
            nominatives.add(new Reading(form, row.gender(), true));
        }
        if (row.kind() == Kind.INDECLINABLE) {
            return;
        }
        for (final String ending : row.obliques()) {
            if (endsWith(lower, ending) && fitsStem(row, table, lower, ending)) {
                final String stem = form.substring(0, form.length() - ending.length());
                final String lemma = row.kind() == Kind.CONSONANT ? stem : stem + row.ending();
                obliques.add(new Oblique(ending.length(), new Reading(lemma, row.gender(), false)));
            }
        }
    }

    // A consonant row's stem must end in a consonant; any other row's stem takes the row's ending back.
    private static boolean fitsStem(final Row row, final Table table, final String lower, final String ending) {
        return row.kind() != Kind.CONSONANT
                || table.isConsonant(lower.codePointBefore(lower.length() - ending.length()));
    }

    // An ending counts only with at least MIN_STEM_LETTERS letters left before it.
    private static boolean endsWith(final String lower, final String ending) {
        return lower.length() - ending.length() >= MIN_STEM_LETTERS && lower.endsWith(ending);
    }

    private static Table tableOf(final String language) {
        return TABLES.computeIfAbsent(language, NameForms::load);
    }

    private static Table load(final String language) {
        final List<Row> rows = new ArrayList<>();
        for (final String text : NameParadigms.of(language)) {
            final Matcher row = ROW.matcher(text.strip());
            if (!row.matches()) {
                log.warn("Name paradigm row of {} is not 'ending|gender: endings' and is ignored", language);
                continue;
            }
            if (row.group(3) == null) {
                rows.add(rowOf(row.group(1), row.group(2), row.group(4)));
            }
        }
        final Table table = new Table(List.copyOf(rows), nonConsonants(language, rows));
        log.debug(
                "Name paradigm language={} rows={} nonConsonantLetters={}",
                language,
                table.rows().size(),
                table.nonConsonants().size());
        return table;
    }

    private static Row rowOf(final String ending, final String gender, final String endings) {
        final Kind kind =
                switch (ending) {
                    case CONSONANT -> Kind.CONSONANT;
                    case INDECLINABLE -> Kind.INDECLINABLE;
                    default -> Kind.ENDING;
                };
        final Gender rowGender =
                switch (gender) {
                    case "m" -> Gender.MALE;
                    case "f" -> Gender.FEMALE;
                    default -> Gender.UNKNOWN;
                };
        final List<String> obliques = List.of(SPACES.split(endings.toLowerCase(Locale.ROOT)));
        return new Row(kind, ending.toLowerCase(Locale.ROOT), rowGender, obliques);
    }

    private static Set<Integer> nonConsonants(final String language, final List<Row> rows) {
        final Set<String> nounEndings = Set.copyOf(LanguageRules.bundled().wordsOf(language, "nounEndings"));
        final Set<Integer> letters = new HashSet<>();
        nounEndings.forEach(ending -> addSingle(letters, ending));
        for (final Row row : rows) {
            row.obliques().forEach(ending -> addSingle(letters, ending));
            if (row.kind() == Kind.ENDING) {
                addSingle(letters, row.ending());
                if (nounEndings.contains(row.ending())) {
                    letters.add(row.ending().codePointBefore(row.ending().length()));
                }
            }
        }
        return Set.copyOf(letters);
    }

    private static void addSingle(final Set<Integer> letters, final String ending) {
        if (ending.codePointCount(0, ending.length()) == 1) {
            letters.add(ending.codePointAt(0));
        }
    }
}
