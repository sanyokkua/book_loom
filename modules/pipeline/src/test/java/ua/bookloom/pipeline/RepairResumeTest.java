package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_ECHO;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_TARGET;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REPAIR;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A repair cut off by a pause or a pausing error is made again on resume from the draft kept in memory, with no new
 * draft and no second fix of the reviewer's refused edit, and counts as the segment's one repair ({@code specs/resume/spec.md} "Make a
 * cut-off repair again from the draft kept in memory").
 */
class RepairResumeTest {

    // The draft writes a full stop for the source's question mark (a repair-level finding the reviewer leaves), and
    // the reviewer's edit swaps a word for a different one, which earns its own fix before the one repair.
    private static final String SOURCE = "Did the old man walk to the harbour?";
    private static final String DRAFTED = "Старий чоловік пішов до гавані.";
    private static final String FIXED = "Старий чоловік повільно пішов до гавані.";
    private static final String REPAIRED = "Старий чоловік повільно пішов до гавані?";
    private static final String REFUSED_EDIT = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
            + "{\"criterion\":\"meaning\",\"quote\":\"чоловік пішов\",\"replacement\":\"чоловік побіг\"}]}]}";

    private static final AppError UNREACHABLE =
            AppError.of(ErrorCode.unreachable, "Provider unreachable", "The provider did not answer.");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void resume_pauseDuringRepair_resendsRepairNoNewDraft() {
        final ScriptedChatModel model = replies(DOOR_ECHO, DOOR_TARGET).blockNthRequest(2);
        final TestProject project = doorAtBalanced();
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(2);
        translation.pause();
        final Paused pause = awaitPaused(pauses);
        final List<String> atPause = formats(model);
        translation.resume();
        report(await(run));

        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(atPause).containsExactly(DRAFT, REPAIR);
        assertRepairSentAgain(project, model);
    }

    @Test
    void resume_unreachableDuringRepair_pausesAndResendsTheRepair() {
        final ScriptedChatModel model =
                replies(DOOR_ECHO).answer(Result.err(UNREACHABLE)).answer(target(DOOR_TARGET));
        final TestProject project = doorAtBalanced();
        final TranslationJobImpl translation = job(project, model);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        final int atPause = model.requests().size();
        translation.resume();
        report(await(run));

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(pause.error()).extracting(AppError::code).isEqualTo(ErrorCode.unreachable);
        assertThat(atPause).isEqualTo(2);
        assertRepairSentAgain(project, model);
    }

    // IF a resume decided the segment again from its draft, THEN the reviewer's fix of the refused edit would be sent
    // a second time before the repair.
    @Test
    void resume_errorDuringRepairAfterARefusedEditFix_resendsOnlyTheRepair() {
        final ScriptedChatModel model = replies(DRAFTED)
                .answerTo(REVIEW, ok(REFUSED_EDIT))
                .answerTo(FIX, target(FIXED))
                .answerTo(REPAIR, Result.err(AppError.of(ErrorCode.upstream, "Provider failure", "Scripted.")))
                .answerTo(REPAIR, target(REPAIRED));
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), SOURCE), brief("en", "uk", QualityDial.BALANCED));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();
        report(await(run));

        assertThat(pause.reason()).isEqualTo(PauseReason.ON_ERROR);
        assertThat(formats(model)).containsExactly(DRAFT, REVIEW, FIX, REPAIR, REPAIR);
        assertThat(stored(project, "Book.md:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, REPAIRED);
    }

    private TestProject doorAtBalanced() {
        return project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.BALANCED));
    }

    // The repaired target passes the checks, which alone decide it: no reviewer call follows.
    private static void assertRepairSentAgain(final TestProject project, final ScriptedChatModel model) {
        assertThat(formats(model)).containsExactly(DRAFT, REPAIR, REPAIR);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, DOOR_TARGET);
    }

    private static LinkedBlockingQueue<Paused> pausesOf(final TranslationJobImpl translation) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        return pauses;
    }

    private static Result<ChatResponse> ok(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
