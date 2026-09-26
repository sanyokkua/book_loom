package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.stage.Stage;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;

/**
 * The one bridge from engine threads to the scene graph: every publisher may be called from any thread and lands on
 * the FX Application Thread, and the exposed state is read-only from outside.
 */
class StateMirrorTest extends ApplicationTest {

    private static final int FIVE_HUNDRED = 500;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all this test needs; the mirror is a plain object with no scene of its own.
    }

    private static AppError error() {
        return new AppError(ErrorCode.unreachable, "Unreachable", "The server did not answer.", null, true, null);
    }

    private static JobReport cancelledReport() {
        return new JobReport(BookFormat.TXT, JobState.CANCELLED, 10, 3, 0, List.of(), null, null);
    }

    private static LogEntry accepted(final int index) {
        return new LogEntry(LogKind.ACCEPTED, List.of("s-" + index));
    }

    private static List<LogEntry> entries(final int from, final int toExclusive) {
        return IntStream.range(from, toExclusive)
                .mapToObj(StateMirrorTest::accepted)
                .toList();
    }

    private static void publishFromAnotherThread(final Runnable publish) throws InterruptedException {
        final Thread worker = new Thread(publish, "mirror-test-publisher");
        worker.start();
        worker.join();
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Records, for every change of every exposed property, whether it happened on the FX Application Thread. */
    private static List<Boolean> recordChangeThreads(final StateMirror mirror) {
        final List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
        onFx(() -> {
            mirror.runState().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.accepted().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.flagged().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.remaining().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.total().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.progressFraction().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.failure().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.report().addListener((o, a, b) -> onFxThread.add(Platform.isFxApplicationThread()));
            mirror.activityLog()
                    .addListener((ListChangeListener<LogEntry>) c -> onFxThread.add(Platform.isFxApplicationThread()));
            return null;
        });
        return onFxThread;
    }

    static Stream<Arguments> publishers() {
        return Stream.of(
                Arguments.of(Named.<Consumer<StateMirror>>of("publishRunStarted", StateMirror::publishRunStarted)),
                Arguments.of(
                        Named.<Consumer<StateMirror>>of("publishRunState", m -> m.publishRunState(RunState.PAUSED))),
                Arguments.of(Named.<Consumer<StateMirror>>of(
                        "publishProgress", m -> m.publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 3, 1, 4)))),
                Arguments.of(Named.<Consumer<StateMirror>>of(
                        "publishLogEntries", m -> m.publishLogEntries(List.of(accepted(1))))),
                Arguments.of(Named.<Consumer<StateMirror>>of(
                        "publishOutcome", m -> m.publishOutcome(RunState.FAILED, cancelledReport(), error()))));
    }

    // IF a publisher touched a property on the calling thread, THEN an engine thread would mutate the scene graph.
    @ParameterizedTest
    @MethodSource("publishers")
    void publish_fromANonFxThread_changesPropertiesOnTheFxThread(final Consumer<StateMirror> publisher)
            throws InterruptedException {
        final StateMirror mirror = new StateMirror();
        final List<Boolean> onFxThread = recordChangeThreads(mirror);

        publishFromAnotherThread(() -> publisher.accept(mirror));

        assertThat(onFxThread).isNotEmpty().containsOnly(true);
    }

    // IF a fresh mirror did not start idle and empty, THEN the dashboard would open showing a phantom run.
    @Test
    void newMirror_beforeAnyPublish_isIdleAndEmpty() {
        final StateMirror mirror = new StateMirror();

        assertThat(onFx(() -> mirror.runState().get())).isEqualTo(RunState.IDLE);
        assertThat(onFx(() -> mirror.accepted().get())).isZero();
        assertThat(onFx(() -> mirror.flagged().get())).isZero();
        assertThat(onFx(() -> mirror.remaining().get())).isZero();
        assertThat(onFx(() -> mirror.total().get())).isZero();
        assertThat(onFx(() -> mirror.progressFraction().get())).isEqualTo(0.0);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.report().get())).isNull();
        assertThat(onFx(() -> mirror.activityLog().size())).isZero();
    }

    // IF the bound were not 500, THEN the log would grow without limit over a long book or be cut too early.
    @Test
    void maxLogEntries_isFiveHundred() {
        assertThat(StateMirror.MAX_LOG_ENTRIES).isEqualTo(FIVE_HUNDRED);
    }

    // IF the log kept every entry, THEN a book of thousands of segments would exhaust memory in the list view.
    @Test
    void publishLogEntries_fiveHundredAndOneEntries_keepsExactlyFiveHundredAndDropsTheFirst() {
        final StateMirror mirror = new StateMirror();

        mirror.publishLogEntries(entries(0, 501));
        WaitForAsyncUtils.waitForFxEvents();

        final List<LogEntry> log = onFx(() -> List.copyOf(mirror.activityLog()));
        assertThat(log).hasSize(500);
        assertThat(log.get(0)).isEqualTo(accepted(1));
        assertThat(log.get(499)).isEqualTo(accepted(500));
        assertThat(log).doesNotContain(accepted(0));
    }

    // IF batches were bounded one at a time, THEN two batches together could exceed the bound.
    @Test
    void publishLogEntries_twoBatchesOverTheBound_dropsOldestAcrossBatches() {
        final StateMirror mirror = new StateMirror();

        mirror.publishLogEntries(entries(0, 300));
        mirror.publishLogEntries(entries(300, 600));
        WaitForAsyncUtils.waitForFxEvents();

        final List<LogEntry> log = onFx(() -> List.copyOf(mirror.activityLog()));
        assertThat(log).hasSize(500);
        assertThat(log.get(0)).isEqualTo(accepted(100));
        assertThat(log.get(499)).isEqualTo(accepted(599));
    }

    // IF a batch under the bound were trimmed, THEN entries would vanish before the log was full.
    @Test
    void publishLogEntries_underTheBound_keepsEverythingInOrder() {
        final StateMirror mirror = new StateMirror();

        mirror.publishLogEntries(entries(0, 3));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> List.copyOf(mirror.activityLog())))
                .containsExactly(accepted(0), accepted(1), accepted(2));
    }

    // IF a caller could edit the log directly, THEN publish* would not be the only mutators.
    @Test
    void activityLog_directEdit_isRejected() {
        final StateMirror mirror = new StateMirror();

        assertThatThrownBy(() -> mirror.activityLog().add(accepted(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // IF the mirror carried the snapshot's raw fields, THEN remaining and total (which the engine does not send)
    // would be missing from the dashboard.
    @Test
    void publishProgress_snapshotOf768And3And469_publishesTheDerivedFigures() {
        final StateMirror mirror = new StateMirror();

        mirror.publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 768, 3, 469));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(768);
        assertThat(onFx(() -> mirror.flagged().get())).isEqualTo(3);
        assertThat(onFx(() -> mirror.remaining().get())).isEqualTo(469);
        assertThat(onFx(() -> mirror.total().get())).isEqualTo(1240);
        assertThat(onFx(() -> mirror.progressFraction().get())).isCloseTo(0.6217741935483871, within(1e-12));
    }

    // IF the chapter index fed the proportion, THEN moving to a later chapter would move the bar.
    @Test
    void publishProgress_sameCountsInAnotherChapter_publishesTheSameFigures() {
        final StateMirror mirror = new StateMirror();

        mirror.publishProgress(new JobProgress(JobStage.TRANSLATE, 7, 11, 768, 3, 469));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.total().get())).isEqualTo(1240);
        assertThat(onFx(() -> mirror.progressFraction().get())).isCloseTo(0.6217741935483871, within(1e-12));
    }

    // IF an all-zero snapshot divided by zero, THEN the bar would be NaN or the publish would fail.
    @Test
    void publishProgress_allZeroSnapshot_publishesZeroProportion() {
        final StateMirror mirror = new StateMirror();

        mirror.publishProgress(new JobProgress(JobStage.TRANSLATE, 0, 0, 0, 0, 0));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.progressFraction().get())).isEqualTo(0.0);
        assertThat(onFx(() -> mirror.total().get())).isZero();
    }

    // IF the terminal publish dropped the report or the failure, THEN the export screen and the error surface would
    // have nothing to read.
    @Test
    void publishOutcome_failedWithReportAndError_setsAllThree() {
        final StateMirror mirror = new StateMirror();
        final JobReport report = cancelledReport();
        final AppError error = error();

        mirror.publishOutcome(RunState.FAILED, report, error);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.runState().get())).isEqualTo(RunState.FAILED);
        assertThat(onFx(() -> mirror.report().get())).isSameAs(report);
        assertThat(onFx(() -> mirror.failure().get())).isSameAs(error);
    }

    // IF a stopped run carried a failure, THEN a neutral stop would render as an error.
    @Test
    void publishOutcome_stoppedWithNoError_leavesFailureNull() {
        final StateMirror mirror = new StateMirror();

        mirror.publishOutcome(RunState.STOPPED, cancelledReport(), null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.runState().get())).isEqualTo(RunState.STOPPED);
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.report().get())).isNotNull();
    }

    // IF a new run inherited the previous run's figures, log or outcome, THEN it would open showing stale results.
    @Test
    void publishRunStarted_afterAFinishedRun_resetsEverything() {
        final StateMirror mirror = new StateMirror();
        mirror.publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 768, 3, 469));
        mirror.publishLogEntries(entries(0, 5));
        mirror.publishOutcome(RunState.FAILED, cancelledReport(), error());
        WaitForAsyncUtils.waitForFxEvents();

        mirror.publishRunStarted();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.runState().get())).isEqualTo(RunState.RUNNING);
        assertThat(onFx(() -> mirror.accepted().get())).isZero();
        assertThat(onFx(() -> mirror.flagged().get())).isZero();
        assertThat(onFx(() -> mirror.remaining().get())).isZero();
        assertThat(onFx(() -> mirror.total().get())).isZero();
        assertThat(onFx(() -> mirror.progressFraction().get())).isEqualTo(0.0);
        assertThat(onFx(() -> mirror.activityLog().size())).isZero();
        assertThat(onFx(() -> mirror.failure().get())).isNull();
        assertThat(onFx(() -> mirror.report().get())).isNull();
    }

    // IF publishRunState also cleared the figures, THEN a pause would blank the counts the person is watching.
    @Test
    void publishRunState_afterProgress_changesOnlyTheState() {
        final StateMirror mirror = new StateMirror();
        mirror.publishProgress(new JobProgress(JobStage.TRANSLATE, 1, 1, 412, 0, 88));

        mirror.publishRunState(RunState.PAUSING);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> mirror.runState().get())).isEqualTo(RunState.PAUSING);
        assertThat(onFx(() -> mirror.accepted().get())).isEqualTo(412);
        assertThat(onFx(() -> mirror.total().get())).isEqualTo(500);
    }
}
