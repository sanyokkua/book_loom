package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.read;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TestBooks;

/**
 * The export report says what the file holds: how much the machine translated on its own, what the person reviewed,
 * what is in the source language because the run did not reach it, and what is in it because the person chose so.
 */
class ExportJobCountsTest {

    private static final int REVISED_FROM = 922;
    private static final int FLAGGED_FROM = REVISED_FROM + 16;
    private static final int PENDING_FROM = FLAGGED_FROM + 3;
    private static final int SEEDED_BODY = PENDING_FROM + 312;
    private static final int IMAGES = 7;
    private static final String LAST_SOURCE = "The very last line.";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    // 1,260 segments with 7 switched-off image descriptions; one more FLAGGED record without a target is pending too.
    @ParameterizedTest
    @CsvSource({
        "0, 1260, 941, 312, 7, 3, 922, 16, 1253, Line 1252.",
        "1, 1261, 941, 313, 7, 3, 922, 16, 1254, The very last line."
    })
    void run_partialBook_reportsEveryKindOfSegment(
            final int flaggedWithoutTarget,
            final int total,
            final int written,
            final int pending,
            final int sourceKept,
            final int flaggedWritten,
            final int autoAccepted,
            final int reviewed,
            final int verified,
            final String lastParagraph) {
        final String id = seededBook(flaggedWithoutTarget);
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(fixture.records(id)).hasSize(total);
        assertThat(report)
                .extracting(
                        ExportReport::written,
                        ExportReport::pending,
                        ExportReport::sourceKept,
                        ExportReport::flaggedWritten,
                        ExportReport::autoAccepted,
                        ExportReport::reviewed,
                        ExportReport::verifiedSegments)
                .containsExactly(written, pending, sourceKept, flaggedWritten, autoAccepted, reviewed, verified);
        assertThat(read(destination))
                .startsWith("Рядок 0.")
                .contains("Line 941 ![Figure 1](fig1.png) here.", "Line 947 ![Figure 7](fig7.png) here.")
                .endsWith(lastParagraph);
    }

    /**
     * A Markdown book whose body is accepted, revised, flagged and pending in that order — the first seven pending
     * paragraphs each holding an image whose description is switched off — followed by {@code extra} paragraphs
     * FLAGGED with no machine target.
     */
    private String seededBook(final int extra) {
        final int body = SEEDED_BODY + extra;
        final String text = IntStream.range(0, body)
                .mapToObj(ExportJobCountsTest::paragraph)
                .collect(Collectors.joining("\n\n"));
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), text),
                "en",
                new AlsoTranslate(true, false, true, false));
        final List<SegmentRecord> records = fixture.bodyRecords(id);
        IntStream.range(0, body)
                .filter(index -> index < PENDING_FROM || index >= SEEDED_BODY)
                .forEach(index -> fixture.decide(id, records.get(index).segmentId(), record -> seeded(record, index)));
        return id;
    }

    private static SegmentRecord seeded(final SegmentRecord record, final int index) {
        if (index >= SEEDED_BODY) {
            return record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(null, null);
        }
        final String target = "Рядок " + index + ".";
        final SegmentStatus status = index < REVISED_FROM
                ? SegmentStatus.ACCEPTED
                : index < FLAGGED_FROM ? SegmentStatus.REVISED : SegmentStatus.FLAGGED;
        return record.withStatus(status)
                .withPath(SegmentPath.DRAFT)
                .withReviewed(status == SegmentStatus.REVISED)
                .withMachineTarget(target, target);
    }

    private static String paragraph(final int index) {
        final int image = index - PENDING_FROM + 1;
        if (index >= SEEDED_BODY) {
            return LAST_SOURCE;
        }
        return image >= 1 && image <= IMAGES
                ? "Line " + index + " ![Figure " + image + "](fig" + image + ".png) here."
                : "Line " + index + ".";
    }
}
