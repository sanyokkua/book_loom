package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedReviewDesk;

/** The background checks of the structure screen: what they ask, on which thread, and what they publish. */
class StructureChecksViewModelTest extends FxTestBase {

    private static final long WAIT_SECONDS = 10;
    private static final RoundTripReport PRESERVED = new RoundTripReport(true, true, List.of(), 1240, 1240);
    private static final RoundTripReport LOST_AN_IMAGE = new RoundTripReport(true, false, List.of("img-7"), 1240, 1240);

    private ScriptedProjectService projects;
    private ScriptedReviewDesk desk;
    private ExecutorService executor;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUpFakes() {
        projects = new ScriptedProjectService();
        desk = new ScriptedReviewDesk();
        executor = new DirectExecutor();
    }

    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    private static BookPlan planWith(final String... oversized) {
        return new BookPlan(Map.of("unit-0", 3), List.of(oversized));
    }

    private StructureChecksViewModel viewModel() {
        return onFx(() -> new StructureChecksViewModel(projects, desk, executor));
    }

    private static StructureChecks stateOf(final StructureChecksViewModel viewModel) {
        return onFx(() -> viewModel.state().get());
    }

    private static void run(final StructureChecksViewModel viewModel, final String projectId) {
        onFx(() -> {
            viewModel.run(projectId);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the run's segment count were not read, THEN Structure could not say why Translating starts above the book
    // text;
    // the segments the brief keeps as source are not translated, so they are left out.
    @Test
    void run_countsAnswer_publishesTheSegmentsARunTranslates() {
        projects.onRoundTrip(Result.ok(LOST_AN_IMAGE));
        projects.onPlan(Result.ok(planWith()));
        desk.willAnswerCounts(new ReviewCounts(167, 0, 0, 0, 0, 163, 4, 0, 0));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(LOST_AN_IMAGE, 0, 163));
    }

    // IF the checks published nothing but a report, THEN the oversized warning could never be shown.
    @Test
    void run_bothChecksAnswer_publishesTheReportAndTheOversizedCount() {
        projects.onRoundTrip(Result.ok(LOST_AN_IMAGE));
        projects.onPlan(Result.ok(planWith("s-4", "s-9")));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(LOST_AN_IMAGE, 2));
        assertThat(projects.roundTripProjects()).containsExactly("p1");
        assertThat(projects.planProjects()).containsExactly("p1");
    }

    // IF a person could not tell a check still running from one that found nothing, THEN they would trust a blank.
    @Test
    void run_beforeTheAnswerLands_isRunning() {
        final QueuedExecutor queued = new QueuedExecutor();
        executor = queued;
        projects.onRoundTrip(Result.ok(PRESERVED));
        projects.onPlan(Result.ok(planWith()));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isInstanceOf(StructureChecks.Running.class);
        assertThat(queued.pending()).isEqualTo(1);
    }

    // IF a failed round trip hid the plan, THEN one broken check would silence the other.
    @Test
    void run_roundTripFails_stillPublishesThePlanWithNoReport() {
        projects.onRoundTrip(Result.err(AppError.of(ErrorCode.internal, "Broken", "The check failed.")));
        projects.onPlan(Result.ok(planWith("s-4")));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(null, 1));
    }

    // IF a failed plan gave a warning, THEN a person would be told of segments nobody counted.
    @Test
    void run_planFails_publishesTheReportAndNoOversizedSegments() {
        projects.onRoundTrip(Result.ok(PRESERVED));
        projects.onPlan(Result.err(AppError.of(ErrorCode.internal, "Broken", "The plan failed.")));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(PRESERVED, 0));
    }

    // IF the checks ran on the FX thread, THEN opening a large book's structure would freeze the window.
    @Test
    void run_realBackgroundExecutor_askedTheServiceOffTheFxThread() throws TimeoutException {
        executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "checks-test-background");
            thread.setDaemon(true);
            return thread;
        });
        projects.onRoundTrip(Result.ok(PRESERVED));
        projects.onPlan(Result.ok(planWith()));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> stateOf(viewModel) instanceof StructureChecks.Finished);

        assertThat(projects.roundTripCallsOnFxThread()).containsExactly(false);
        assertThat(projects.planCallsOnFxThread()).containsExactly(false);
    }

    // IF a slow check of a book the person left overwrote the newer check, THEN the screen would show another book's
    // findings.
    @Test
    void run_olderRunAnswersAfterANewerOne_isDropped() {
        final QueuedExecutor queued = new QueuedExecutor();
        executor = queued;
        final StructureChecksViewModel viewModel = viewModel();
        run(viewModel, "old");
        run(viewModel, "new");
        projects.onRoundTrip(Result.ok(PRESERVED));
        projects.onPlan(Result.ok(planWith("s-4")));
        onFx(() -> {
            queued.runAt(1);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        projects.onPlan(Result.ok(planWith("s-1", "s-2", "s-3")));
        onFx(() -> {
            queued.runAt(0);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(PRESERVED, 1));
    }

    // IF a service that throws escaped the executor, THEN the screen would stay on "running" for ever.
    @Test
    void run_serviceThrows_publishesFinishedWithNothing() {
        projects.throwingOnRoundTrip(new IllegalStateException("boom"));
        final StructureChecksViewModel viewModel = viewModel();

        run(viewModel, "p1");

        assertThat(stateOf(viewModel)).isEqualTo(new StructureChecks.Finished(null, 0));
    }
}
