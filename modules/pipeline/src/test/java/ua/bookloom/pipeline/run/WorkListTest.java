package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
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
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Project;
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
                injector.getInstance(GlossaryRepository.class),
                injector.getInstance(TmRepository.class),
                injector.getInstance(SummaryRepository.class));
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

    // The auxiliary unit is the run's last work, and it is no section: the section count stays the body's.
    @Test
    void read_lastUnitIsAuxiliary_listsItAfterTheBodyWithoutCountingItAsASection() {
        final WorkList work = read(documentWithAuxiliary(), Map.of());

        assertThat(work.sectionCount()).isEqualTo(2);
        assertThat(work.segmentCount()).isEqualTo(5);
        assertThat(pendingIds(work)).containsExactly("one:0", "one:1", "one:2", "one:3", "two:0", "aux:title");
        assertThat(work.remaining().getLast().section()).isEqualTo(2);
    }

    // A kept record is neither work nor pending, whatever the switch was when the record was stored.
    @Test
    void read_metadataSwitchedOff_leavesTheTitleOutOfWorkAndPendingCount() {
        saveProject(new AlsoTranslate(true, true, false, false));
        final WorkList work = read(documentWithAuxiliary(), Map.of());

        assertThat(pendingIds(work)).doesNotContain("aux:title");
        assertThat(work.currentTranslationProgress().pending()).isEqualTo(5);
    }

    // A person may flip a switch while the run is paused; the next section must follow the brief as it then stands.
    @Test
    void remaining_switchChangedBetweenReads_followsTheBrief() {
        final WorkList work = read(documentWithAuxiliary(), Map.of());
        assertThat(pendingIds(work)).contains("aux:title");

        saveProject(new AlsoTranslate(true, true, false, false));
        work.refresh();
        assertThat(pendingIds(work)).doesNotContain("aux:title");
        assertThat(work.currentTranslationProgress().pending()).isEqualTo(5);

        saveProject(AlsoTranslate.defaults());
        work.refresh();
        assertThat(pendingIds(work)).endsWith("aux:title");
        assertThat(work.currentTranslationProgress().pending()).isEqualTo(6);
    }

    // The progress bar must reach 100 % with the auxiliary unit running as the last section, not one past it.
    @Test
    void apply_auxiliaryItem_reportsTheLastSection() {
        final WorkList work = read(documentWithAuxiliary(), Map.of());
        final WorkItem title = work.remaining().getLast();

        final JobProgress progress = work.apply(title, SegmentStatus.ACCEPTED, SegmentPath.DRAFT);

        assertThat(progress)
                .extracting(JobProgress::section, JobProgress::sections)
                .containsExactly(2, 2);
    }

    // Pausing after a section names body sections only: nothing follows the last body segment's section end but the
    // title.
    @Test
    void endsSection_auxiliaryItem_isNeverASectionEnd() {
        final WorkList work = read(documentWithAuxiliary(), Map.of());

        assertThat(work.endsSection(work.remaining().getLast())).isFalse();
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

    // The unit-end name scan reads everything decided so far, an earlier run's decisions and a flagged one included.
    @Test
    void decidedSegments_storedDecisions_listsEveryDecidedSegmentInDocumentOrder() {
        final WorkList work = read(
                document(),
                Map.of(
                        "one:2", SegmentStatus.FLAGGED,
                        "one:0", SegmentStatus.ACCEPTED,
                        "one:1", SegmentStatus.REVISED));

        assertThat(work.decidedSegments()).extracting(Segment::id).containsExactly("one:0", "one:1", "one:2");
    }

    // The summary's unit end also comes at the auxiliary unit's end, which is never a section end.
    @Test
    void endsUnit_auxiliaryItem_endsItsUnit() {
        final WorkList work = read(
                documentWithAuxiliary(),
                Map.of(
                        "one:0", SegmentStatus.ACCEPTED,
                        "one:1", SegmentStatus.ACCEPTED,
                        "one:2", SegmentStatus.ACCEPTED,
                        "one:3", SegmentStatus.ACCEPTED,
                        "two:0", SegmentStatus.ACCEPTED));
        final WorkItem title = work.remaining().getFirst();

        work.apply(title, SegmentStatus.ACCEPTED, SegmentPath.DRAFT);

        assertThat(work.endsUnit(title)).isTrue();
        assertThat(work.isBody(title)).isFalse();
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
                .containsExactly(1, 2, 2, 1);
        assertThat(progress.pending()).isEqualTo(2);
    }

    // The counts must move with each decision, or the events would show a stale total until the run ends.
    @Test
    void apply_acceptedDecision_movesOneSegmentFromPendingToAccepted() {
        final WorkList work = read(document(), Map.of());

        final JobProgress progress = work.apply(work.remaining().getFirst(), SegmentStatus.ACCEPTED, SegmentPath.DRAFT);

        assertThat(progress)
                .extracting(JobProgress::accepted, JobProgress::flagged, JobProgress::pending)
                .containsExactly(1, 0, 4);
    }

    // A repaired acceptance of an earlier run is still a repaired one: the tiles count the whole project.
    @Test
    void refresh_storedRepairedAcceptance_countsItApartFromTheAutomaticOne() {
        final WorkList work =
                read(document(), Map.of("one:0", SegmentStatus.ACCEPTED, "one:1", SegmentStatus.ACCEPTED));
        stores.segments().update(PROJECT, "one:0", record -> record.withPath(SegmentPath.REPAIRED));

        work.refresh();

        assertThat(work.currentTranslationProgress())
                .extracting(JobProgress::accepted, JobProgress::autoAccepted, JobProgress::repairedAccepted)
                .containsExactly(2, 1, 1);
    }

    // A flagged segment is never counted as repaired-and-accepted, whatever rounds it used.
    @Test
    void apply_repairedThenFlaggedRepaired_countsOnlyTheAcceptanceAsRepaired() {
        final WorkList work = read(document(), Map.of());
        work.apply(work.remaining().getFirst(), SegmentStatus.ACCEPTED, SegmentPath.REPAIRED);

        final JobProgress progress =
                work.apply(work.remaining().getFirst(), SegmentStatus.FLAGGED, SegmentPath.REPAIRED);

        assertThat(progress)
                .extracting(JobProgress::autoAccepted, JobProgress::repairedAccepted, JobProgress::flagged)
                .containsExactly(0, 1, 1);
    }

    // The screen shows "chapter k of n" and "chunk k of n": both must be 1-based and name the item's own unit.
    @Test
    void position_itemOfTheSecondUnitInItsThirdChunk_isSectionTwoOfTwoAndChunkThreeOfFive() {
        final WorkList work = read(document(), Map.of());
        work.enterChunk(3, 5);

        assertThat(work.position(work.remaining().getLast())).isEqualTo(new ChunkPosition(2, 2, 3, 5));
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
        if (Objects.requireNonNull(stores.projects().find(PROJECT).data(), "found")
                .isEmpty()) {
            saveProject(AlsoTranslate.defaults());
        }
        return Objects.requireNonNull(WorkList.read(stores, PROJECT, document).data(), "work list");
    }

    private void saveProject(final AlsoTranslate alsoTranslate) {
        final BookBrief defaults = BookBrief.defaults("en");
        final BookBrief brief = new BookBrief(
                "en",
                "uk",
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                alsoTranslate,
                QualityDial.FAST);
        stores.projects().save(new Project(PROJECT, Path.of("Book.md"), BookFormat.MARKDOWN, "hash", brief));
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
