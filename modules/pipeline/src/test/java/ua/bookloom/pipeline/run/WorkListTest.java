package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.project.OpenProjects;

/** What the run still has to decide, read from the stored records, and the counts that move as it decides. */
class WorkListTest {

    private static final String PROJECT = "p1";

    private RunStores stores;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule(), new PersistenceModule());
        stores = new RunStores(
                injector.getInstance(ProjectRepository.class),
                injector.getInstance(SegmentRepository.class),
                injector.getInstance(CheckpointPort.class),
                injector.getInstance(OpenProjects.class),
                injector.getInstance(RunRepository.class),
                injector.getInstance(GlossaryRepository.class));
    }

    // A draft's preceding targets come from its whole unit, so the segments decided before this run must be listed too.
    @Test
    void unitSegments_firstPendingAfterStoredDecisions_listsItsWholeUnit() {
        final WorkList work = read(
                document(),
                Map.of(
                        "one:0", SegmentStatus.ACCEPTED,
                        "one:1", SegmentStatus.REVISED,
                        "one:2", SegmentStatus.FLAGGED));

        assertThat(work.unitSegments(work.remaining().getFirst()))
                .extracting(Segment::id)
                .containsExactly("one:0", "one:1", "one:2", "one:3");
    }

    // The auxiliary unit is never the run's work until the brief's switches reach it.
    @Test
    void read_lastUnitIsAuxiliary_leavesItOutOfPendingWorkAndSectionCount() {
        final WorkList work = read(documentWithAuxiliary(), Map.of());

        assertThat(work.sectionCount()).isEqualTo(2);
        assertThat(work.segmentCount()).isEqualTo(5);
        assertThat(pendingIds(work))
                .containsExactly("one:0", "one:1", "one:2", "one:3", "two:0")
                .doesNotContain("aux:title");
    }

    // Starting at the first segment again would repeat what an earlier run decided; a flagged one is never work.
    @Test
    void read_storedDecisions_startAtTheFirstPendingSegment() {
        final WorkList work = read(
                document(),
                Map.of(
                        "one:0", SegmentStatus.ACCEPTED,
                        "one:1", SegmentStatus.REVISED,
                        "one:2", SegmentStatus.FLAGGED));

        assertThat(work.remaining().getFirst().segment().id()).isEqualTo("one:3");
        assertThat(pendingIds(work)).containsExactly("one:3", "two:0");
    }

    // Counting only this run's decisions would show 0 accepted at the start of a run that resumes a stopped one.
    @Test
    void currentTranslationProgress_storedDecisions_startFromTheProjectsCounts() {
        final WorkList work = read(
                document(),
                Map.of(
                        "one:0", SegmentStatus.ACCEPTED,
                        "one:1", SegmentStatus.REVISED,
                        "one:2", SegmentStatus.FLAGGED));

        final JobProgress progress = work.currentTranslationProgress();

        assertThat(progress)
                .extracting(JobProgress::section, JobProgress::sections, JobProgress::accepted, JobProgress::flagged)
                .containsExactly(0, 2, 2, 1);
        assertThat(progress.pending()).isEqualTo(2);
    }

    // The counts must move with each decision, or the events would show a stale total until the run ends.
    @Test
    void apply_acceptedDecision_movesOneSegmentFromPendingToAccepted() {
        final WorkList work = read(document(), Map.of());

        final JobProgress progress = work.apply(work.remaining().getFirst(), SegmentStatus.ACCEPTED);

        assertThat(progress)
                .extracting(JobProgress::accepted, JobProgress::flagged, JobProgress::pending)
                .containsExactly(1, 0, 4);
    }

    private static List<String> pendingIds(final WorkList work) {
        return work.remaining().stream().map(item -> item.segment().id()).toList();
    }

    private WorkList read(final Document document, final Map<String, SegmentStatus> stored) {
        final List<SegmentRecord> records = document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .map(segment -> record(segment, stored.getOrDefault(segment.id(), SegmentStatus.PENDING)))
                .toList();
        stores.segments().saveAll(PROJECT, records);
        return Objects.requireNonNull(WorkList.read(stores, PROJECT, document).data(), "work list");
    }

    private static SegmentRecord record(final Segment segment, final SegmentStatus status) {
        return new SegmentRecord(
                PROJECT,
                segment.id(),
                segment.unit(),
                segment.order(),
                segment.kind(),
                status,
                null,
                null,
                null,
                null,
                0.0,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    private static Document documentWithAuxiliary() {
        final Document body = document();
        final Segment title = new Segment(
                "aux:title",
                Unit.AUXILIARY_ID,
                0,
                SegmentKind.METADATA_TITLE,
                "Book",
                "Book",
                Map.of(),
                "hash-title",
                null,
                null,
                new ByteSpanAnchor(0, 4),
                null,
                SegmentStatus.PENDING,
                0.0);
        final Unit auxiliary = new Unit(
                Unit.AUXILIARY_ID,
                2,
                "one.md",
                "application/x-bookloom-auxiliary",
                new SkeletonHandle("aux"),
                List.of(title));
        final List<Unit> units = new ArrayList<>(body.units());
        units.add(auxiliary);
        return body.withUnits(units);
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
