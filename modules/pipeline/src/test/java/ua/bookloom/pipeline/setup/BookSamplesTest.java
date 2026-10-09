package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** Where the brief's samples are taken from, and what is never read as story. */
class BookSamplesTest {

    @TempDir
    private Path tempDir;

    private Document book(final String markdown) {
        final Desk desk = ReviewFixtures.markdown(tempDir, markdown);
        return desk.openProjects().get(desk.projectId());
    }

    /** Sentences {@code from} to {@code to}, {@code perParagraph} to a paragraph, each "Line n goes on here.". */
    private static String lines(final int from, final int to, final int perParagraph) {
        return IntStream.rangeClosed(from, to)
                .mapToObj(n -> "Line " + n + " goes on here." + ((n - from + 1) % perParagraph == 0 ? "\n\n" : " "))
                .collect(Collectors.joining())
                .strip();
    }

    private static List<String> starts(final BookSample sample) {
        return sample.passages().stream()
                .map(passage -> passage.substring(0, passage.indexOf(" goes")))
                .toList();
    }

    // IF the brief read only the opening, THEN 590 words would decide the register of a whole book.
    @Test
    void of_longBook_takesTheStartTheMiddleAndALateWindowAndTheSecondSampleReadsWhatFollowsEach() {
        final Document document = book("# Chapter One\n\n" + lines(1, 300, 3) + "\n");

        final List<BookSample> samples = BookSamples.of(document, "en");

        assertThat(samples).hasSize(2);
        assertThat(starts(samples.get(0))).containsExactly("Line 1", "Line 141", "Line 221");
        assertThat(starts(samples.get(1))).containsExactly("Line 41", "Line 181", "Line 261");
        assertThat(samples.get(0).passages().getFirst()).endsWith("Line 40 goes on here.");
    }

    @Test
    void of_bookOfChapters_startsTheLateWindowAtTheStartOfALateChapter() {
        final String book = IntStream.range(0, 5)
                .mapToObj(chapter ->
                        "# Chapter " + (chapter + 1) + "\n\n" + lines(chapter * 80 + 1, chapter * 80 + 80, 4))
                .collect(Collectors.joining("\n\n"));

        final List<BookSample> samples = BookSamples.of(book(book + "\n"), "en");

        assertThat(starts(samples.get(0))).containsExactly("Line 1", "Line 201", "Line 321");
    }

    // IF a converter's page or a contents list were story, THEN its lines would be the first window the model reads.
    @Test
    void of_converterPageAndShortChapter_areNotRead() {
        final Document document = book("# About this file\n\nConverted by the Tool.\n\nVisit the site for more.\n\n"
                + "# Chapter One\n\n" + lines(1, 30, 3) + "\n\n# Notes\n\n" + lines(901, 910, 2) + "\n");

        final List<BookSample> samples = BookSamples.of(document, "en");

        final String read =
                samples.stream().flatMap(sample -> sample.passages().stream()).collect(Collectors.joining("\n"));
        assertThat(read).contains("Line 1 goes").doesNotContain("Converted", "Line 901");
        assertThat(starts(samples.get(0))).containsExactly("Line 1", "Line 11", "Line 21");
        assertThat(starts(samples.get(1))).containsExactly("Line 6", "Line 16", "Line 26");
    }

    @Test
    void of_halfOfEveryParagraphQuoted_measuresHalfTheLettersAsDialogue() {
        final String paragraph = "“Abc def ghi.” Jkl mno pqr.";
        final String book = IntStream.range(0, 12).mapToObj(n -> paragraph).collect(Collectors.joining("\n\n"));

        final List<BookSample> samples = BookSamples.of(book(book + "\n"), "en");

        assertThat(samples).extracting(BookSample::dialoguePercent).containsExactly(50, 50);
    }

    @Test
    void of_noQuotedSpeech_measuresNoDialogue() {
        assertThat(BookSamples.of(book(lines(1, 30, 3) + "\n"), "en"))
                .extracting(BookSample::dialoguePercent)
                .containsExactly(0, 0);
    }

    @Test
    void of_bookOfOneSentence_givesOneSample() {
        assertThat(BookSamples.of(book("The rain had not stopped for three days, and nobody came.\n"), "en"))
                .hasSize(1);
    }

    @Test
    void of_bookWithNoBodyText_givesNoSample() {
        assertThat(BookSamples.of(book("# Only a heading\n"), "en")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "the docking lights",
                "THE DOCKING   LIGHTS of the station",
                "“I lit a cigarette,” she said",
                "\"I lit a cigarette,\" she said",
                "I lit a cigarette ... the docking lights",
                "...waited — again."
            })
    void holds_quoteCopiedWithTheUsualChanges_isFound(final String quote) {
        final BookSample sample = new BookSample(
                List.of("“I lit a cigarette,” she said, and waited – again.\nThe docking lights of the station."), 0);

        assertThat(sample.holds(quote)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "...", "the docking lights of the moon", "the station ... I lit a cigarette"})
    void holds_quoteTheSampleDoesNotHold_isNotFound(final String quote) {
        final BookSample sample = new BookSample(
                List.of("“I lit a cigarette,” she said, and waited – again.\nThe docking lights of the station."), 0);

        assertThat(sample.holds(quote)).isFalse();
    }
}
