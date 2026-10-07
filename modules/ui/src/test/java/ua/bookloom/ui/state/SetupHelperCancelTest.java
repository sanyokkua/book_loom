package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.SetupAssistant;

/** Stopping a model proposal interrupts the call it waits on and still answers the caller once, as cancelled. */
class SetupHelperCancelTest extends ExportViewModelTestBase {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopWorker() {
        worker.shutdownNow();
    }

    // IF Stop only hid the card, THEN the model call would go on holding the single-flight gate behind it.
    @Test
    void stop_whileTheAssistantWaits_interruptsItAndAnswersCancelledOnTheFxThread() throws Exception {
        final CountDownLatch waiting = new CountDownLatch(1);
        final AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        final SetupAssistant blocking = new BlockingAssistant(waiting, interrupted);
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });
        final SetupHelper helper = new SetupHelper(blocking, models, settings, worker, activities);
        final AtomicReference<Result<FileNameSuggestion>> answer = new AtomicReference<>();
        onFx(() -> helper.run(ActivityKind.SUGGEST_NAME, model -> blocking.suggestFileName("p", model), answer::set));
        assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue();
        final long id = onFx(() -> activities.running().getFirst().id());

        onFx(() -> {
            activities.stop(id);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> answer.get() != null);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(interrupted.get()).isTrue();
        assertThat(answer.get())
                .extracting(Result::error)
                .extracting(AppError::code)
                .isEqualTo(ErrorCode.cancelled);
        assertThat(onFx(() -> activities.running().isEmpty())).isTrue();
    }

    /** Sleeps in a file name suggestion until interrupted, then answers cancelled as the provider client does. */
    private record BlockingAssistant(CountDownLatch waiting, AtomicReference<Boolean> interrupted)
            implements SetupAssistant {

        @Override
        public Result<FileNameSuggestion> suggestFileName(final String projectId, final ChatModel model) {
            waiting.countDown();
            try {
                TimeUnit.SECONDS.sleep(10);
            } catch (InterruptedException stopped) {
                interrupted.set(true);
                return Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "Interrupted."));
            }
            return Result.err(AppError.of(ErrorCode.internal, "Late", "Not interrupted."));
        }

        @Override
        public Result<BriefSuggestion> suggestBrief(final String projectId, final ChatModel model) {
            throw new UnsupportedOperationException("not asked");
        }
    }
}
