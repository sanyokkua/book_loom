package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.S0;
import static ua.bookloom.pipeline.ChunkRunFixtures.S1;
import static ua.bookloom.pipeline.ChunkRunFixtures.T0;
import static ua.bookloom.pipeline.ChunkRunFixtures.T1;
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
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The stall of the Bartimaeus hand test, replayed through a whole run: a judge that does not answer flags its segment
 * and the run goes on, a segment that keeps failing is flagged after two pauses, a paused run can skip the failing
 * segment, and a resumed run continues at the call that failed.
 */
class TranslationJobStallTest {

    private static final String FIXED_T0 = "Старий чоловік повільно пішов до гавані.";
    // The reviewer's edit for s1 swaps a word for a different one: a directed fix follows.
    private static final String REFUSED_EDIT_ON_S1 = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
            + "{\"criterion\":\"meaning\",\"quote\":\"чоловік пішов\",\"replacement\":\"чоловік побіг\"}]}]}";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Only a stall degrades the reviewer; a provider outage pauses, as TranslationJobUnattendedRecoveryTest proves. The
    // second request is the same call sent again without its response format.
    @Test
    void run_chunkReviewTimesOutTwice_flagsTheChunkAndFinishesWithoutAPause() {
        final ScriptedChatModel model = replies(T0, T1)
                .answerTo(REVIEW, Result.err(error(ErrorCode.timeout)))
                .answer(Result.err(error(ErrorCode.timeout)));
        final TestProject project = project(twoParagraphs(), brief("en", "uk", QualityDial.BALANCED));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final JobReport report = report(await(executor().submit(translation::run)));

        assertThat(pauses).isEmpty();
        assertThat(report.flagged()).isEqualTo(2);
        assertThat(stored(project, "Book.md:1").machineTarget()).isEqualTo(T1);
    }

    // Two pauses for the same draft are the budget: the third failure flags it and the run sends the next draft. A
    // timeout spends the budget; an outage would not, as TranslationJobUnattendedRecoveryTest proves.
    @Test
    void run_draftFailingAfterTwoPauses_isFlaggedAndTheRunGoesOn() {
        final ScriptedChatModel model = replies("ONE.")
                .answer(Result.err(error(ErrorCode.timeout)))
                .answer(Result.err(error(ErrorCode.timeout)))
                .answer(Result.err(error(ErrorCode.timeout)))
                .answer(target("THREE."));
        final TranslationJobImpl translation = job(project(threeLines(), brief("en", "uk")), model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.resume();
        awaitPaused(pauses);
        translation.resume();
        final JobReport report = report(await(run));

        assertThat(pauses).isEmpty();
        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.timeout));
        assertThat(report.accepted()).isEqualTo(2);
    }

    @Test
    void skipSegment_whilePausedOnAnError_flagsThatSegmentAndContinues() {
        final ScriptedChatModel model =
                replies("ONE.").answer(Result.err(error(ErrorCode.upstream))).answer(target("THREE."));
        final TestProject project = project(threeLines(), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.skipSegment();
        final JobReport report = report(await(run));

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:1", ErrorCode.upstream));
        assertThat(stored(project, "Book.txt:2").status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(model.requests()).hasSize(3);
    }

    // The reviewer had already answered when the directed fix for its refused edit failed: resuming sends the fix
    // again,
    // not the reviewer.
    @Test
    void run_resumedAfterTheFixForARefusedEditFailed_continuesAtTheFix() {
        final ScriptedChatModel model = replies(T0, T1)
                .answer(Result.err(error(ErrorCode.upstream)))
                .answer(target(FIXED_T0))
                .answerTo(REVIEW, ok(REFUSED_EDIT_ON_S1));
        final TestProject project = project(twoParagraphs(), brief("en", "uk", QualityDial.BALANCED));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.resume();
        final JobReport report = report(await(run));

        assertThat(report.accepted()).isEqualTo(2);
        assertThat(stored(project, "Book.md:0").machineTarget()).isEqualTo(FIXED_T0);
        assertThat(formats(model)).containsExactly(DRAFT, DRAFT, REVIEW, FIX, FIX);
    }

    private Path twoParagraphs() {
        return TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", S0, S1));
    }

    private Path threeLines() {
        return TestBooks.txt(tempDir.resolve("Book.txt"), "One.\n\nTwo.\n\nThree.");
    }

    private static LinkedBlockingQueue<Paused> pausesOf(final TranslationJobImpl translation) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        return pauses;
    }

    private static Result<ChatResponse> ok(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private static AppError error(final ErrorCode code) {
        return AppError.of(code, "Provider failure", "The scripted provider failed.");
    }
}
