package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.SegmentTargets;
import ua.bookloom.document.fixture.BodyContentEpub;
import ua.bookloom.document.fixture.Fb2Fixtures;

/**
 * A unit's writes are planned against the tree as it stands <em>before</em> the first of them lands.
 *
 * <p>A run index is resolved by splitting a block on its direct line-break children, so it is a function of the
 * block's current content. The placeholder-multiset gate is order-insensitive on purpose — a translation
 * legitimately reorders inline markup — so it accepts a target that moves a {@code <br/>}-mapped token out of
 * nested inline markup and up to the block's top level, which changes how the block splits. Writing segment zero
 * first therefore re-split the block from two runs into three, after which segment one's recorded run index of 1
 * addressed the range segment zero's translation had just been written into.
 *
 * <p>Measured on {@code <div>a<em>x<br/>y</em>b<br/>SECOND</div>} with the writers reverted, both tree formats
 * produce the same corruption from that one gate-passing target: EPUB ends as
 * {@code <div>a<br/>DRUHYJ<br/>SECOND</div>} and FB2 as {@code <p>a<br/>DRUHYJ<br/>SECOND</p>} — in both, the
 * first segment's translation is deleted and the second segment's source is left untranslated. Each test below
 * asserts the whole written block rather than the presence of one translation, because both halves of the defect
 * are in the same string, and both formats are asserted because they reach the shared planner through two
 * different {@code TreeNode} adapters over two different parsers.
 */
class UnitWriteBackPlanningTest {

    /** A block whose second run is reachable only after the first run's own line break is accounted for. */
    private static final String REORDER_BODY = "<div>a<em>x<br />y</em>b<br />SECOND</div>\n";

    private static final String REORDER_FB2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>S</book-title><lang>uk</lang></title-info></description>
              <body><section><p>a<emphasis>x<br />y</emphasis>b<br />SECOND</p></section></body>
            </FictionBook>
            """;

    private static final String RUNS_FB2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description><title-info><book-title>S</book-title><lang>uk</lang></title-info></description>
              <body><section><p>Run one<br />Run two<br />Run three</p></section></body>
            </FictionBook>
            """;

    /** A target that moves the `<br/>`-mapped token out of the emphasis; the gate passes it, being order-blind. */
    private static final String REORDERED_TARGET = "a⟦g1⟧⟦g0⟧xy⟦g2⟧b";

    @TempDir
    private Path tempDir;

    // WHEN a document is reassembled, the system SHALL write each segment's target into the
    // run its anchor addresses, so that no segment's write lands on another segment's text.
    @Test
    void write_epubBlockWhoseFirstSegmentReordersALineBreak_landsBothSegmentsCorrectly() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, BodyContentEpub.withBody(tempDir.resolve("in.epub"), REORDER_BODY));
        final List<Segment> segments = segmentsOf(opened);
        final String firstTarget = restore(service, BookFormat.EPUB, segments.get(0), REORDERED_TARGET);

        final Path written = write(service, SegmentTargets.withTargets(opened, List.of(firstTarget, "DRUHYJ")), "epub");

        assertThat(ZipText.firstMemberEndingWith(written, ".xhtml"))
                .contains("<div>a<br /><em>xy</em>b<br />DRUHYJ</div>")
                .doesNotContain("SECOND");
    }

    // WHEN a document is reassembled, the system SHALL write each segment's target into the
    // run its anchor addresses, so that no segment's write lands on another segment's text.
    // The FB2 half of the same defect. The planner is shared, but each format reaches it through its own
    // TreeNode adapter over its own parser, and it was the FB2 adapter's fragment reparse that turned a
    // mis-addressed write into a thrown exception in the neighbouring XML-character defect — so one format's
    // assertion cannot stand in for the other's here either.
    @Test
    void write_fb2BlockWhoseFirstSegmentReordersALineBreak_landsBothSegmentsCorrectly() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, fb2(REORDER_FB2, "reorder-in.fb2"));
        final List<Segment> segments = segmentsOf(opened);
        final String firstTarget = restore(service, BookFormat.FB2, segments.get(0), REORDERED_TARGET);

        final Path written = write(service, SegmentTargets.withTargets(opened, List.of(firstTarget, "DRUHYJ")), "fb2");

        assertThat(readUtf8(written))
                .contains("<p>a<br /><emphasis>xy</emphasis>b<br />DRUHYJ</p>")
                .doesNotContain("SECOND");
    }

    // WHEN a document is reassembled, the system SHALL write each segment's target into the
    // run its anchor addresses, so that no segment's write lands on another segment's text.
    // The ordinary path, which batching must not have broken: three runs of one block, no reordering, each
    // segment's own translation in its own run and in its own order.
    @Test
    void write_epubBlockOfThreeRunsWithNoReordering_writesEveryRunToItsOwnSegment() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(
                service,
                BodyContentEpub.withBody(
                        tempDir.resolve("runs.epub"), "<div>Run one<br />Run two<br />Run three</div>\n"));

        final Path written =
                write(service, SegmentTargets.withTargets(opened, List.of("Один", "Два", "Три")), "runs-out.epub");

        assertThat(ZipText.firstMemberEndingWith(written, ".xhtml"))
                .contains("<div>Один<br />Два<br />Три</div>")
                .doesNotContain("Run one", "Run two", "Run three");
    }

    // WHEN a document is reassembled, the system SHALL write each segment's target into the
    // run its anchor addresses, so that no segment's write lands on another segment's text.
    @Test
    void write_fb2BlockOfThreeRunsWithNoReordering_writesEveryRunToItsOwnSegment() {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, fb2(RUNS_FB2, "runs-in.fb2"));

        final Path written =
                write(service, SegmentTargets.withTargets(opened, List.of("Один", "Два", "Три")), "runs-out.fb2");

        assertThat(readUtf8(written))
                .contains("<p>Один<br />Два<br />Три</p>")
                .doesNotContain("Run one", "Run two", "Run three");
    }

    private static String restore(DocumentService service, BookFormat format, Segment segment, String target) {
        final Result<String> restored = service.unmask(format, segment, target);
        assertThat(restored.isOk())
                .withFailMessage("the reordered target must pass the order-blind gate, but: %s", restored.error())
                .isTrue();
        return Objects.requireNonNull(restored.data(), "data");
    }

    private Path fb2(String xml, String fileName) {
        return Fb2Fixtures.writeFb2(tempDir.resolve(fileName), xml, StandardCharsets.UTF_8);
    }

    private Path write(DocumentService service, Document document, String outputName) {
        final Result<Path> written = service.write(document, tempDir.resolve("out-" + outputName), "uk");
        assertThat(written.isOk())
                .withFailMessage("write failed: %s", written.error())
                .isTrue();
        return Objects.requireNonNull(written.data(), "data");
    }

    private static Document open(DocumentService service, Path file) {
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "document");
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }

    private static String readUtf8(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
