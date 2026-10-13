package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.glossary.PronounGender;

/**
 * The committed names books (15i.E1), one failure shape each, held to what they promise with no model: they load with
 * a gold that names clusters, junk, real names and recurring terms; every gold term occurs in the text; and the shape
 * that makes a book hard is really in it (the pronoun tally the code reads, the strings the scan sees).
 */
class NamesBookTest {

    private static final String SOURCE = "en";

    @TempDir
    private Path dir;

    static Stream<NamesBook> books() {
        return NamesBook.all().stream();
    }

    static Stream<Arguments> goldCharacters() {
        return books().flatMap(book ->
                book.gold().characters().stream().map(person -> Arguments.of(book.id(), person.term(), book)));
    }

    static Stream<Arguments> goldTerms() {
        return books().flatMap(book -> Stream.of(
                        book.gold().junk().stream(),
                        book.gold().keep().stream(),
                        book.gold().recurringTerms().stream().map(JudgementGold.Recurring::term))
                .flatMap(terms -> terms)
                .map(term -> Arguments.of(book.id(), term, book)));
    }

    private static int mentions(final NamesBook book, final String term) {
        final Matcher matcher = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])")
                .matcher(String.join("\n", book.paragraphs()));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int caseInsensitiveMentions(final NamesBook book, final String term) {
        final Matcher matcher = Pattern.compile(
                        "(?<![\\p{L}\\p{N}])" + Pattern.quote(term.toLowerCase(Locale.ROOT)) + "(?![\\p{L}\\p{N}])")
                .matcher(String.join("\n", book.paragraphs()).toLowerCase(Locale.ROOT));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void books_ids_areTheShapesTheTaskNames() {
        assertThat(NamesBook.all())
                .extracting(NamesBook::id)
                .containsExactly(
                        "misattributed-she",
                        "short-and-full-form",
                        "short-and-full-weak",
                        "city-her",
                        "stray-he",
                        "bartender-mark",
                        "brand-and-camera",
                        "groups-nationalities-converter",
                        "technical-headings",
                        "mayday");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void book_gold_isReadWithItsLanguagesAndAtLeastOneThingToScore(final NamesBook book) {
        assertThat(book.gold().sourceLanguage()).isEqualTo(SOURCE);
        assertThat(book.gold().targetLanguage()).isEqualTo("uk");
        assertThat(book.paragraphs()).hasSizeBetween(15, 45);
        assertThat(book.gold().characters().size()
                        + book.gold().junk().size()
                        + book.gold().keep().size())
                .isPositive();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("goldCharacters")
    void book_goldCharacter_isMentionedAtLeastFiveTimesAndHasACluster(
            final String id, final String term, final NamesBook book) {
        assertThat(mentions(book, term)).as(id + " " + term).isGreaterThanOrEqualTo(5);
        assertThat(book.gold().characters())
                .filteredOn(person -> person.term().equals(term))
                .singleElement()
                .satisfies(person -> assertThat(person.cluster()).isNotBlank());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("goldTerms")
    void book_goldJunkKeepAndRecurringTerm_occursInTheText(final String id, final String term, final NamesBook book) {
        assertThat(caseInsensitiveMentions(book, term)).as(id + " " + term).isPositive();
    }

    @Test
    void book_knownFailure_namesTheTaskThatFixesIt() {
        assertThat(NamesBook.all().stream().filter(NamesBook::knownFailure))
                .allSatisfy(book -> assertThat(book.fixedBy()).matches("15i\\.[A-Z]\\d+"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void book_imported_hasTheParagraphsItWasWritten(final NamesBook book) {
        final SequenceJobs.Prepared prepared = SequenceJobs.importBook(
                book.write(dir), SequenceJobs.brief(SOURCE, "uk", QualityDial.FAST, Narrator.unspecified()));

        assertThat(prepared.document().units()).isNotEmpty();
    }

    @Test
    void misattributedShe_corinsPronounsAsTheCodeReadsThem_areFiveSheAndNineHeSoNoGenderIsDecided() {
        final PronounGender.Evidence corin =
                PronounGender.of("Corin", NamesBook.byId("misattributed-she").paragraphs(), SOURCE);

        assertThat(corin.femaleWords()).hasSize(5);
        assertThat(corin.maleWords()).hasSize(9);
        assertThat(corin.dominant()).isEmpty();
        assertThat(corin.isStrong()).isFalse();
    }

    @Test
    void misattributedShe_firstUseOfCorin_isASheSentenceSoTheFirstWindowShowsFemale() {
        assertThat(NamesBook.byId("misattributed-she").paragraphs().getFirst())
                .isEqualTo("Corin carried the tray to the nurse. She thanked him and went on folding sheets.");
    }

    // No book is red since 15i.N5 read the bartender's name in context: the she that is not his was fixed by N1, the
    // brand written with an ampersand by N3 and a plain word's capital by N5.
    @Test
    void books_knownFailures_noneSinceTheNamesAreReadInContext() {
        assertThat(NamesBook.all().stream().filter(NamesBook::knownFailure)).isEmpty();
    }

    // IF either spelling alone were already a strong case, THEN the book would not show why the tallies must be pooled.
    @Test
    void shortAndFullWeak_pronounTallies_areThreeAndTwoSheAndNeitherIsStrongAlone() {
        final List<String> paragraphs = NamesBook.byId("short-and-full-weak").paragraphs();

        final PronounGender.Evidence full = PronounGender.of("Zephra Quillon", paragraphs, SOURCE);
        final PronounGender.Evidence shortName = PronounGender.of("Zephra", paragraphs, SOURCE);

        assertThat(full.femaleWords()).hasSize(3);
        assertThat(shortName.femaleWords()).hasSize(2);
        assertThat(full.isStrong()).isFalse();
        assertThat(shortName.isStrong()).isFalse();
        assertThat(full.and(shortName).isStrong()).isTrue();
    }

    @Test
    void misattributedShe_scriptedModel_saysFemaleForCorinCitingFourWindows() {
        assertThat(NamesBook.byId("misattributed-she").model().verdicts())
                .singleElement()
                .satisfies(verdict -> {
                    assertThat(verdict.term()).isEqualTo("Corin");
                    assertThat(verdict.gender()).isEqualTo("female");
                    assertThat(verdict.windows()).containsExactly(1, 2, 3, 4);
                });
    }

    @Test
    void strayHe_perpetuasPronouns_areSevenSheAndOneHeAndDecideFemale() {
        final PronounGender.Evidence perpetua =
                PronounGender.of("Perpetua", NamesBook.byId("stray-he").paragraphs(), SOURCE);

        assertThat(perpetua.femaleWords()).hasSize(7);
        assertThat(perpetua.maleWords()).hasSize(1);
        assertThat(perpetua.dominant()).contains(Gender.FEMALE);
    }

    @Test
    void shortAndFullForm_bothWrittenFormsOfDax_occurAndShareOneCluster() {
        final NamesBook book = NamesBook.byId("short-and-full-form");

        assertThat(mentions(book, "Dax Orlo")).isGreaterThanOrEqualTo(5);
        assertThat(book.gold().characters())
                .filteredOn(person -> person.term().startsWith("Dax"))
                .extracting(JudgementGold.Person::cluster)
                .containsOnly("dax")
                .hasSize(2);
    }

    @Test
    void cityHer_valtern_isAPlaceWithNoGenderAndBankersAreWorthKeeping() {
        final NamesBook book = NamesBook.byId("city-her");

        assertThat(book.gold().characters())
                .filteredOn(person -> person.term().equals("Valtern"))
                .singleElement()
                .satisfies(person -> {
                    assertThat(person.type()).isEqualTo(TermType.PLACE);
                    assertThat(person.gender()).isEqualTo(Gender.UNKNOWN);
                });
        assertThat(book.paragraphs())
                .filteredOn(text -> text.contains("Valtern") && text.contains("Her bankers"))
                .hasSize(8);
        assertThat(book.gold().recurringTerms()).containsExactly(new JudgementGold.Recurring("bankers", true));
    }

    @Test
    void bartenderMark_book_holdsTheNameAndTheCommonWordTogether() {
        final NamesBook book = NamesBook.byId("bartender-mark");

        assertThat(mentions(book, "Mark")).isGreaterThanOrEqualTo(10);
        assertThat(String.join("\n", book.paragraphs()))
                .contains("a mark on the wall", "the guild's mark", "\"Mark the spot,\"");
        assertThat(book.gold().recurringTerms()).containsExactly(new JudgementGold.Recurring("mark", false));
    }

    @Test
    void brandAndCamera_book_writesTheBrandAndTheCameraEveryWayTheShapeNames() {
        final NamesBook book = NamesBook.byId("brand-and-camera");
        final String text = String.join("\n", book.paragraphs());

        assertThat(text).contains("Orrin & Vale", "KESSEL OPTIK", "Kessel Optik", "Kessels");
        assertThat(book.gold().keep()).containsExactly("Orrin & Vale", "Kessel Optik");
        assertThat(book.gold().junk()).containsExactlyInAnyOrder("Orrin", "Vale", "KESSEL OPTIK", "Kessels");
    }

    @Test
    void groupsNationalitiesConverter_book_putsTheConvertersNameInFrontMatter() {
        final NamesBook book = NamesBook.byId("groups-nationalities-converter");

        assertThat(book.chapters().getFirst().title()).isEqualTo("About this edition");
        assertThat(book.chapters().getFirst().paragraphs())
                .allSatisfy(text -> assertThat(text).contains("Jorun Falk"));
        assertThat(book.gold().junk()).contains("Velarian", "Velarians", "Ostrene", "Guildsmen", "Jorun Falk");
    }

    @Test
    void technicalHeadings_book_repeatsParagraphLengthCapitalisedHeadings() {
        final NamesBook book = NamesBook.byId("technical-headings");

        assertThat(book.paragraphs()).filteredOn("Output Stage"::equals).hasSize(2);
        assertThat(book.paragraphs()).contains("Power Supply Unit", "Signal Path", "Gain Stage");
        assertThat(book.gold().junk())
                .containsExactly("Power Supply Unit", "Signal Path", "Gain Stage", "Output Stage");
    }

    @Test
    void mayday_book_usesTheCallInSpeechAndNarrationAndGoldCallsItJunk() {
        final NamesBook book = NamesBook.byId("mayday");

        assertThat(mentions(book, "Mayday")).isEqualTo(5);
        assertThat(book.gold().junk()).containsExactly("Mayday");
    }
}
