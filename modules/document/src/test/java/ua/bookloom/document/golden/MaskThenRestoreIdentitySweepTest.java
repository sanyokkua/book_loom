package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentService;
import ua.bookloom.document.DocumentServices;
import ua.bookloom.document.fixture.FixtureCatalog;

/**
 * The catalogue-wide mask-then-restore identity check: every fixture opened through the real port, and every one
 * of its segments given its own masked form back as its target — the requirement <em>Restore a masked segment to
 * its source content when nothing is translated</em>.
 *
 * <p><strong>Why this is a separate sweep from {@link FixtureSweepTest}.</strong> The zero-edit write path never
 * calls {@link ua.bookloom.document.DocumentService#unmask} — reassembly splices target text straight into the
 * skeleton (see that method's own Javadoc) — so a document that reassembles canonical-equal proves nothing about
 * restore. This sweep exercises the one path {@link FixtureSweepTest} structurally cannot reach: a masker that
 * silently drops something from its own placeholder map would still let the zero-edit round trip pass, because
 * that round trip never asks the map for anything back.
 */
class MaskThenRestoreIdentitySweepTest {

    @TempDir
    private Path tempDir;

    private static List<FixtureCatalog.Case> catalogue() {
        return FixtureCatalog.all();
    }

    // WHEN a segment's masked form is supplied back as its own target, the system
    // SHALL produce restored content equal to its source content for TXT and Markdown and canonical-equal for
    // EPUB and FB2.
    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogue")
    void unmask_everyCatalogueSegmentGivenItsOwnMaskedFormAsTarget_restoresItsSourceContent(
            FixtureCatalog.Case fixture) {
        final Path source = fixture.builder().apply(tempDir.resolve(fixture.fileName()));
        // One service per fixture, matching FixtureSweepTest's own arrangement: a reader and its writer share a
        // registry, so a document opened by one service instance is unknown to another.
        final DocumentService service = DocumentServices.newService();

        final Result<Document> opened = service.open(source);
        assertThat(opened.isOk())
                .withFailMessage("%s did not open: %s", fixture.name(), opened.error())
                .isTrue();
        final Document document = Objects.requireNonNull(opened.data());

        assertThat(segmentsOf(document))
                .as("mask-then-restore identity across every segment of %s", fixture.name())
                .allSatisfy(segment ->
                        assertSegmentRestoresToItsSource(service, document.format(), segment, fixture.name()));
    }

    private static void assertSegmentRestoresToItsSource(
            DocumentService service, BookFormat format, Segment segment, String fixtureName) {
        final Result<String> restored = service.unmask(format, segment, segment.masked());
        assertThat(restored.isOk())
                .withFailMessage(
                        "%s segment %s failed to restore its own masked form as its target: %s",
                        fixtureName, segment.id(), restored.error())
                .isTrue();
        assertRestoredMatchesSource(format, segment, Objects.requireNonNull(restored.data()), fixtureName);
    }

    /**
     * Dispatches to the comparator each format's requirement actually names — never string equality for a tree
     * format, since {@code sourceInner} and a restored fragment are composed by two different routes through the
     * same underlying markup and can differ textually while denoting the same document
     * ({@link MaskRestoreCanonicalAssert}).
     */
    private static void assertRestoredMatchesSource(
            BookFormat format, Segment segment, String restored, String fixtureName) {
        final String context = fixtureName + " segment " + segment.id();
        switch (format) {
            case TXT, MARKDOWN -> assertThat(restored).as(context).isEqualTo(segment.sourceInner());
            case EPUB ->
                MaskRestoreCanonicalAssert.assertEpubFragmentCanonicalEqual(segment.sourceInner(), restored, context);
            case FB2 ->
                MaskRestoreCanonicalAssert.assertFb2FragmentCanonicalEqual(segment.sourceInner(), restored, context);
        }
    }

    private static List<Segment> segmentsOf(Document document) {
        return document.units().stream().flatMap(u -> u.segments().stream()).toList();
    }
}
