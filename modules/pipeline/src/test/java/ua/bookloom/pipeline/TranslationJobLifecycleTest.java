package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** Covers what a finished run leaves stored, startup refusals, terminal controls, and one-run claiming. */
class TranslationJobLifecycleTest {

    @TempDir
    private Path tempDir;

    // Removing the successful terminal branch would make this report differ, and the run must write no book itself.
    @Test
    void run_threeAcceptedSegments_completesWithProjectCountsAndWritesNoFile() {
        final Path source = markdown("One.\n\nTwo.\n\nThree.");
        final TranslationJobImpl translation = job(source, replies("ONE.", "TWO.", "THREE."));

        final Result<JobReport> result = translation.run();

        assertThat(result.isOk()).isTrue();
        assertThat(report(result))
                .extracting(
                        JobReport::end,
                        JobReport::format,
                        JobReport::segments,
                        JobReport::accepted,
                        JobReport::flagged,
                        JobReport::error)
                .containsExactly(JobState.COMPLETED, BookFormat.MARKDOWN, 3, 3, 0, null);
        assertThat(report(result).flaggedSegments()).isEmpty();
        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(tempDir.resolve("Book.uk.md")).doesNotExist();
    }

    // Storing the decision only in the job would leave the record pending and the masked target unset.
    @Test
    void run_acceptedAndFlaggedMarkdown_storesEachDecisionInItsRecord() {
        final Path source = markdown("He opened the *old* door.\n\nKeep *this* paragraph.");
        final ScriptedChatModel model =
                replies("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.").answer(TranslationJobTestSupport.cutOffReply());
        final TestProject project = project(source, brief("en", "uk"));

        final JobReport completed = report(job(project, model).run());

        assertThat(completed)
                .extracting(JobReport::end, JobReport::accepted, JobReport::flagged)
                .containsExactly(JobState.COMPLETED, 1, 1);
        assertThat(stored(project, "Book.md:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget, SegmentRecord::maskedMachineTarget)
                .containsExactly(
                        SegmentStatus.ACCEPTED, "HE OPENED THE *OLD* DOOR.", "HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.");
        assertThat(stored(project, "Book.md:1"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, null);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 1, 0, 1, 0));
    }

    // Reading the source again in the job would hide a book that was closed after the import.
    @Test
    void run_bookNoLongerOpen_returnsStartupError() {
        final ScriptedChatModel model = replies("ONE.");
        final TestProject project = project(markdown("One."), brief("en", "uk"));
        project.stores().openProjects().remove(project.id());
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> result = translation.run();

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
        assertThat(events).isEmpty();
        assertThat(translation.state()).isEqualTo(JobState.FAILED);
    }

    // Dropping the claimed flag would run a completed job a second time and repeat its model call.
    @Test
    void run_twice_secondIsValidation() {
        final Path source = markdown("One.");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(source, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);

        final Result<JobReport> first = translation.run();
        final int eventCount = events.size();

        final Result<JobReport> second = translation.run();

        assertThat(report(first).end()).isEqualTo(JobState.COMPLETED);
        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(second.error()).extracting(AppError::code).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).hasSize(1);
        assertThat(events).hasSize(eventCount);
    }

    // Publishing the claim after StageStarted would permit this recursive call to start duplicate work.
    @Test
    void run_calledRecursivelyFromStageCallback_returnsValidation() {
        final Path source = markdown("One.");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(source, model);
        final AtomicReference<Result<JobReport>> nested = new AtomicReference<>();
        translation.subscribe(event -> recordRecursiveRun(translation, nested, event));

        final Result<JobReport> outer = translation.run();

        assertThat(report(outer).end()).isEqualTo(JobState.COMPLETED);
        assertThat(nested.get())
                .extracting(Result::error)
                .extracting(AppError::code)
                .isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).hasSize(1);
    }

    // Deciding a segment before checking cancellation would leave this segment decided instead of pending.
    @Test
    void cancel_beforeRun_finishesCancelledWithoutDeciding() {
        final Path source = markdown("One.");
        final ScriptedChatModel model = replies("ONE.");
        final TranslationJobImpl translation = job(source, model);
        final List<JobEvent> events = new ArrayList<>();
        translation.subscribe(events::add);
        translation.cancel();

        assertThat(translation.state()).isEqualTo(JobState.CANCELLED);

        final Result<JobReport> result = translation.run();

        assertThat(translation.state()).isEqualTo(JobState.CANCELLED);
        assertThat(report(result))
                .extracting(
                        JobReport::end, JobReport::segments, JobReport::accepted, JobReport::flagged, JobReport::error)
                .containsExactly(JobState.CANCELLED, 1, 0, 0, null);
        assertThat(model.requests()).isEmpty();
        assertThat(events).hasSize(1).allMatch(Finished.class::isInstance);
        assertThat(((Finished) events.getFirst()).report()).isEqualTo(report(result));
    }

    // Replaying stored events or mutating a terminal state would make this late listener observe work.
    @Test
    void controls_terminalJob_areNoOpsAndSubscriptionsDoNotReplay() {
        final TranslationJobImpl translation = job(markdown("One."), replies("ONE."));
        translation.run();
        final List<JobEvent> lateEvents = new ArrayList<>();
        translation.subscribe(lateEvents::add);

        translation.pause();
        translation.resume();
        translation.cancel();
        translation.pauseAt(java.util.Set.of());

        assertThat(translation.state()).isEqualTo(JobState.COMPLETED);
        assertThat(lateEvents).isEmpty();
    }

    private Path markdown(final String content) {
        return TestBooks.markdown(tempDir.resolve("Book.md"), content);
    }

    private static void recordRecursiveRun(
            final TranslationJobImpl translation,
            final AtomicReference<Result<JobReport>> nested,
            final JobEvent event) {
        if (event instanceof StageStarted started && started.stage() == JobStage.TRANSLATE) {
            nested.set(translation.run());
        }
    }
}
