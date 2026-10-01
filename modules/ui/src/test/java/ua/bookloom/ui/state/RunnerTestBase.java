package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ScriptedReviewDesk;

/**
 * Shared fixtures for the {@link TranslationRunner} tests: a real mirror, a recording job, a cadence the test fires by
 * hand, and a listener recording every run-state change and every progress-figure change the mirror publishes.
 */
abstract class RunnerTestBase extends FxTestBase {

    static final long WAIT_SECONDS = 10;
    static final int BURST = 5000;
    static final int BATCH = 450;
    static final int TICKS = 10;
    static final Path SOURCE = Path.of("/books/in.txt");
    static final String PROJECT_ID = "project-1";
    static final String FILE_NAME = "Frankenstein.epub";
    static final ModelSelection SELECTION = new ModelSelection("pseudo", "pseudo-1");

    protected StateMirror mirror;
    protected ActivityTracker activities;
    protected ManualTicks ticks;
    protected ExecutorService executor;
    protected TranslationRunner runner;
    protected ScriptedReviewDesk desk;
    protected List<RunState> states;
    protected AtomicInteger acceptedChanges;
    protected AtomicInteger flaggedChanges;
    protected AtomicInteger remainingChanges;
    protected RecordingJob job;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the runner and mirror have no scene.
    }

    @BeforeEach
    void setUpRunner() {
        mirror = new StateMirror();
        activities = new ActivityTracker(mirror);
        ticks = new ManualTicks();
        executor = Executors.newFixedThreadPool(2, task -> {
            final Thread thread = new Thread(task, "runner-test-job");
            thread.setDaemon(true);
            return thread;
        });
        desk = new ScriptedReviewDesk();
        runner = new TranslationRunner(mirror, executor, ticks, desk);
        states = new CopyOnWriteArrayList<>();
        acceptedChanges = new AtomicInteger();
        flaggedChanges = new AtomicInteger();
        remainingChanges = new AtomicInteger();
        job = new RecordingJob();
        onFx(() -> {
            mirror.runState().addListener((o, before, after) -> states.add(after));
            mirror.accepted().addListener((o, before, after) -> acceptedChanges.incrementAndGet());
            mirror.flagged().addListener((o, before, after) -> flaggedChanges.incrementAndGet());
            mirror.remaining().addListener((o, before, after) -> remainingChanges.incrementAndGet());
            return null;
        });
    }

    @AfterEach
    void tearDownRunner() {
        executor.shutdownNow();
    }

    /** The context every started run in these tests carries: {@code Frankenstein.epub} under the pseudo model. */
    static RunContext runContext() {
        return new RunContext(PROJECT_ID, FILE_NAME, ReviewMode.UNATTENDED, QualityDial.BALANCED, SELECTION);
    }

    static JobProgress progress(final int accepted, final int flagged, final int pending) {
        return ProgressFixtures.progress(1, 1, accepted, flagged, pending);
    }

    static SegmentDecided decided(final String id, final SegmentStatus status, final JobProgress progress) {
        return new SegmentDecided(id, status, null, progress);
    }

    static AppError error() {
        return new AppError(ErrorCode.unreachable, "Unreachable", "The server did not answer.", null, true, null);
    }

    static JobReport completedReport(final int segments) {
        return new JobReport(BookFormat.TXT, JobState.COMPLETED, segments, segments, 0, List.of(), null);
    }

    static JobReport cancelledReport() {
        return new JobReport(BookFormat.TXT, JobState.CANCELLED, 10, 3, 0, List.of(), null);
    }

    static JobReport failedReport(final AppError error) {
        return new JobReport(BookFormat.TXT, JobState.FAILED, 10, 3, 0, List.of(), error);
    }

    static LogEntry log(final LogKind kind, final String... args) {
        return new LogEntry(kind, List.of(args));
    }

    void emitAccepted(final RecordingJob target, final int firstId, final int lastId) {
        IntStream.rangeClosed(firstId, lastId)
                .forEach(id -> target.emit(decided("s-" + id, SegmentStatus.ACCEPTED, progress(id, 0, BURST - id))));
    }

    /** Starts {@link #job}, and returns once the job thread is inside {@code run()}. */
    protected void startJob() throws InterruptedException {
        assertThat(runner.start(job, runContext())).isTrue();
        job.awaitRunStarted();
    }

    protected void awaitState(final RunState expected) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> mirror.runState().get()) == expected);
    }

    protected RunState state() {
        return onFx(() -> mirror.runState().get());
    }

    /** The log's lines by their wording only: no time and no repeat count. */
    protected List<LogEntry> logEntries() {
        return onFx(() -> mirror.activityLog().stream().map(LogEntry::wording).toList());
    }

    /** The log's lines as shown: with their times and repeat counts. */
    protected List<LogEntry> shownLogEntries() {
        return onFx(() -> List.copyOf(mirror.activityLog()));
    }

    /** Lets the job thread deliver everything queued, then fires one cadence tick and waits for the FX thread. */
    protected void deliverAndTick() throws InterruptedException {
        job.drain();
        ticks.fire();
        WaitForAsyncUtils.waitForFxEvents();
    }
}
