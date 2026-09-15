package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.documents;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.shutdown;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;

/** Proves ordinary pause points stop exactly once at each document boundary. */
class TranslationJobPauseBoundariesTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Removing the post-decision request check would let call two begin before this pause.
    @Test
    void pause_requestedAfterFirstDecision_stopsBeforeSecondCall() {
        final ScriptedChatModel model = replies("ONE.", "TWO.", "THREE.");
        final TranslationJobImpl translation = markdownJob(model, "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final AtomicReference<JobState> pausedState = new AtomicReference<>();
        translation.subscribe(event -> requestAfterFirst(translation, pauses, pausedState, event));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pausedState.get()).isEqualTo(JobState.PAUSED);
        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, 2);
        assertThat(model.requests()).hasSize(1);
        translation.resume();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).hasSize(3);
        shutdown(workers);
    }

    // Dropping the pre-run request would allow a model call before this first boundary pause.
    @Test
    void pause_requestedBeforeRun_stopsBeforeFirstModelCall() {
        final ScriptedChatModel model = replies("ONE.", "TWO.", "THREE.");
        final TranslationJobImpl translation = markdownJob(model, "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        final List<JobEvent> events = new java.util.ArrayList<>();
        translation.subscribe(events::add);
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pause();
        final ExecutorService workers = executor();

        assertThat(translation.state()).isEqualTo(JobState.NEW);

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(0, 0, 3);
        assertThat(events)
                .extracting(event -> event.getClass().getSimpleName())
                .containsExactly("StageStarted", "Paused");
        assertThat(model.requests()).isEmpty();
        translation.resume();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Failing to clear a NEW-state pause request would emit an unexpected pause on this run.
    @Test
    void resume_preRunPauseRequest_clearsIt() {
        final TranslationJobImpl translation = markdownJob(replies("ONE."), "One.");
        final List<JobEvent> events = new java.util.ArrayList<>();
        translation.subscribe(events::add);
        translation.pause();
        translation.resume();

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events).noneMatch(Paused.class::isInstance);
        assertThat(events).noneMatch(Resumed.class::isInstance);
    }

    // Keeping a request raised during model work after resume would pause this completed first answer.
    @Test
    void resume_requestedWhileModelRuns_clearsPendingPause() {
        final TranslationJobTestSupport.BlockingChatModel model =
                new TranslationJobTestSupport.BlockingChatModel(replies("ONE."));
        final TranslationJobImpl translation = markdownJob(model, "One.");
        final List<JobEvent> events = new java.util.ArrayList<>();
        translation.subscribe(events::add);
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        model.awaitEntered();
        translation.pause();
        translation.resume();
        model.release();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events).noneMatch(Paused.class::isInstance);
        assertThat(events).noneMatch(Resumed.class::isInstance);
        shutdown(workers);
    }

    // Re-evaluating the resumed boundary would produce more than these three after-segment pauses.
    @Test
    void pauseAt_afterSegment_pausesAtEveryDecidedBoundary() {
        final TranslationJobImpl translation = markdownJob(replies("ONE.", "TWO.", "THREE."), "One.\n\nTwo.\n\nThree.");
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();
        final Paused third = awaitPaused(pauses);
        translation.resume();

        assertThat(first).extracting(Paused::reason).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(second).extracting(Paused::reason).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(third).extracting(Paused::reason).isEqualTo(PauseReason.AFTER_SEGMENT);
        assertThat(first.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, 2);
        assertThat(second.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(2, 0, 1);
        assertThat(third.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(3, 0, 0);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        shutdown(workers);
    }

    // Treating a spine as one section would miss the first of these two pauses.
    @Test
    void pauseAt_afterSection_epubTwoPlusOne_pausesTwice() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("One.", "Two."), List.of("Three.")), "en");
        final ScriptedChatModel model = replies("ONE.", "TWO.", "THREE.");
        final TranslationJobImpl translation = job(documents(), source, tempDir.resolve("Book.uk.epub"), model);
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SECTION));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused first = awaitPaused(pauses);
        translation.resume();
        final Paused second = awaitPaused(pauses);
        translation.resume();

        assertThat(first.progress())
                .extracting(p -> p.section(), p -> p.accepted(), p -> p.pending())
                .containsExactly(0, 2, 1);
        assertThat(second.progress())
                .extracting(p -> p.section(), p -> p.accepted(), p -> p.pending())
                .containsExactly(1, 3, 0);
        assertThat(first.reason()).isEqualTo(PauseReason.AFTER_SECTION);
        assertThat(second.reason()).isEqualTo(PauseReason.AFTER_SECTION);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests()).hasSize(3);
        shutdown(workers);
    }

    // Pausing an empty source section would make this queue receive a second event.
    @Test
    void pauseAt_afterSection_emptyThenTwo_skipsEmptySection() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of(), List.of("One.", "Two.")), "en");
        final TranslationJobImpl translation =
                job(documents(), source, tempDir.resolve("Book.uk.epub"), replies("ONE.", "TWO."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SECTION));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause.progress())
                .extracting(p -> p.section(), p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 2, 0, 0);
        assertThat(pause.reason()).isEqualTo(PauseReason.AFTER_SECTION);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(pauses).isEmpty();
        shutdown(workers);
    }

    // Starting export before this pause would create the destination and temporary book too soon.
    @Test
    void pauseAt_betweenStages_waitsBeforeCreatingDestination() {
        final Path destination = tempDir.resolve("Book.uk.md");
        final TranslationJobImpl translation = job(documents(), markdown("One."), destination, replies("ONE."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.BETWEEN_STAGES));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.reason()).isEqualTo(PauseReason.BETWEEN_STAGES);
        assertThat(pause.progress())
                .extracting(p -> p.stage(), p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(ua.bookloom.api.pipeline.JobStage.TRANSLATE, 1, 0, 0);
        assertThat(Files.exists(destination)).isFalse();
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
        translation.resume();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(Files.exists(destination)).isTrue();
        shutdown(workers);
    }

    // Introducing an implicit pause with an empty pause-point set would add a Paused event here.
    @Test
    void pauseAt_none_neverPauses() {
        final ScriptedChatModel model = replies("ONE.", "TWO.", "THREE.");
        final TranslationJobImpl translation = markdownJob(model, "One.\n\nTwo.\n\nThree.");
        final List<JobEvent> events = new java.util.ArrayList<>();
        translation.subscribe(events::add);
        translation.pauseAt(Set.of());

        final Result<JobReport> result = translation.run();

        assertThat(report(result).end()).isEqualTo(JobState.COMPLETED);
        assertThat(events).noneMatch(Paused.class::isInstance);
        assertThat(events).noneMatch(Resumed.class::isInstance);
        assertThat(model.requests()).hasSize(3);
    }

    // Choosing a smaller matching point would report AFTER_SEGMENT instead of BETWEEN_STAGES.
    @Test
    void pauseAt_coincidentPoints_usesWidestReasonOnce() {
        final Path source = TestBooks.txt(tempDir.resolve("Book.txt"), "One.");
        final TranslationJobImpl translation =
                job(documents(), source, tempDir.resolve("Book.uk.txt"), replies("ONE."));
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pauseAt(Set.of(PausePoint.AFTER_SEGMENT, PausePoint.AFTER_SECTION, PausePoint.BETWEEN_STAGES));
        final ExecutorService workers = executor();

        final Future<Result<JobReport>> run = workers.submit(translation::run);
        final Paused pause = awaitPaused(pauses);
        translation.resume();

        assertThat(pause.reason()).isEqualTo(PauseReason.BETWEEN_STAGES);
        assertThat(pause.progress())
                .extracting(p -> p.accepted(), p -> p.flagged(), p -> p.pending())
                .containsExactly(1, 0, 0);
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(pauses).isEmpty();
        shutdown(workers);
    }

    private TranslationJobImpl markdownJob(final ua.bookloom.api.llm.ChatModel model, final String content) {
        return job(documents(), markdown(content), tempDir.resolve("Book.uk.md"), model);
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static void requestAfterFirst(
            final TranslationJobImpl translation,
            final LinkedBlockingQueue<Paused> pauses,
            final AtomicReference<JobState> pausedState,
            final JobEvent event) {
        if (event instanceof Paused) {
            pausedState.set(translation.state());
        }
        capturePaused(pauses, event);
        if (event instanceof SegmentDecided decided && decided.progress().accepted() == 1) {
            translation.pause();
        }
    }
}
