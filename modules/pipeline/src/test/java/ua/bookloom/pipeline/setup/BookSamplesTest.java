package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** Where the brief's single windows are taken from, how big they are, and what is never read as story. */
class BookSamplesTest {

    private static final Pattern LINE = Pattern.compile("Line (\\d+) goes");
    private static final int CHAPTER_SENTENCES = 200;

    @TempDir
    private Path tempDir;

    private Document book(final String markdown) {
        final Desk desk = ReviewFixtures.markdown(tempDir, markdown);
        return Objects.requireNonNull(desk.openProjects().get(desk.projectId()));
    }

    /** Sentences {@code from} to {@code to}, {@code perParagraph} to a paragraph, each "Line n goes on here, ...". */
    private static String lines(final int from, final int to, final int perParagraph) {
        return IntStream.rangeClosed(from, to)
                .mapToObj(n -> "Line " + n + " goes on here, and the road ran on beneath the pale grey sky."
                        + ((n - from + 1) % perParagraph == 0 ? "\n\n" : " "))
                .collect(Collectors.joining())
                .strip();
    }

    /** {@code count} chapters of {@value #CHAPTER_SENTENCES} numbered sentences each, one after the other. */
    private static String chapters(final int count) {
        return IntStream.range(0, count)
                .mapToObj(chapter -> "# Chapter " + (chapter + 1) + "\n\n"
                        + lines(chapter * CHAPTER_SENTENCES + 1, (chapter + 1) * CHAPTER_SENTENCES, 4))
                .collect(Collectors.joining("\n\n"));
    }

    private static List<Integer> lineNumbers(final BookSample sample) {
        final Matcher matcher = LINE.matcher(String.join("\n", sample.passages()));
        return matcher.results().map(found -> Integer.parseInt(found.group(1))).toList();
    }

    private static int firstLine(final BookSample sample) {
        return lineNumbers(sample).getFirst();
    }

    private static int lastLine(final BookSample sample) {
        return lineNumbers(sample).getLast();
    }

    private static int tokens(final BookSample sample) {
        return TokenEstimator.estimate(String.join("\n", sample.passages()), "en");
    }

    private static String allText(final List<BookSample> samples) {
        return samples.stream().flatMap(sample -> sample.passages().stream()).collect(Collectors.joining("\n"));
    }

    @ParameterizedTest
    @CsvSource({"FAST,3", "BALANCED,4", "MAX,5"})
    void windowCount_dial_isThreeFourOrFive(final QualityDial dial, final int expected) {
        assertThat(BookSamples.windowCount(dial)).isEqualTo(expected);
    }

    // The window is the dial's own size, held to what the model's window leaves after the call's system message
    // (here 700 tokens), its reply cap (here 500) and the 500-token safety margin every window keeps.
    @ParameterizedTest
    @CsvSource(
            nullValues = "none",
            value = {
                "FAST,none,1500",
                "BALANCED,none,2500",
                "MAX,none,4000",
                "MAX,16384,4000",
                "MAX,131072,4000",
                "MAX,8192,4000",
                "MAX,4096,2396",
                "BALANCED,4096,2396",
                "FAST,4096,1500"
            })
    void windowTokens_dialAndDetectedContext_isTheDialsSizeLimitedToTheRoomLeft(
            final QualityDial dial, final Integer detectedContext, final int expected) {
        assertThat(BookSamples.windowTokens(dial, detectedContext, 1200)).isEqualTo(expected);
    }

    // IF the brief read one passage at the start, THEN 590 words would decide the register of a whole book.
    @ParameterizedTest
    @CsvSource({"3,1500", "4,2500", "5,4000"})
    void of_longBook_givesTheWantedNumberOfSingleWindowsEachWithinItsTokenBudget(final int windows, final int budget) {
        final List<BookSample> samples = BookSamples.of(book(chapters(8) + "\n"), "en", windows, budget);

        assertThat(samples).hasSize(windows);
        assertThat(samples).allSatisfy(sample -> assertThat(sample.passages()).hasSize(1));
        assertThat(samples).allSatisfy(sample -> assertThat(tokens(sample)).isLessThanOrEqualTo(budget));
        assertThat(samples).allSatisfy(sample -> assertThat(tokens(sample)).isGreaterThanOrEqualTo(budget * 4 / 5));
    }

