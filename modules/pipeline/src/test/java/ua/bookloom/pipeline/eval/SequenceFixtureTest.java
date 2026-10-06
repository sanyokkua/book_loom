package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.NarratorPerson;

class SequenceFixtureTest {

    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final Pattern DIALOGUE = Pattern.compile("[\"“”]|^— ");
    private static final Pattern LATIN =
            Pattern.compile("\\*(Fiat lux|Nihil sine labore|Per ignem, per umbram|Memento mori|Sub rosa|Ad infinitum)");

    private static List<String> paragraphs() {
        return SequenceFixture.bookText()
                .lines()
                .filter(line -> !line.isBlank() && !line.startsWith("# ") && !line.startsWith(" "))
                .toList();
    }

    private static long share(final long count) {
        return Math.round(100.0 * count / paragraphs().size());
    }

    private static long words(final String line) {
        return WORD.matcher(line).results().count();
    }

    @Test
    void load_manifest_namesEightChaptersThreeOfThemFirstPerson() {
        final SequenceFixture fixture = SequenceFixture.load();

        assertThat(fixture.chapters()).hasSize(8);
        assertThat(fixture.chapters())
                .extracting(SequenceFixture.Chapter::narrator)
                .containsExactly(
                        NarratorPerson.THIRD,
                        NarratorPerson.FIRST,
                        NarratorPerson.THIRD,
                        NarratorPerson.THIRD,
                        NarratorPerson.FIRST,
                        NarratorPerson.THIRD,
                        NarratorPerson.FIRST,
                        NarratorPerson.THIRD);
        assertThat(fixture.glossary())
                .extracting(SequenceFixture.GlossarySeed::term)
                .contains("Nathaniel", "Lovelace", "Bartimaeus");
    }

    @Test
    void bookText_fixture_hasEveryChapterHeadingTheManifestNames() {
        final List<String> headings = SequenceFixture.bookText()
                .lines()
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2))
                .toList();

        assertThat(headings)
                .containsExactlyElementsOf(SequenceFixture.load().chapters().stream()
                        .map(SequenceFixture.Chapter::title)
                        .toList());
    }

    @Test
    void generate_sameSeed_givesTheCommittedBookByteForByte() {
        assertThat(SequenceBookGenerator.generate()).isEqualTo(SequenceFixture.bookText());
    }

    @Test
    void bookText_fixture_isAboutThreeHundredTwentyParagraphsAndThirteenThousandWords() {
        final long words = SequenceFixture.bookText()
                .lines()
                .mapToLong(SequenceFixtureTest::words)
                .sum();

        assertThat(paragraphs().stream().filter(line -> !line.contains("  ")).count())
                .isBetween(300L, 340L);
        assertThat(words).isBetween(12_000L, 15_000L);
    }

    @Test
    void bookText_fixture_holdsSpeechInAtLeastAFifthOfTheParagraphs() {
        final long speech = paragraphs().stream()
                .filter(line -> DIALOGUE.matcher(line).find())
                .count();

        assertThat(share(speech)).isGreaterThanOrEqualTo(20);
    }

    @Test
    void bookText_fixture_holdsLongAndShortParagraphsAtTheRealRunsDensity() {
        final long longOnes = paragraphs().stream()
                .filter(line -> words(line) >= 80 && words(line) <= 140)
                .count();
        final long shortOnes =
                paragraphs().stream().filter(line -> words(line) <= 8).count();

        assertThat(share(longOnes)).isGreaterThanOrEqualTo(10);
        assertThat(share(shortOnes)).isGreaterThanOrEqualTo(15);
        assertThat(paragraphs().stream().filter(line -> words(line) > 140)).isEmpty();
    }

    @Test
    void bookText_fixture_mixesStraightMarksIntoCurlyParagraphs() {
        final long mixed = paragraphs().stream()
                .filter(line -> line.contains("\"") && (line.contains("“") || line.contains("”")))
                .count();

        assertThat(mixed).isGreaterThanOrEqualTo(12);
    }

    @ParameterizedTest
    @CsvSource({
        "Mr,40",
        "Mrs,40",
        "Ms,40",
        "master,40",
        "imp,30",
        "magician,30",
        "boy,40",
        "sir,15",
        "pentacle,15",
        "circle,15"
    })
    void bookText_recurringTerm_occursInAtLeastTheExpectedNumberOfParagraphs(final String term, final long least) {
        final SequenceFixture.Term known = SequenceFixture.load().terms().stream()
                .filter(t -> t.term().equals(term))
                .findFirst()
                .orElseThrow();
        final Pattern pattern = Pattern.compile(known.pattern());

        final long count = paragraphs().stream()
                .filter(line -> pattern.matcher(line).find())
                .count();

        assertThat(count).isGreaterThanOrEqualTo(least);
    }

    @ParameterizedTest
    @CsvSource({
        "Nathaniel,25",
        "Bartimaeus,25",
        "Underwood,60",
        "Lovelace,25",
        "Whitlock,20",
        "Harrowgate,15",
        "Quill,20"
    })
    void bookText_name_occursInAtLeastTheExpectedNumberOfParagraphs(final String name, final long least) {
        final Pattern pattern = Pattern.compile("\\b" + name + "\\b");

        final long count = paragraphs().stream()
                .filter(line -> pattern.matcher(line).find())
                .count();

        assertThat(count).isGreaterThanOrEqualTo(least);
    }

    @Test
    void bookText_fixture_carriesTheShapesTheRealRunBrokeOn() {
        final String book = SequenceFixture.bookText();

        assertThat(book).contains("\n— ", "‘", "B-Bartimaeus", "Stoke-on-Marsh", "the Old Bailey");
        assertThat(book).contains("Great Hall", "£12", "March 14th", "1887", "4:30", "I am Bartimaeus");
        assertThat(book).contains("\n[1] ", "[2](#n2)", "**twice**", "  \nthe spirit waits");
        assertThat(paragraphs()).anyMatch(line -> line.startsWith("\"") && line.contains(" '"));
        assertThat(paragraphs().stream().filter(line -> LATIN.matcher(line).find()))
                .hasSizeGreaterThanOrEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"I laughed.", "The morning brought no rain and no peace.", "Do not trouble yourself"})
    void bookText_scriptedModelAnchor_occursExactlyOnce(final String anchor) {
        assertThat(SequenceFixture.bookText().split(Pattern.quote(anchor), -1)).hasSize(2);
    }

    @Test
    void parse_unknownNarratorMode_isRefused() {
        assertThatThrownBy(() -> SequenceNarratorMode.parse("both")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({",unset", "'',unset", "unset,unset", "SET,set", "' set ',set"})
    void parse_narratorValue_namesTheModeOrDefaultsToUnset(final String value, final String label) {
        assertThat(SequenceNarratorMode.parse(value).label()).isEqualTo(label);
    }

    @Test
    void narrator_setMode_isAFirstPersonMale() {
        assertThat(SequenceNarratorMode.SET.narrator().hasCheckableGender()).isTrue();
        assertThat(SequenceNarratorMode.UNSET.narrator().hasCheckableGender()).isFalse();
    }
}
