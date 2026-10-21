package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T2;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** A batch reply's parsed items are announced on the batch call, so each row shows its own result. */
class BatchItemsJobTest {

    private static final List<String> IDS = List.of("Book.md:0", "Book.md:1", "Book.md:2", "Book.md:3");

    @TempDir
    private Path tempDir;

    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private void run(final ScriptedChatModel model) {
        final TestProject project = project(ChunkRunFixtures.fourParagraphs(tempDir), brief("en", "uk"));
        final TranslationJobImpl job = batchedJob(project, model);
        job.subscribe(events::add);
        report(job.run());
    }

    private List<CallSnapshot> drafts() {
        return events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .filter(snapshot -> snapshot.kind() == CallKind.DRAFT)
                .toList();
    }

    /** The last snapshot of the draft call numbered {@code callId} that carries items, or one with none. */
    private CallSnapshot lastOf(final long callId) {
        return drafts().stream()
                .filter(snapshot -> snapshot.callId() == callId)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    // IF the reply leaves out id 2, THEN the batch call shows four items in order and item 2 is MISSING for its
    // segment.
    @Test
    void run_replyMissingOneId_announcesFourItemsOneMissing() {
        run(replies(T1).answerTo(BATCH, batchReply(items(entry("1", T0), entry("3", T2), entry("4", T3)))));

        assertThat(lastOf(1L).items())
                .extracting(CallItem::id, CallItem::segmentId, CallItem::target, CallItem::status)
                .containsExactly(
                        tuple("1", IDS.get(0), T0, CallItemStatus.OK),
                        tuple("2", IDS.get(1), null, CallItemStatus.MISSING),
                        tuple("3", IDS.get(2), T2, CallItemStatus.OK),
                        tuple("4", IDS.get(3), T3, CallItemStatus.OK));
    }

    // The single-segment redraft is a different call and shows no batch items.
    @Test
    void run_replyMissingOneId_theAloneDraftCallShowsNoItems() {
        run(replies(T1).answerTo(BATCH, batchReply(items(entry("1", T0), entry("3", T2), entry("4", T3)))));

        assertThat(lastOf(2L).items()).isEmpty();
    }

    // Each call shows its own items: the first keeps four with two missing, the re-ask shows its two.
    @Test
    void run_replyMissingTwoIds_eachCallShowsItsOwnItems() {
        run(new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("4", T3))))
                .answerTo(BATCH, batchReply(items(entry("2", T1), entry("3", T2)))));

        assertThat(lastOf(1L).items())
                .extracting(CallItem::status)
                .containsExactly(CallItemStatus.OK, CallItemStatus.MISSING, CallItemStatus.MISSING, CallItemStatus.OK);
        assertThat(lastOf(2L).items())
                .extracting(CallItem::segmentId, CallItem::status)
                .containsExactly(tuple(IDS.get(1), CallItemStatus.OK), tuple(IDS.get(2), CallItemStatus.OK));
    }

    // An id the batch never held is shown as EXTRA with no segment.
    @Test
    void run_replyWithAnExtraId_announcesItAsExtraWithNoSegment() {
        run(new ScriptedChatModel()
                .answerTo(
                        BATCH,
                        batchReply(items(
                                entry("1", T0),
                                entry("2", T1),
                                entry("3", T2),
                                entry("4", T3),
                                entry("9", "Extra.")))));

        assertThat(lastOf(1L).items())
                .extracting(CallItem::id, CallItem::segmentId, CallItem::status)
                .contains(tuple("9", null, CallItemStatus.EXTRA))
                .hasSize(5);
    }

    // IF the reply is blank, THEN every expected id is shown as MISSING.
    @Test
    void run_blankReply_announcesEveryItemMissing() {
        run(replies(T0, T1, T2, T3).answerTo(BATCH, batchReply("")));

        assertThat(lastOf(1L).items()).hasSize(4).extracting(CallItem::status).containsOnly(CallItemStatus.MISSING);
    }
}