    // IF two windows shared a sentence, THEN the K readings would not be independent and a majority would count it
    // twice.
    @ParameterizedTest
    @CsvSource({"3,1500", "4,2500", "5,4000"})
    void of_longBook_neverLetsTwoWindowsShareASentence(final int windows, final int budget) {
        final List<BookSample> samples = BookSamples.of(book(chapters(8) + "\n"), "en", windows, budget);

        final List<Integer> firstAndLastOfEach = samples.stream()
                .flatMap(s -> Stream.of(firstLine(s), lastLine(s)))
                .toList();
        assertThat(samples).hasSize(windows);
        assertThat(firstAndLastOfEach).doesNotHaveDuplicates().isSortedAccordingTo(Comparator.naturalOrder());
    }

    // IF the windows clustered at the start, THEN a narrator who only appears late would never be read.
    @Test
    void of_bookOfEightChapters_spreadsFourWindowsFromTheFirstChapterToTheLastOne() {
        final List<BookSample> samples = BookSamples.of(book(chapters(8) + "\n"), "en", 4, 2500);

        assertThat(samples).hasSize(4);
        assertThat(firstLine(samples.getFirst())).isEqualTo(1);
        assertThat(samples)
                .extracting(sample -> (firstLine(sample) - 1) / CHAPTER_SENTENCES)
                .doesNotHaveDuplicates()
                .contains(0, 7);
        assertThat(firstLine(samples.getLast())).isBetween(1401, 1600);
        assertThat(lastLine(samples.getLast())).isBetween(1401, 1600);
    }

    // IF a converter page, an interlude of a few lines or the notes at the back were read, THEN a window would hold
    // the tool's words, not the book's.
    @Test
    void of_frontPageShortInterludeAndBackNotes_areNeverRead() {
        final Document document = book("# About this file\n\nConverted by the Tool.\n\nVisit the site for more.\n\n"
                + "# Chapter One\n\n" + lines(1, 400, 4) + "\n\n# Interlude\n\n" + lines(9001, 9010, 2)
                + "\n\n# Chapter Two\n\n" + lines(401, 800, 4) + "\n\n# Notes\n\n" + lines(9101, 9110, 2) + "\n");

        final List<BookSample> samples = BookSamples.of(document, "en", 4, 2500);

        assertThat(samples).hasSize(4);
        assertThat(firstLine(samples.getFirst())).isEqualTo(1);
        assertThat(allText(samples)).doesNotContain("Converted", "Visit the site");
        assertThat(samples).flatExtracting(BookSamplesTest::lineNumbers).allMatch(number -> number <= 800);
    }

    // IF a page of praise counted as story (each blurb's attribution counts as a sentence, so twelve blurbs make 24),
    // THEN the brief would read reviewers' words before the book's (15h.E3, front-matter-converter).
    @Test
    void of_praisePageOfQuotedBlurbs_isNotRead() {
        final String praise = IntStream.rangeClosed(1, 12)
                .mapToObj(n -> "\"A quiet triumph number " + n + ".\" The Northern Review")
                .collect(Collectors.joining("\n\n"));
        final Document document = book("# Praise\n\n" + praise + "\n\n# Chapter One\n\n" + lines(1, 30, 3) + "\n");

        final List<BookSample> samples = BookSamples.of(document, "en", 4, 2500);

        assertThat(allText(samples)).doesNotContain("triumph", "Northern Review");
        assertThat(samples).hasSize(1);
        assertThat(firstLine(samples.getFirst())).isEqualTo(1);
    }

    // IF a story of 360 sentences (about 7,000 tokens) were cut into four windows of 2,500, THEN they would overlap;
    // fewer windows are asked instead.
    @Test
    void of_storyTooShortForTheWantedWindows_givesFewerWindowsAndNeverOverlapsThem() {
        final List<BookSample> samples = BookSamples.of(book(lines(1, 360, 4) + "\n"), "en", 4, 2500);

        assertThat(samples).hasSizeBetween(1, 2);
        assertThat(samples)
                .flatExtracting(BookSamplesTest::lineNumbers)
                .doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(firstLine(samples.getFirst())).isEqualTo(1);
    }

