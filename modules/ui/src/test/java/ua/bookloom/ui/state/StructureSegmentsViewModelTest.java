package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
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
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedProjectService;

/** The structure screen's segment listing: what it asks, on which thread, what it publishes and what it drops. */
class StructureSegmentsViewModelTest extends FxTestBase {

    private static final long WAIT_SECONDS = 10;

    private ScriptedProjectService projects;
    private ExecutorService executor;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUpFakes() {
        projects = new ScriptedProjectService();
        executor = Executors.newFixedThreadPool(2, task -> {
            final Thread thread = new Thread(task, "segments-test-background");
            thread.setDaemon(true);
            return thread;
        });
    }

    @AfterEach
    void tearDownExecutor() {
        executor.shutdownNow();
    }

    private static SegmentPreview preview(final String id, final int chunk, final int chunks) {
        return new SegmentPreview(
                id,
                "ch1 · " + id,
                SegmentKind.PARAGRAPH,
                "Text " + id,
                "Text " + id,
                3,
                false,
                false,
                chunk,
                chunks,
                false);
    }

    private StructureSegmentsViewModel viewModel() {
        return onFx(() -> new StructureSegmentsViewModel(projects, executor));
    }

    private static StructureSegments stateOf(final StructureSegmentsViewModel viewModel) {
        return onFx(() -> viewModel.state().get());
    }

    private void show(final StructureSegmentsViewModel viewModel, final String... units) {
        onFx(() -> {
            viewModel.show("p1", List.of(units));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitLoaded(final StructureSegmentsViewModel viewModel) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> stateOf(viewModel) instanceof StructureSegments.Loaded);
    }

    // IF the chunk boundary were not marked on the row that opens a chunk, THEN the list could not show where a chunk
    // ends.
    @Test
    void show_twoUnits_marksTheFirstRowOfEachChunkAndSumsTheChunks() throws TimeoutException {
        projects.onSegments("u1", Result.ok(List.of(preview("a", 1, 2), preview("b", 1, 2), preview("c", 2, 2))));
        projects.onSegments("u2", Result.ok(List.of(preview("d", 1, 1))));
        final StructureSegmentsViewModel viewModel = viewModel();

        show(viewModel, "u1", "u2");
        awaitLoaded(viewModel);

        final StructureSegments.Loaded loaded = (StructureSegments.Loaded) stateOf(viewModel);
        assertThat(loaded.chunks()).isEqualTo(3);
        assertThat(loaded.rows().stream().map(StructureSegmentRow::chunkStart).toList())
                .containsExactly(true, false, true, true);
        assertThat(projects.segmentCalls()).containsExactly("p1/u1", "p1/u2");
    }

    // IF the listing ran on the FX thread, THEN picking a large chapter would freeze the window.
    @Test
    void show_asksTheServiceOffTheFxThread() throws TimeoutException {
        projects.onSegments("u1", Result.ok(List.of(preview("a", 1, 1))));
        final StructureSegmentsViewModel viewModel = viewModel();

        show(viewModel, "u1");
        awaitLoaded(viewModel);

        assertThat(projects.segmentCallsOnFxThread()).containsExactly(false);
    }

    // IF a slow listing of the chapter the person left overwrote the newer one, THEN the list would show another
    // chapter's text under the chapter that is picked.
    @Test
    void show_olderListingAnswersAfterANewerPick_isDropped() throws TimeoutException {
        projects.onSegments("slow", Result.ok(List.of(preview("old", 1, 1))));
        projects.onSegments("fast", Result.ok(List.of(preview("new", 1, 1))));
        projects.holdSegments("slow");
        final StructureSegmentsViewModel viewModel = viewModel();
        show(viewModel, "slow");
        show(viewModel, "fast");
        awaitLoaded(viewModel);

        projects.releaseSegments("slow");
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(300, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();

        final StructureSegments.Loaded loaded = (StructureSegments.Loaded) stateOf(viewModel);
        assertThat(loaded.rows().stream().map(row -> row.preview().segmentId()).toList())
                .containsExactly("new");
    }

    // IF a failed listing showed an empty list, THEN a person would read "no segments" for "could not be read".
    @Test
    void show_serviceAnswersAnError_publishesFailedWithItsCode() throws TimeoutException {
        projects.onSegments("u1", Result.err(AppError.of(ErrorCode.validation, "Unknown", "No such unit.")));
        final StructureSegmentsViewModel viewModel = viewModel();

        show(viewModel, "u1");
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS, TimeUnit.SECONDS, () -> stateOf(viewModel) instanceof StructureSegments.Failed);

        assertThat(stateOf(viewModel)).isEqualTo(new StructureSegments.Failed(ErrorCode.validation));
    }

    // IF a node with no unit still asked the service, THEN a grouping row would list another unit's text.
    @Test
    void show_noUnits_isIdleAndAsksNothing() {
        final StructureSegmentsViewModel viewModel = viewModel();

        show(viewModel);

        assertThat(stateOf(viewModel)).isInstanceOf(StructureSegments.Idle.class);
        assertThat(projects.segmentCalls()).isEmpty();
    }

    // IF a brief change left the old chunking on show, THEN the badges would disagree with what the run does.
    @Test
    void refresh_afterAShow_asksAgainForTheSameUnits() throws TimeoutException {
        projects.onSegments("u1", Result.ok(List.of(preview("a", 1, 1))));
        final StructureSegmentsViewModel viewModel = viewModel();
        show(viewModel, "u1");
        awaitLoaded(viewModel);
        projects.onSegments("u1", Result.ok(List.of(preview("a", 1, 2), preview("b", 2, 2))));

        onFx(() -> {
            viewModel.refresh();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> stateOf(viewModel) instanceof StructureSegments.Loaded loaded && loaded.chunks() == 2);

        assertThat(projects.segmentCalls()).containsExactly("p1/u1", "p1/u1");
    }
}
