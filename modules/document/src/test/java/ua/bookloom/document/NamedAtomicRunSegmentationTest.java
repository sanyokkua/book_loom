package ua.bookloom.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.fixture.BodyContentEpub;

/**
 * Whether a line-break-delimited run whose only content is a named-atomic span becomes a segment.
 *
 * <p>The walker skipped a run whose <em>character data</em> was blank, which is not the same question: a code
 * span's interior is text a reader sees but the model never does, because masking replaces the whole span with one
 * token. Measured before the emptiness test was narrowed to translatable text, {@code <p>Hi<br/><code>x</code></p>}
 * emitted a second segment whose entire masked form was {@code ⟦g0⟧} — a model call that can only hand the token
 * back, and one more chance for the placeholder gate to reject a chunk over nothing.
 *
 * <p>The guard in the other direction is the point of the second test: narrowing this too far would drop runs that
 * do own prose, which is the failure mode the block-level code-only exclusion (D11) already had to be scoped
 * against. A run that holds a code span <em>and</em> words around it is real translatable content and must still
 * be its own segment.
 */
class NamedAtomicRunSegmentationTest {

    @TempDir
    private Path tempDir;

    // WHEN a node inside a segment's content is a protected span, the system SHALL emit
    // exactly one token for it and SHALL NOT expose any part of its interior in the masked form — so a run holding
    // nothing but such a span has nothing left for the model and is not a segment at all.
    @Test
    void open_runWhoseOnlyContentIsANamedAtomicSpan_producesNoSegmentForIt() {
        final List<Segment> segments = segmentsOf("<p>Hi<br/><code>x</code></p>\n", "atomic-only.epub");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).masked()).isEqualTo("Hi");
    }

    // The system SHALL leave every piece of character data in a segment that is not itself a
    // protected span present and translatable in the masked form.
    // The opposite direction: a run carrying a code span AND prose around it still owns words the model must see,
    // so it is still a segment. Without this pair, narrowing the emptiness test too far would pass the case above
    // while silently dropping real prose.
    @Test
    void open_runHoldingAnAtomicSpanAmongProse_stillProducesItsOwnSegment() {
        final List<Segment> segments = segmentsOf("<p>Hi<br/>Call <code>x</code> first.</p>\n", "atomic-prose.epub");

        assertThat(segments).hasSize(2);
        assertThat(segments.get(1).masked()).isEqualTo("Call ⟦g0⟧ first.");
    }

    private List<Segment> segmentsOf(String bodyContent, String fileName) {
        final Path source = BodyContentEpub.withBody(tempDir.resolve(fileName), bodyContent);
        final Result<Document> opened = DocumentServices.newService().open(source);
        assertThat(opened.isOk())
                .withFailMessage("fixture did not open: %s", opened.error())
                .isTrue();
        return Objects.requireNonNull(opened.data(), "document").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .toList();
    }
}
