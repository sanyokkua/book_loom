package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.targetReply;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.SegmentDetail;
import ua.bookloom.api.pipeline.SegmentDrafted;
import ua.bookloom.api.pipeline.SegmentStarted;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The Translating screen's live panel, tiles and log are built from these events alone, so each segment's start,
 * draft and decision must carry its locator, its text as a person reads it and where the run stands.
 */
class LiveTextEventsTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_doorSentenceInChapterSeven_announcesItsStartDraftAndDecision() {
        final List<JobEvent> events = runDoor();

        assertThat(events)
                .filteredOn(SegmentStarted.class::isInstance)
                .map(SegmentStarted.class::cast)
                .filteredOn(started -> LiveTextFixtures.DOOR_ID.equals(started.segmentId()))
                .singleElement()
                .satisfies(started -> {
                    assertThat(started.locator()).isEqualTo("ch7 · p42");
                    assertThat(started.displaySource()).isEqualTo("He opened the old door.");
                    assertThat(started.position()).isEqualTo(new ChunkPosition(7, 11, 1, 1));
                });
        assertThat(events)
                .filteredOn(SegmentDrafted.class::isInstance)
                .map(SegmentDrafted.class::cast)
                .filteredOn(drafted -> LiveTextFixtures.DOOR_ID.equals(drafted.segmentId()))
                .singleElement()
                .extracting(SegmentDrafted::displayTarget)
                .isEqualTo("Він відчинив старі двері.");
        final SegmentDecided door = decided(events, LiveTextFixtures.DOOR_ID);
        assertThat(door.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(door.detail())
                .isNotNull()
                .extracting(SegmentDetail::judgeScore, SegmentDetail::path)
                .containsExactly(null, SegmentPath.DRAFT);
    }

    // The reviewer reads the whole chunk, so its call belongs to no one segment but names every segment it reviews.
    @Test
    void run_reviewedChunkOfFortyAndFortyOne_announcesOneReviewerCallNamingBothSegments() {
        final List<JobEvent> events = runDoor();

        assertThat(events)
                .filteredOn(ModelCallStarted.class::isInstance)
                .map(ModelCallStarted.class::cast)
                .filteredOn(started -> started.kind() == CallKind.REVIEW)
                .extracting(ModelCallStarted::segmentId, ModelCallStarted::segmentIds)
                .containsExactly(tuple(null, List.of("ch07.xhtml:40", "ch07.xhtml:41")));
    }

    @Test
    void run_oneDirectedFixAndTwoFirstDrafts_lastSnapshotCountsTwoAutoAndOneRepaired() {
        final Path book = TestBooks.markdown(
                tempDir.resolve("Book.md"),
                String.join("\n\n", ChunkRunFixtures.S1, ChunkRunFixtures.S2, ChunkRunFixtures.S3));
        final TranslationJobImpl translation = job(
                project(book, TranslationJobTestSupport.brief("en", "uk")),
                replies(ChunkRunFixtures.ECHO1, ChunkRunFixtures.T1, ChunkRunFixtures.T2, ChunkRunFixtures.T3));
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        report(translation.run());

        assertThat(lastDecision(events).progress())
                .extracting(JobProgress::accepted, JobProgress::autoAccepted, JobProgress::repairedAccepted)
                .containsExactly(3, 2, 1);
    }

    @Test
    void run_decisionInChunkTwentyOneOfThirtyThree_carriesSectionSevenOfElevenAndChunkTwentyOneOfThirtyThree() {
        final List<String> paragraphs =
                IntStream.range(0, 264).mapToObj(index -> "Line " + index + ".").toList();
        final TestProject project = LiveTextFixtures.seventhChapterPending(tempDir, paragraphs);
        final TranslationJobImpl translation = job(project, LiveTextFixtures.acceptingModel());
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        report(translation.run());

        assertThat(decided(events, "ch07.xhtml:160").progress())
                .extracting(JobProgress::section, JobProgress::sections, JobProgress::chunk, JobProgress::chunks)
                .containsExactly(7, 11, 21, 33);
    }

    // A repair is its own call, and both belong to the segment before its decision.
    @Test
    void run_structuralRepair_announcesDraftThenStructuralRepairBeforeTheDecision() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"segments\":", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(targetReply("ONE."), FinishReason.STOP)));
        final TranslationJobImpl translation = job(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        translation.run();

        assertThat(events.stream().map(LiveTextEventsTest::segmentLabel).filter(label -> !label.isEmpty()))
                .containsExactly(
                        "started Book.md:0",
                        "call DRAFT Book.md:0",
                        "finished DRAFT Book.md:0",
                        "call STRUCTURAL_REPAIR Book.md:0",
                        "finished STRUCTURAL_REPAIR Book.md:0",
                        "drafted Book.md:0",
                        "decided Book.md:0");
    }

    // A "waiting for the model" clock must never start over a request the provider never received.
    @Test
    void run_pauseRequestedAsTheSegmentStarts_sendsNoRequestAndAnnouncesNoCall() {
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(events::add);
        translation.subscribe(event -> pauseOnStart(translation, pauses, event));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        awaitPaused(pauses);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(model.requests()).isEmpty();
        assertThat(events).filteredOn(SegmentStarted.class::isInstance).hasSize(1);
        assertThat(events).noneMatch(ModelCallStarted.class::isInstance);
    }

    private List<JobEvent> runDoor() {
        final TranslationJobImpl translation = job(LiveTextFixtures.doorProject(tempDir), LiveTextFixtures.doorModel());
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);
        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);
        return events;
    }

    private static SegmentDecided decided(final List<JobEvent> events, final String segmentId) {
        return events.stream()
                .filter(SegmentDecided.class::isInstance)
                .map(SegmentDecided.class::cast)
                .filter(decided -> segmentId.equals(decided.segmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no decision for " + segmentId));
    }

    private static SegmentDecided lastDecision(final List<JobEvent> events) {
        return events.stream()
                .filter(SegmentDecided.class::isInstance)
                .map(SegmentDecided.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no decision"));
    }

    private static String segmentLabel(final JobEvent event) {
        return switch (event) {
            case SegmentStarted started -> "started " + started.segmentId();
            case ModelCallStarted started -> "call " + started.kind() + " " + started.segmentId();
            case ModelCallFinished finished -> "finished " + finished.kind() + " " + finished.segmentId();
            case SegmentDrafted drafted -> "drafted " + drafted.segmentId();
            case SegmentDecided decided -> "decided " + decided.segmentId();
            default -> "";
        };
    }

    private static void pauseOnStart(
            final TranslationJobImpl translation, final LinkedBlockingQueue<Paused> pauses, final JobEvent event) {
        if (event instanceof SegmentStarted) {
            translation.pause();
        }
        TranslationJobTestSupport.capturePaused(pauses, event);
    }
}
