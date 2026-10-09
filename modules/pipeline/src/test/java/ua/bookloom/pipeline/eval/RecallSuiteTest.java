package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.document.DocumentModule;

/**
 * The detector-recall suite offline, on an invented English → Ukrainian mini-book (`eval/recall/mini`): eight
 * paragraphs, the exported book saved under another file name, a gold file of seven read segments (a dropped sentence,
 * an untranslated paragraph, a changed number, a wrong meaning no detector can see, three clean ones — one with a year
 * written out in words that the number check doubts) and one stale line. Every expected number is counted by hand.
 */
class RecallSuiteTest {

    private static final String MINI = "/eval/recall/mini/";
    private static final List<String> FILES =
            List.of(RecallBook.FILE, "source.md", "exported.md", "glossary.csv", RecallGold.FILE);

    @TempDir
    private Path root;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    private Path miniBook(final Path parent) {
        final Path dir = parent.resolve("mini");
        try {
            Files.createDirectories(dir);
            for (final String name : FILES) {
                try (InputStream in =
                        Objects.requireNonNull(RecallSuiteTest.class.getResourceAsStream(MINI + name), name)) {
                    Files.copy(in, dir.resolve(name));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    @Test
    void run_miniBook_countsAlignedReadDefectiveAndStaleGold() {
        miniBook(root);

        final RecallReport report = RecallSuite.run(root, documents);

        assertThat(report.aligned()).isEqualTo(8);
        assertThat(report.unaligned()).isZero();
        assertThat(report.readCount()).isEqualTo(7);
        assertThat(report.defective()).isEqualTo(4);
        assertThat(report.staleGold()).isEqualTo(1);
        assertThat(report.recall()).isEqualTo(0.75);
        assertThat(report.falseAlarm()).isEqualTo(1.0 / 3);
    }

    @Test
    void detectors_miniBook_scoreEveryRunCheckAndAuditCheckThatFiredOrWasExpected() {
        miniBook(root);

        final RecallReport report = RecallSuite.run(root, documents);

        assertThat(report.detectors())
                .extracting(
                        RecallReport.Detector::name,
                        RecallReport.Detector::fired,
                        RecallReport.Detector::truePositive,
                        RecallReport.Detector::expected,
                        RecallReport.Detector::caught)
                .containsExactly(
                        tuple("audit:language-identity", 1, 1, 1, 1),
                        tuple("audit:number", 2, 1, 1, 1),
                        tuple("audit:sentence-count", 1, 1, 1, 1),
                        tuple("echo", 1, 1, 0, 0),
                        tuple("language-identity", 1, 1, 1, 1),
                        tuple("length", 1, 1, 0, 0),
                        tuple("number", 2, 1, 1, 1),
                        tuple("script", 1, 1, 0, 0),
                        tuple("sentence-count", 1, 1, 1, 1));
    }

    @Test
    void detectors_numberCheck_hasHalfPrecisionAndNoRecallWhenNothingIsExpected() {
        miniBook(root);

        final List<RecallReport.Detector> detectors =
                RecallSuite.run(root, documents).detectors();

        assertThat(detectors)
                .filteredOn(detector -> detector.name().equals("number"))
                .singleElement()
                .satisfies(detector -> {
                    assertThat(detector.precision()).isEqualTo(0.5);
                    assertThat(detector.recall()).isEqualTo(1.0);
                });
        assertThat(detectors)
                .filteredOn(detector -> detector.name().equals("echo"))
                .singleElement()
                .satisfies(detector -> assertThat(detector.recall()).isNull());
    }

    @Test
    void classes_miniBook_reportRecallByAnyDetectorAndPrecisionByTheExpectedOnes() {
        miniBook(root);

        final RecallReport report = RecallSuite.run(root, documents);

        assertThat(report.classes())
                .extracting(
                        RecallReport.DefectClass::name,
                        RecallReport.DefectClass::segments,
                        RecallReport.DefectClass::caught,
                        RecallReport.DefectClass::recall,
                        RecallReport.DefectClass::flagged,
                        RecallReport.DefectClass::precision)
                .containsExactly(
                        tuple("changed-number", 1, 1, 1.0, 2, 0.5),
                        tuple("dropped-sentence", 1, 1, 1.0, 1, 1.0),
                        tuple("untranslated", 1, 1, 1.0, 1, 1.0),
                        tuple("wrong-meaning", 1, 0, 0.0, 0, null));
    }

    @Test
    void report_miniBook_holdsNamesAndCountsButNoBookText() {
        miniBook(root);

        final RecallReport report = RecallSuite.run(root, documents);

        assertThat(report.json())
                .contains("\"suite\":\"recall\"", "\"name\":\"sentence-count\"", "\"precision\":null")
                .doesNotContain("harbour", "lighthouse", "Гавань", "кораблів");
        assertThat(report.table())
                .startsWith("promptEval recall books=1 aligned=8 read=7 defective=4 unaligned=0 staleGold=1"
                        + " recall=75% falseAlarm=33%")
                .doesNotContain("harbour", "Гавань");
    }

    @Test
    void run_miniBook_writesEverySegmentWithItsHashForTheReading() throws IOException {
        final Path dir = miniBook(root);

        RecallSuite.run(root, documents);

        final List<String> lines = Files.readAllLines(dir.resolve(RecallSuite.SEGMENTS), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(8);
        assertThat(lines.get(3))
                .isEqualTo("{\"id\":\"source.md:3\","
                        + "\"hash\":\"d3cd74cc231fd0a0c2402366bbf570ac4760c0f6ea765939236817539ee2c2d3\","
                        + "\"source\":\"The wind rose over the water.\",\"target\":\"Над водою здійнявся вітер.\","
                        + "\"fired\":[]}");
    }

    @Test
    void run_rootIsTheBookDirectory_readsTheOneBook() {
        final Path dir = miniBook(root);

        assertThat(RecallSuite.run(dir, documents).aligned()).isEqualTo(8);
    }

    @Test
    void run_noBookJson_failsNamingTheRoot() {
        assertThatThrownBy(() -> RecallSuite.run(root, documents))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("book.json");
    }

    @Test
    void hash_sourceAndTarget_isSha256OfBothJoinedByTheUnitSeparator() {
        assertThat(RecallGold.hash("The wind rose over the water.", "Над водою здійнявся вітер."))
                .isEqualTo("d3cd74cc231fd0a0c2402366bbf570ac4760c0f6ea765939236817539ee2c2d3");
    }

    @Test
    void read_malformedLine_failsWithItsLineNumber() throws IOException {
        final Path gold =
                Files.writeString(root.resolve(RecallGold.FILE), "{\"hash\":\"a\",\"classes\":[]}\n{\"hash\":\"b\"}\n");

        assertThatThrownBy(() -> RecallGold.read(gold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void read_repeatedHash_failsWithItsLineNumber() throws IOException {
        final Path gold = Files.writeString(
                root.resolve(RecallGold.FILE),
                "{\"hash\":\"a\",\"classes\":[]}\n{\"hash\":\"a\",\"classes\":[\"x\"]}\n");

        assertThatThrownBy(() -> RecallGold.read(gold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2 repeats hash a");
    }

    @Test
    void read_noFile_isEmpty() {
        assertThat(RecallGold.read(root.resolve(RecallGold.FILE))).isEmpty();
    }
}
