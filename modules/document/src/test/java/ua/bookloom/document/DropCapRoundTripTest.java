package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.fixture.BodyContentEpub;

/**
 * The two paragraphs of a real book's chapter opening (Bartimaeus, {@code part0009.html}) whose export was refused
 * with "placeholder count written=2 reopened=0": a drop cap {@code <span>“A</span>bove} and an italic run ending
 * in a closing quote. A reply that puts the drop cap's pair around the whole paragraph passed the old gate, was
 * written as a span around every word, and re-opened as a segment <em>of that span</em> — the reader makes the
 * outermost element owning text its block — so the written book had no inline markup left in that paragraph.
 */
class DropCapRoundTripTest {

    private static final String BODY = "<p class=\"tx\"><span class=\"calibre8\">“A</span>bove all,” said his master,"
            + " “there is one fact.”</p>\n"
            + "<p class=\"tx1\">“Remember <i class=\"calibre3\">this,”</i> he said in a soft voice.</p>\n";

    private static final String WRAPPED_WHOLE = "⟦g0⟧«Понад усе», — сказав його господар, — «є один факт».⟦g1⟧";

    @TempDir
    private Path dir;

    // The cause, shown at the writer: a span written around every word re-opens with no placeholder at all.
    @Test
    void write_pairAroundTheWholeParagraph_reopensWithNoPlaceholders() {
        final DocumentService service = DocumentServices.newService();
        final Document book = open(service, BodyContentEpub.withBody(dir.resolve("book.epub"), BODY));

        final Document reopened = writeAndReopen(
                service, book, "<span class=\"calibre8\">«Понад усе», — сказав його господар, — «є один факт».</span>");

        assertThat(firstBody(reopened).placeholders()).isEmpty();
    }

    // The fix: the gate refuses that reply, so it is never written.
    @Test
    void unmask_dropCapPairAroundTheWholeTranslation_returnsValidationError() {
        final DocumentService service = DocumentServices.newService();
        final Document book = open(service, BodyContentEpub.withBody(dir.resolve("book.epub"), BODY));

        final Result<String> result = service.unmask(book.format(), firstBody(book), WRAPPED_WHOLE);

        assertThat(result.error()).isNotNull().extracting(error -> error.code()).isEqualTo(ErrorCode.validation);
        assertThat(result.data()).isNull();
    }

    // A drop cap kept on the first letter writes and re-opens with both of its fragments, in order.
    @Test
    void unmaskWriteReopen_dropCapOnTheFirstLetter_keepsBothFragments() {
        final DocumentService service = DocumentServices.newService();
        final Document book = open(service, BodyContentEpub.withBody(dir.resolve("book.epub"), BODY));
        final Segment segment = firstBody(book);
        final String restored = Objects.requireNonNull(
                service.unmask(book.format(), segment, "⟦g0⟧«П⟦g1⟧онад усе», — сказав його господар, — «є один факт».")
                        .data());

        final Document reopened = writeAndReopen(service, book, restored);

        assertThat(firstBody(reopened).masked())
                .isEqualTo("⟦g0⟧«П⟦g1⟧онад усе», — сказав його господар, — «є один факт».");
        assertThat(List.copyOf(firstBody(reopened).placeholders().values()))
                .containsExactly("<span class=\"calibre8\">", "</span>");
    }

    // The italic paragraph's pair moved to the end of a clause still re-opens with its fragments.
    @Test
    void unmaskWriteReopen_italicEndingInAQuote_keepsBothFragments() {
        final DocumentService service = DocumentServices.newService();
        final Document book = open(service, BodyContentEpub.withBody(dir.resolve("book.epub"), BODY));
        final Segment italic = bodySegments(book).get(1);
        final String restored = Objects.requireNonNull(
                service.unmask(book.format(), italic, "«Пам'ятай ⟦g0⟧це»,⟦g1⟧ — тихо сказав він.")
                        .data());

        final Document reopened = writeTarget(service, book, italic.id(), restored);

        assertThat(bodySegments(reopened).get(1).masked()).isEqualTo("«Пам'ятай ⟦g0⟧це»,⟦g1⟧ — тихо сказав він.");
    }

    private Document writeAndReopen(DocumentService service, Document book, String target) {
        return writeTarget(service, book, firstBody(book).id(), target);
    }

    private Document writeTarget(DocumentService service, Document book, String segmentId, String target) {
        final List<Unit> units = book.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> segment.id().equals(segmentId)
                                ? segment.withDecision(SegmentStatus.ACCEPTED, target)
                                : segment)
                        .toList()))
                .toList();
        final Path written =
                Objects.requireNonNull(service.write(book.withUnits(units), dir.resolve("out.epub"), "en", "uk")
                        .data());
        return open(service, written);
    }

    private static Document open(DocumentService service, Path path) {
        return Objects.requireNonNull(service.open(path).data(), "opened");
    }

    private static Segment firstBody(Document document) {
        return bodySegments(document).get(0);
    }

    private static List<Segment> bodySegments(Document document) {
        return document.units().stream()
                .filter(unit -> !unit.isAuxiliary())
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }
}
