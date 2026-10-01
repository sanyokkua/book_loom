package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** The deterministic name scan: which capitalised words and runs it proposes, and in what order. */
class FrequencyScanTest {

    private static final int THRESHOLD = 3;

    static Stream<Arguments> books() {
        return Stream.of(
                Arguments.of("names, runs and a name seen twice", namesAndRuns(), List.of("Hale", "Baker Street")),
                Arguments.of(
                        "a tie is ranked by first occurrence",
                        interleaved("Then Moreau left.", "Then Hale left.", THRESHOLD),
                        List.of("Moreau", "Hale")),
                Arguments.of("sentence-initial word only", List.of(forty("The dog barked. ")), List.of()),
                Arguments.of("an empty book", List.of(), List.of()),
                Arguments.of("a lone capital letter is a pronoun, not a name", copies("Then I left.", 5), List.of()),
                Arguments.of("after an honorific at a sentence start", copies("Mr. Hale nodded.", 3), List.of("Hale")),
                Arguments.of("after an honorific mid-sentence", copies("She asked Mr. Hale.", 3), List.of("Hale")),
                Arguments.of(
                        "a token between words reads as a space",
                        copies("see Hale⟦g3⟧Street now.", 3),
                        List.of("Hale Street")),
                Arguments.of("a script without capitals", copies("哈尔打开了门。", 5), List.of()),
                Arguments.of("one chapter with two mentions", copies("Then Moreau left.", 2), List.of()),
                Arguments.of("two chapters with two mentions each", copies("Then Moreau left.", 4), List.of("Moreau")),
                Arguments.of(
                        "a run of four is cut into three and one",
                        copies("Then Ann Bea Cy Di sang.", 3),
                        List.of("Ann Bea Cy", "Di")),
                Arguments.of("a comma does not join a run", copies("Then Ann, Bea sang.", 3), List.of("Ann", "Bea")),
                Arguments.of(
                        "a lower-case word ends a run", copies("Then Ann and Bea sang.", 3), List.of("Ann", "Bea")));
    }

    static Stream<Arguments> sentenceBreakBooks() {
        return Stream.of(
                Arguments.of("after a quoted full stop", copies("Go. \"Hale left.\"", 3), List.of()),
                Arguments.of(
                        "after an exclamation and an opening quote", copies("Stop! \"Hale, wait.\"", 3), List.of()),
                Arguments.of("after an ellipsis", copies("Wait… Hale ran.", 3), List.of()));
    }

    static Stream<Arguments> wordBooks() {
        return Stream.of(
                Arguments.of("a hyphenated place is one word", copies("We rode to Al-Arish.", 3), List.of("Al-Arish")),
                Arguments.of(
                        "a possessive is not part of the name", copies("We saw Hale’s house.", 3), List.of("Hale")),
                Arguments.of("an apostrophe inside a name", copies("We met O'Brien there.", 3), List.of("O'Brien")),
                Arguments.of("after an em dash", copies("He paused—Hale ran.", 3), List.of()),
                Arguments.of("after an opening quote", copies("He said, “Hale ran.”", 3), List.of()),
                Arguments.of(
                        "a word written in lower case a fifth of the time",
                        withLine(copies("We crossed the Bridge.", 4), "The bridge fell."),
                        List.of()),
                Arguments.of(
                        "a sentence start counts once the word is seen mid-sentence twice",
                        withLine(copies("Hale ran.", 3), "We saw Hale.", "We saw Hale."),
                        List.of("Hale")),
                Arguments.of(
                        "a sentence start seen mid-sentence once",
                        withLine(copies("Hale ran.", 3), "We saw Hale."),
                        List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "uk, Він сказав Так тихо., ''",
        "en, Він сказав Так тихо., Так",
        "en, They said Yes loudly., ''",
        "xx, They said Yes loudly., ''",
        "de, Sie sah Herr Brandt dort., Brandt",
        "fr, Il vit Monsieur Hale ici., Hale",
        "es, Vio a Don Diego allí., Diego",
        "pl, Widział Pan Kowalski tam., Kowalski"
    })
    void candidates_stopWordOfTheLanguage_isNeverAName(
            final String language, final String line, final String expected) {
        final List<String> terms =
                FrequencyScan.candidates(GlossaryTestSegments.of(copies(line, 3)), THRESHOLD, language).stream()
                        .map(NameCandidate::term)
                        .toList();

        assertThat(String.join(",", terms)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"books", "sentenceBreakBooks", "wordBooks"})
    void candidates_bookText_proposesExactlyTheTerms(
            final String description, final List<String> lines, final List<String> expectedTerms) {
        final List<String> terms = FrequencyScan.candidates(GlossaryTestSegments.of(lines), THRESHOLD, "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms).containsExactlyElementsOf(expectedTerms);
    }

    @Test
    void candidates_minimumOne_returnsCountAndFirstSentenceOfTheFirstMention() {
        final List<String> lines = List.of("She met Moreau in the hall. He left.", "Then Moreau left.");

        final List<NameCandidate> candidates = FrequencyScan.candidates(GlossaryTestSegments.of(lines), 1, "en");

        assertThat(candidates).containsExactly(new NameCandidate("Moreau", 2, "She met Moreau in the hall."));
    }

    private static List<String> namesAndRuns() {
        final List<String> lines = new ArrayList<>(copies("We saw Hale at the door.", 5));
        lines.addAll(copies("They walked to Baker Street later.", 3));
        lines.addAll(copies("She met Moreau there.", 2));
        return lines;
    }

    private static List<String> interleaved(final String first, final String second, final int times) {
        return Collections.nCopies(times, List.of(first, second)).stream()
                .flatMap(List::stream)
                .toList();
    }

    private static List<String> withLine(final List<String> lines, final String... more) {
        final List<String> all = new ArrayList<>(lines);
        all.addAll(List.of(more));
        return all;
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    private static String forty(final String sentence) {
        return sentence.repeat(40);
    }
}
