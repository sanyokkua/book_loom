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
 * language) gets the identity table: the form is its own nominative. Vocative rows are not read the other way round.
 *
 * <p>Forward, {@link #forms} generates a target's case forms and vocative from the target language's table: the most
 * specific row of the target's gender (the longest ending; {@code C} counts as none), its {@code >voc} row, and the
 * vowel changes the file's {@code nameAlternations} lists ({@code о/е>і}: an {@code і} in the last closed syllable is an
 * {@code о} or {@code е} in the other forms; {@code fleeting-е>ь}: that {@code е} becomes a soft sign). The plain forms
 * are kept beside the changed ones, so a form is never refused for a change the table cannot tell apart.
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
    static final Pattern SPACES = Pattern.compile("\\s+");
    /** The most forms a target of several words is given, all words' forms combined. */
    static final int MAX_FORMS = 64;

    private static final String CONSONANT = "C";
    private static final String INDECLINABLE = "indeclinable";
    private static final Pattern VOWEL_TO_I = Pattern.compile("(\\p{L}+)/(\\p{L}+)>(\\p{L})");
    private static final Pattern FLEETING = Pattern.compile("fleeting-(\\p{L})>(\\p{L})");
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

    /**
     * One reading of a written form with the row it comes from.
     *
     * @param lemma the nominative, spelled with the form's own letters up to the ending
     * @param gender the row's gender; {@link Gender#UNKNOWN} for a row of any gender or the identity table
     * @param row the row as the file names it ({@code ending|gender}, e.g. {@code C|m}); empty for the identity table
     * @param order the row's place in the file, from zero; {@link Integer#MAX_VALUE} for the identity table
     * @param nominative whether the form is this nominative itself rather than one of its other cases
     */
    public record Declension(String lemma, Gender gender, String row, int order, boolean nominative) {

        /** Validates the components. */
        public Declension {
            Objects.requireNonNull(lemma, "lemma");
            Objects.requireNonNull(gender, "gender");
            Objects.requireNonNull(row, "row");
        }

        Reading reading() {
            return new Reading(lemma, gender, nominative);
        }
    }

    /**
     * A target's forms.
     *
     * @param target the target as written
     * @param cases its case forms, the target itself left out; empty when the language has no table
     * @param vocatives its vocative forms; empty when the language has no vocative row for it
     */
    public record TargetForms(String target, Set<String> cases, Set<String> vocatives) {

        /** Copies the sets. */
        public TargetForms {
            Objects.requireNonNull(target, "target");
            cases = Set.copyOf(cases);
            vocatives = Set.copyOf(vocatives);
        }

        /**
         * Every form a rendering of the entry may take.
         *
         * @return the target, its case forms and its vocatives; never empty
         */
        public Set<String> all() {
            final Set<String> all = new LinkedHashSet<>();
            all.add(target);
            all.addAll(cases);
            all.addAll(vocatives);
            return Set.copyOf(all);
        }
    }

    enum Kind {
        CONSONANT,
        INDECLINABLE,
        ENDING
    }

    record Row(Kind kind, String ending, Gender gender, List<String> obliques, String name, int order) {}

    record Alternation(List<String> from, String to, boolean fleeting) {}

    record Table(List<Row> rows, List<Row> vocatives, List<Alternation> alternations, Set<Integer> nonConsonants) {

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
        final Set<Reading> readings = new LinkedHashSet<>();
        declensions(form, language).forEach(declension -> readings.add(declension.reading()));
        log.trace("Name form {} of {} reads as {}", form, language, readings);
        return Set.copyOf(readings);
    }

    /**
     * The nominatives a written form could belong to, each with the row that gives it.
     *
     * @param form the non-null form as written
     * @param language the source language tag, or null when it is not known (the identity table)
     * @return never empty; the form itself as a nominative of unknown gender and no row when the table gives nothing
     */
    public static List<Declension> declensions(final String form, @Nullable final String language) {
        Objects.requireNonNull(form, "form");
        final List<Declension> identity = List.of(new Declension(form, Gender.UNKNOWN, "", Integer.MAX_VALUE, true));
        if (language == null) {
            return identity;
        }
        final Table table = tableOf(language);
        final String lower = form.toLowerCase(Locale.ROOT);
        final List<Declension> nominatives = new ArrayList<>();
        final List<Oblique> obliques = new ArrayList<>();
        for (final Row row : table.rows()) {
            read(row, table, form, lower, nominatives, obliques);
        }
        final int longest = obliques.stream().mapToInt(Oblique::length).max().orElse(0);
        obliques.stream()
                .filter(oblique -> oblique.length() == longest)
                .forEach(oblique -> nominatives.add(oblique.declension()));
        return nominatives.isEmpty() ? identity : List.copyOf(nominatives);
    }

    /**
     * The case forms and the vocative of a target, from the target language's table.
     *
     * @param target the non-null target as written, of one word or more
     * @param gender the entry's gender; {@link Gender#UNKNOWN} allows the forms of both genders
     * @param language the target language tag, or null when it is not known
     * @return the forms; only the target when the language has no table
     */
    public static TargetForms forms(final String target, final Gender gender, @Nullable final String language) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(gender, "gender");
        if (language == null || !hasParadigm(language) || target.isBlank()) {
            return new TargetForms(target, Set.of(), Set.of());
        }
        return TargetDeclension.forms(target, gender, language, tableOf(language));
    }

    private record Oblique(int length, Declension declension) {}

    private static void read(
            final Row row,
            final Table table,
            final String form,
            final String lower,
            final List<Declension> nominatives,
            final List<Oblique> obliques) {
        final boolean nominative =
                switch (row.kind()) {
                    case INDECLINABLE -> row.obliques().stream().anyMatch(ending -> endsWith(lower, ending));
                    case CONSONANT ->
                        lower.length() >= MIN_STEM_LETTERS && table.isConsonant(lower.codePointBefore(lower.length()));
                    case ENDING -> endsWith(lower, row.ending());
                };
        if (nominative) {
            nominatives.add(new Declension(form, row.gender(), row.name(), row.order(), true));
        }
        if (row.kind() == Kind.INDECLINABLE) {
            return;
        }
        for (final String ending : row.obliques()) {
            if (endsWith(lower, ending) && fitsStem(row, table, lower, ending)) {
                final String stem = form.substring(0, form.length() - ending.length());
                final String lemma = row.kind() == Kind.CONSONANT ? stem : stem + row.ending();
                obliques.add(new Oblique(
                        ending.length(), new Declension(lemma, row.gender(), row.name(), row.order(), false)));
            }
        }
    }

    // A consonant row's stem must end in a consonant; any other row's stem takes the row's ending back.
    private static boolean fitsStem(final Row row, final Table table, final String lower, final String ending) {
        return row.kind() != Kind.CONSONANT
                || table.isConsonant(lower.codePointBefore(lower.length() - ending.length()));
    }

    // An ending counts only with at least MIN_STEM_LETTERS letters left before it.
    static boolean endsWith(final String lower, final String ending) {
        return lower.length() - ending.length() >= MIN_STEM_LETTERS && lower.endsWith(ending);
    }

    private static Table tableOf(final String language) {
        return TABLES.computeIfAbsent(language, NameForms::load);
    }

    private static Table load(final String language) {
        final List<Row> rows = new ArrayList<>();
        final List<Row> vocatives = new ArrayList<>();
        final List<String> written = NameParadigms.of(language);
        for (int order = 0; order < written.size(); order++) {
            final Matcher row = ROW.matcher(written.get(order).strip());
            if (!row.matches()) {
                log.warn("Name paradigm row of {} is not 'ending|gender: endings' and is ignored", language);
                continue;
            }
            final Row read = rowOf(row.group(1), row.group(2), row.group(4), order);
            (row.group(3) == null ? rows : vocatives).add(read);
        }
        final Table table = new Table(
                List.copyOf(rows), List.copyOf(vocatives), alternationsOf(language), nonConsonants(language, rows));
        log.debug(
                "Name paradigm language={} rows={} vocativeRows={} alternations={} nonConsonantLetters={}",
                language,
                table.rows().size(),
                table.vocatives().size(),
                table.alternations().size(),
                table.nonConsonants().size());
        return table;
    }

    private static List<Alternation> alternationsOf(final String language) {
        final List<Alternation> alternations = new ArrayList<>();
        for (final String written : LanguageRules.bundled().wordsOf(language, "nameAlternations")) {
            final Matcher fleeting = FLEETING.matcher(written);
            final Matcher vowel = VOWEL_TO_I.matcher(written);
            if (fleeting.matches()) {
                alternations.add(new Alternation(List.of(fleeting.group(1)), fleeting.group(2), true));
            } else if (vowel.matches()) {
                alternations.add(new Alternation(List.of(vowel.group(1), vowel.group(2)), vowel.group(3), false));
            } else {
                log.warn("Name alternation of {} is not 'a/b>c' or 'fleeting-a>b' and is ignored", language);
            }
        }
        return List.copyOf(alternations);
    }

    private static Row rowOf(final String ending, final String gender, final String endings, final int order) {
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
        return new Row(kind, ending.toLowerCase(Locale.ROOT), rowGender, obliques, ending + "|" + gender, order);
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