    @Test
    void of_storyShorterThanOneWindow_givesOneWindowHoldingAllOfIt() {
        final List<BookSample> samples = BookSamples.of(book(lines(1, 30, 3) + "\n"), "en", 5, 4000);

        assertThat(samples).hasSize(1);
        assertThat(lineNumbers(samples.getFirst())).hasSize(30).startsWith(1).endsWith(30);
    }

    @Test
    void of_bookOfOneSentence_givesOneWindow() {
        assertThat(BookSamples.of(book("The rain had not stopped for three days, and nobody came.\n"), "en", 4, 2500))
                .hasSize(1);
    }

    @Test
    void of_bookWithNoBodyText_givesNoWindow() {
        assertThat(BookSamples.of(book("# Only a heading\n"), "en", 4, 2500)).isEmpty();
    }

    @Test
    void of_halfOfEveryParagraphQuoted_measuresHalfTheLettersAsDialogue() {
        final String paragraph = "“Abc def ghi.” Jkl mno pqr.";
        final String book = IntStream.range(0, 12).mapToObj(n -> paragraph).collect(Collectors.joining("\n\n"));

        final List<BookSample> samples = BookSamples.of(book(book + "\n"), "en", 4, 2500);

        assertThat(samples).extracting(BookSample::dialoguePercent).containsExactly(50);
    }

    @Test
    void of_noQuotedSpeech_measuresNoDialogue() {
        assertThat(BookSamples.of(book(lines(1, 30, 3) + "\n"), "en", 4, 2500))
                .extracting(BookSample::dialoguePercent)
                .containsExactly(0);
    }

    // IF a story of one and a half windows were read as one window and called whole, THEN one cited reading of a
    // stretch would stand for the whole book.
    @Test
    void of_storyFitsOneWindow_isTheWholeStory() {
        final List<BookSample> samples = BookSamples.of(book(lines(1, 30, 3) + "\n"), "en", 4, 100_000);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().wholeStory()).isTrue();
    }

    @Test
    void of_storyOfOneAndAHalfWindowsReadAsOneWindow_isNotTheWholeStory() {
        final Document document = book(lines(1, 60, 3) + "\n");
        final int storyTokens =
                tokens(BookSamples.of(document, "en", 4, 100_000).getFirst());

        final List<BookSample> samples = BookSamples.of(document, "en", 4, storyTokens * 2 / 3);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().wholeStory()).isFalse();
    }

    @Test
    void of_storyReadInSeveralWindows_noWindowIsTheWholeStory() {
        final List<BookSample> samples = BookSamples.of(book(chapters(3)), "en", 4, 800);

        assertThat(samples.size()).isGreaterThan(1);
        assertThat(samples).noneMatch(BookSample::wholeStory);
    }

    // The window text is what the prompt shows under "Passage 1:"; one window is one passage.
    @Test
    void text_oneWindow_isOnePassage() {
        final BookSample window =
                BookSamples.of(book(lines(1, 30, 3) + "\n"), "en", 4, 2500).getFirst();

        assertThat(window.text()).startsWith("Passage 1: Line 1 goes").doesNotContain("Passage 2");
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

    // A register is the narration's: the narrator's line is found, a line a character speaks (in curly, straight or
    // dash dialogue, on its own line of the window or beside narration) is not, and neither is a quote half in speech.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "The docking lights of the station.|true",
                "she said, and waited|true",
                "I lit a cigarette|false",
                "Mind the rope, sir|false",
                "Close the gate|false",
                "a cigarette,” she said|false"
            })
    void holdsInNarration_quote_isFoundOnlyOutsideTheCharactersSpeech(final String quote, final boolean expected) {
        final BookSample sample = new BookSample(List.of("""
                        “I lit a cigarette,” she said, and waited – again.
                        "Mind the rope, sir."
                        — Close the gate.
                        The docking lights of the station."""), 40, false, "en");

        assertThat(sample.holdsInNarration(quote)).isEqualTo(expected);
        assertThat(sample.holds(quote)).isTrue();
    }
}
