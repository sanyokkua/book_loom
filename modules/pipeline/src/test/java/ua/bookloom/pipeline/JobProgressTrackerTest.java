package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;

/** Verifies the pipeline owns bounded, section-local preceding-target context. */
class JobProgressTrackerTest {

    // Accepted targets retain only the newest three within their unit and never cross to the next unit.
    @Test
    void draftContextFor_acceptedTargets_areBoundedAndResetAtSectionBoundary() {
        final JobProgressTracker tracker = new JobProgressTracker(document());

        acceptNext(tracker, "Target 1.");
        assertThat(tracker.draftContextFor(tracker.next()).precedingTargets()).containsExactly("Target 1.");
        acceptNext(tracker, "Target 2.");
        acceptNext(tracker, "Target 3.");
        assertThat(tracker.draftContextFor(tracker.next()).precedingTargets())
                .containsExactly("Target 1.", "Target 2.", "Target 3.");
        acceptNext(tracker, "Target 4.");

        assertThat(tracker.draftContextFor(tracker.next()).precedingTargets()).isEmpty();
    }

    private static void acceptNext(final JobProgressTracker tracker, final String target) {
        final SegmentWork work = tracker.next();
        tracker.apply(work, new Decision(work.segment().withDecision(SegmentStatus.ACCEPTED, target), null));
    }

    private static Document document() {
        return new Document(
                "Book",
                BookFormat.MARKDOWN,
                "en",
                null,
                "UTF-8",
                false,
                "hash",
                Map.of(),
                List.of(unit("one", 0, 4), unit("two", 1, 1)));
    }

    private static Unit unit(final String id, final int order, final int segmentCount) {
        final List<Segment> segments = java.util.stream.IntStream.range(0, segmentCount)
                .mapToObj(index -> segment(id, index))
                .toList();
        return new Unit(id, order, id + ".md", "text/markdown", new SkeletonHandle(id), segments);
    }

    private static Segment segment(final String unit, final int order) {
        final String text = "Source " + order + ".";
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                SegmentKind.PARAGRAPH,
                text,
                text,
                Map.of(),
                "hash-" + unit + "-" + order,
                null,
                null,
                new ByteSpanAnchor(0, text.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }
}
