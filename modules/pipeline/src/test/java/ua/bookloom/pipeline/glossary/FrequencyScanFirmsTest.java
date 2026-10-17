package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 15i.N8: a firm joined by "and" and a stop-word head written as a name each stay one candidate. */
class FrequencyScanFirmsTest {

    private static final int THRESHOLD = 3;

    static Stream<Arguments> firms() {
        return Stream.of(
                Arguments.of("a firm tail", "We called on Pell and Sons today.", "Pell and Sons", true),
                Arguments.of("a singular tail", "We called on Pell and Son today.", "Pell and Son", true),
                Arguments.of("a company tail", "We called on Pell and Company today.", "Pell and Company", true),
                Arguments.of("no tail, a person", "We called on Pell and Bea today.", "Pell and Bea", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firms")
    void candidates_firmJoinedByAnd_isOneCandidateOnlyWithAFirmTail(
            final String description, final String line, final String firm, final boolean proposed) {
        final List<String> lines = new ArrayList<>(Collections.nCopies(4, line));
        lines.addAll(Collections.nCopies(6, "Then we thanked Pell again."));

        final List<String> terms =
                namesOf(lines, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms.contains(firm)).isEqualTo(proposed);
    }

    @Test
    void candidates_firmWithNoLanguage_isNotJoined() {
        final List<String> lines = new ArrayList<>(Collections.nCopies(4, "We called on Pell and Sons today."));
        lines.addAll(Collections.nCopies(6, "Then we thanked Pell again."));

        final List<String> terms =
                namesOf(lines, "xx").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContain("Pell and Sons");
    }

    @Test
    void candidates_stopWordHeadWrittenCapitalisedMidSentence_keepsTheRun() {
        final List<String> lines = new ArrayList<>(Collections.nCopies(3, "We crossed to Other Shore at dusk."));
        lines.addAll(Collections.nCopies(6, "We rowed to the other bank at dusk."));

        final List<String> terms =
                namesOf(lines, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms).contains("Other Shore");
    }

    @Test
    void candidates_stopWordHeadWrittenTwice_isNotAName() {
        final List<String> lines = new ArrayList<>(Collections.nCopies(2, "We crossed to Other Shore at dusk."));
        lines.addAll(Collections.nCopies(6, "We rowed to the other bank at dusk."));

        final List<String> terms =
                namesOf(lines, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContain("Other Shore");
    }

    @Test
    void candidates_stopWordNeverWrittenInLowerCase_isAnHonorificNotAHead() {
        final List<String> lines = Collections.nCopies(3, "We crossed to Other Shore at dusk.");

        final List<String> terms =
                namesOf(lines, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContain("Other Shore");
    }

    @Test
    void candidates_articleOfTheLanguage_neverOpensAName() {
        final List<String> lines = new ArrayList<>(Collections.nCopies(3, "Nous lisons Le Courrier chaque jour."));
        lines.addAll(Collections.nCopies(6, "Nous lisons le journal chaque jour."));

        final List<String> terms =
                namesOf(lines, "fr").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContain("Le Courrier");
    }

    @Test
    void candidates_stopWordMostlyCapitalisedElsewhere_isNotAHead() {
        final List<String> lines = new ArrayList<>(Collections.nCopies(3, "We crossed to Other Shore at dusk."));
        lines.addAll(Collections.nCopies(9, "He said, wait, Other things remain."));
        lines.addAll(Collections.nCopies(6, "We rowed to the other bank at dusk."));

        final List<String> terms =
                namesOf(lines, "en").stream().map(NameCandidate::term).toList();

        assertThat(terms).doesNotContain("Other Shore");
    }

    private static List<NameCandidate> namesOf(final List<String> lines, final String language) {
        final BookSlice book = GlossaryTestSegments.of(lines);
        return FrequencyScan.candidates(book.index(), book.ordinals(), THRESHOLD, language);
    }
}
