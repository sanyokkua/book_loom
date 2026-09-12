package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.SegmentTargets;
import ua.bookloom.document.fixture.BodyContentEpub;

/**
 * A preformatted block's leading line feeds across a full mask → identity-restore → write-back cycle, when that
 * block is <em>nested inside</em> another element that masking captures atomically.
 *
 * <p>jsoup's HTML parser discards one line feed after a {@code <pre>} or {@code <listing>} start tag, and a
 * captured fragment is parsed a second time when it is restored — so a nested listing loses a second line feed
 * unless the capture puts one back. Before the atomic branch composed its fragment recursively, the outer
 * element's arm captured raw markup and the preformatted arm was never reached, so a nested {@code <pre>} holding
 * two leading line feeds came back from an identity round trip with <strong>both</strong> gone. The impact its own
 * restorer names is silently deleting a line from inside a poem or verse block, on every export.
 *
 * <p>The zero-edit control ({@code FormatGoldenRoundTripTest} and the fixture sweep) cannot see this: it never
 * calls {@code unmask} at all, so a fragment that is only ever captured and never restored is a fixed point. Two
 * leading line feeds rather than one, for the reason {@code EpubFixtures#preTwoLeadingLineFeeds} gives — with one,
 * both sides of any comparison discard it alike and the test passes whether or not the restore exists.
 */
class NestedPreformattedFragmentRoundTripTest {

    /** Successive zero-edit writes to run for the unbounded-growth case; three is enough to show a trend. */
    private static final int REPEATED_WRITES = 3;

    @TempDir
    private Path tempDir;

    // WHEN a `<pre>` listing occurs among the children of a block that owns translatable text,
    // the system SHALL mask it as a protected span, and the restored content SHALL be identical to the source
    // content it replaced.
    @ParameterizedTest
    @CsvSource({"code,pre", "code,listing", "pre,pre"})
    void write_preformattedBlockNestedInAnAtomicFragment_keepsBothLeadingLineFeeds(String outer, String inner) {
        final String body =
                "<div>Note: <" + outer + ">a<" + inner + ">\n\nx</" + inner + ">b</" + outer + "> ends it.</div>\n";
        final Path source = BodyContentEpub.withBody(tempDir.resolve("nested.epub"), body);

        final String written = identityRestoreAndWrite(source, "nested-out.epub");

        assertThat(written).contains("<" + inner + ">\n\nx</" + inner + ">");
    }

    // WHEN a `<pre>` listing occurs among the children of a block that owns translatable text,
    // the system SHALL mask it as a protected span, and the restored content SHALL be identical to the source
    // content it replaced.
    // The line feed is restored because the parser DISCARDED one, so the same treatment applied where nothing was
    // discarded would add a line instead. `<textarea>` is exactly that case: the HTML serialization spec lists it
    // beside `<pre>` and `<listing>`, but jsoup does not strip anything from it — measured as 3, 4, 5 then 6 line
    // feeds where the source wrote 2, growing on every write without bound.
    @Test
    void write_textareaHoldingTwoLineFeeds_isStableAcrossRepeatedWrites() {
        final Path source = BodyContentEpub.withBody(
                tempDir.resolve("textarea.epub"), "<div>Note: <textarea>\n\nx</textarea> ends.</div>\n<p>Prose.</p>\n");

        final String written = writeRepeatedlyWithNoEdits(source);

        assertThat(written).contains("<textarea>\n\nx</textarea>");
        assertThat(written).doesNotContain("<textarea>\n\n\n");
    }

    /** Opens, restores every segment from its own masked form, writes, and returns the written chapter's text. */
    private String identityRestoreAndWrite(Path source, String outputName) {
        final DocumentService service = DocumentServices.newService();
        final Document opened = open(service, source);
        final List<Segment> segments = opened.units().get(0).segments();
        final List<String> targets = segments.stream()
                .map(segment -> identityRestore(service, segment))
                .toList();

        final Result<Path> written =
                service.write(SegmentTargets.withTargets(opened, targets), tempDir.resolve(outputName), "uk");

        assertThat(written.isOk())
                .withFailMessage("write failed: %s", written.error())
                .isTrue();
        return ZipText.firstMemberEndingWith(Objects.requireNonNull(written.data(), "data"), ".xhtml");
    }

    private static String identityRestore(DocumentService service, Segment segment) {
        final Result<String> restored = service.unmask(BookFormat.EPUB, segment, segment.masked());
        assertThat(restored.isOk())
                .withFailMessage("identity restore failed: %s", restored.error())
                .isTrue();
        return Objects.requireNonNull(restored.data(), "data");
    }

    /** Writes the book back with no segment edits, repeatedly, feeding each output into the next open. */
    private String writeRepeatedlyWithNoEdits(Path source) {
        Path current = source;
        String chapter = "";
        for (int round = 0; round < REPEATED_WRITES; round++) {
            final DocumentService service = DocumentServices.newService();
            final Result<Path> written =
                    service.write(open(service, current), tempDir.resolve("round-" + round + ".epub"), "en");
            assertThat(written.isOk())
                    .withFailMessage("write round %d failed: %s", round, written.error())
                    .isTrue();
            current = Objects.requireNonNull(written.data(), "data");
            chapter = ZipText.firstMemberEndingWith(current, ".xhtml");
        }
        return chapter;
    }

    private static Document open(DocumentService service, Path file) {
        final Result<Document> opened = service.open(file);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "data");
    }
}
