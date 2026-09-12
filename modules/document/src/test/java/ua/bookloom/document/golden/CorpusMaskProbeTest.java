package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;

/**
 * Task 10.1's mask-then-restore probe over the harness's own small, hand-built fixtures — never the real corpus
 * (that path stays behind {@link CorpusVerificationTest}'s {@code corpus}-tagged test). Exercises the probe
 * exactly as {@link CorpusSweep} wires it in ({@link #verifyOneBook_markdownWithABoldSpan_recordsPlaceholderStatistics})
 * and, separately, {@link CorpusMaskProbe#run} directly against a document whose segment carries a source that
 * cannot possibly match what restoring its own masked form produces, to prove the catch-and-record discipline
 * task 10.1 asks for without depending on any book that could trip it.
 */
class CorpusMaskProbeTest {

    // WHEN the corpus verification runs, the system SHALL restore each book's segments from
    // their own masked forms and record its placeholder statistics.
    @Test
    void verifyOneBook_markdownWithABoldSpan_recordsPlaceholderStatistics(@TempDir Path tempDir) throws IOException {
        final Path corpusDir = tempDir.resolve("corpus");
        Files.createDirectories(corpusDir);
        final Path book = corpusDir.resolve("book.md");
        // A bold span masks to exactly two atomic placeholders (its opening and closing "**" delimiters) — see
        // MarkdownMasker#collectPaired; the heading and the plain paragraph carry no inline construct at all.
        Files.writeString(
                book, "# Title\n\nPlain paragraph one.\n\nParagraph with **bold** word.\n", StandardCharsets.UTF_8);

        final CorpusBookOutcome outcome = CorpusSweep.verifyOneBook(book, corpusDir, tempDir.resolve("work"));

        assertThat(outcome.mask()).isInstanceOfSatisfying(CorpusMaskOutcome.Completed.class, completed -> {
            assertThat(completed.segmentCount())
                    .as("mask probe segment count matches the open probe's own count")
                    .isEqualTo(((CorpusOpenOutcome.Opened) outcome.open()).segmentCount());
            assertThat(completed.totalPlaceholders()).isEqualTo(2);
            assertThat(completed.maxPlaceholdersInOneSegment()).isEqualTo(2);
            assertThat(completed.segmentsWithPlaceholders()).isEqualTo(1);
            assertThat(completed.ok()).isTrue();
            assertThat(completed.mismatchCount()).isZero();
            assertThat(completed.skippedCodeOnlyBlocks()).isNull();
            assertThat(completed.failureMessage()).isNull();
        });
    }

    // WHEN the verification runs over a corpus in which one book fails to open, THEN that
    // book's failure is recorded with its error code, AND the mask probe attempts nothing for it, since a book
    // that never opened has no segments to restore.
    @Test
    void verifyOneBook_bookFailsToOpen_recordsMaskAsNotAttempted(@TempDir Path tempDir) throws IOException {
        final Path corpusDir = tempDir.resolve("corpus");
        Files.createDirectories(corpusDir);
        final Path book = corpusDir.resolve("bad.epub");
        Files.write(book, "not a zip archive".getBytes(StandardCharsets.UTF_8));

        final CorpusBookOutcome outcome = CorpusSweep.verifyOneBook(book, corpusDir, tempDir.resolve("work"));

        assertThat(outcome.open()).isInstanceOf(CorpusOpenOutcome.Failed.class);
        assertThat(outcome.mask()).isInstanceOf(CorpusMaskOutcome.NotAttempted.class);
    }

    // WHEN a book's round trip is not faithful, THEN its outcome is recorded as a distinct
    // failed outcome rather than the run throwing, so a run can distinguish "nothing threw" from "nothing
    // changed" — proven here for the mask probe's own per-segment restore comparison, which the probe never lets
    // escape as an exception.
    @Test
    void run_aSegmentsSourceDisagreesWithWhatItsOwnMaskedFormRestoresTo_recordsAMismatchWithoutThrowing() {
        final DocumentService service = DocumentServices.newService();

        final CorpusMaskOutcome outcome = CorpusMaskProbe.run(service, documentWithAnUnrestorableSegment());

        assertThat(outcome).isInstanceOfSatisfying(CorpusMaskOutcome.Completed.class, completed -> {
            assertThat(completed.segmentCount()).isEqualTo(1);
            assertThat(completed.totalPlaceholders()).isEqualTo(1);
            assertThat(completed.maxPlaceholdersInOneSegment()).isEqualTo(1);
            assertThat(completed.segmentsWithPlaceholders()).isEqualTo(1);
            assertThat(completed.ok()).isFalse();
            assertThat(completed.mismatchCount()).isEqualTo(1);
            assertThat(completed.failureMessage()).isNotBlank();
        });
    }

    /**
     * A one-segment TXT document whose {@code sourceInner} ({@code "World"}) can never equal what restoring its
     * own {@code masked} form ({@code "⟦g0⟧"}, mapped to {@code "Hello"}) produces — a state the real masking
     * pass never reaches, built by hand purely to exercise the probe's mismatch-counting path deterministically.
     */
    private static Document documentWithAnUnrestorableSegment() {
        final Segment segment = new Segment(
                "unit-1:0",
                "unit-1",
                0,
                SegmentKind.PARAGRAPH,
                "World",
                "⟦g0⟧",
                Map.of("g0", "Hello"),
                "0000000000000000000000000000000000000000000000000000000000000000",
                null,
                null,
                new ByteSpanAnchor(0, 5),
                null,
                SegmentStatus.PENDING,
                0.0);
        final Unit unit =
                new Unit("unit-1", 0, "unit-1", "text/plain", new SkeletonHandle("handle-1"), List.of(segment));
        return new Document("doc-1", BookFormat.TXT, null, null, "UTF-8", false, "cafebabe", Map.of(), List.of(unit));
    }
}
