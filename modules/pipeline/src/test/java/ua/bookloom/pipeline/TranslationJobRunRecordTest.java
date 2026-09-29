package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** The stored run state moves with the run, so a review desk reading it never sees a finished run as running. */
class TranslationJobRunRecordTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Writing the state only at the end would leave RUNNING unseen and the pause invisible to a reader.
    @Test
    void runRecord_followsTheRun() {
        final TestProject project = project(book("One.\n\nTwo."), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("ONE.", "TWO."));
        final List<JobState> observed = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> observeStored(project, observed, event));
        translation.subscribe(event -> capturePaused(pauses, event));
        translation.pause();

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.resume();
        report(await(run));

        assertThat(observed).containsExactly(JobState.RUNNING, JobState.RUNNING, JobState.PAUSED, JobState.RUNNING);
        final RunRecord record = latest(project);
        assertThat(record.state()).isEqualTo(JobState.COMPLETED);
        assertThat(record.endedAt()).isNotNull();
        assertThat(record.startedAt()).isBeforeOrEqualTo(Objects.requireNonNull(record.endedAt()));
        assertThat(record).extracting(RunRecord::accepted, RunRecord::flagged).containsExactly(2, 0);
    }

    @Test
    void runRecord_stopDuringRun_endsCancelledWithItsOwnCounts() {
        final TestProject project = project(book("One.\n\nTwo."), brief("en", "uk"));
        final TranslationJobImpl translation = job(project, replies("ONE.", "TWO."));
        translation.subscribe(event -> cancelAfterFirstDecision(translation, event));

        report(translation.run());

        final RunRecord record = latest(project);
        assertThat(record.state()).isEqualTo(JobState.CANCELLED);
        assertThat(record.endedAt()).isNotNull();
        assertThat(record.accepted()).isEqualTo(1);
    }

    @Test
    void runRecord_modelCallThrows_endsFailed() {
        final TestProject project = project(book("One."), brief("en", "uk"));
        final ScriptedChatModel model = new ScriptedChatModel().throwFailure(new IllegalStateException("broke"));

        report(job(project, model).run());

        final RunRecord record = latest(project);
        assertThat(record.state()).isEqualTo(JobState.FAILED);
        assertThat(record.endedAt()).isNotNull();
    }

    private static void observeStored(final TestProject project, final List<JobState> observed, final JobEvent event) {
        if (event instanceof StageStarted || event instanceof Paused || event instanceof Resumed) {
            observed.add(latest(project).state());
        }
    }

    private static void cancelAfterFirstDecision(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof ua.bookloom.api.pipeline.SegmentDecided) {
            translation.cancel();
        }
    }

    private static RunRecord latest(final TestProject project) {
        final Optional<RunRecord> latest = Objects.requireNonNull(
                project.stores().runs().latest(project.id()).data(), "latest");
        return latest.orElseThrow();
    }

    private Path book(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }
}
