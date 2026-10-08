package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * A Manual run whose review pause is answered through the review panel's view model, over the real review desk and the
 * in-memory repositories of the booted injector; only the model is a hand-written fake.
 *
 * <p>The pseudo model cannot show a pause on an ACCEPTED segment (its upper-cased echo of these sources is FLAGGED in
 * every mode), and the scripted review desk of the {@code :ui} tests cannot show that its answer matches the real
 * one. Each reply is Cyrillic and 0.97 of its source's length, so every soft check passes and the confidence is 1.0,
 * above Manual's threshold of 0.85.
 *
 * <p>Infrastructure: it proves an integration, not a product requirement.
 */
class ReviewPauseDeskIntegrationTest {

    private static final long WAIT_SECONDS = 30;
    private static final String FIRST_TARGET = "Маяк стояв на прибережній скелі.";
    private static final String SECOND_TARGET = "Дощ ущух лише пізно надвечір.";
    private static final String SOURCE_TEXT = "The lighthouse stood on the rock.\n\nThe rain stopped only at dusk.\n";

    @TempDir
    private Path dataDir;

    @TempDir
    private Path booksDir;

    private final FakeModel model =
            new FakeModel("{\"target\":\"" + FIRST_TARGET + "\"}", "{\"target\":\"" + SECOND_TARGET + "\"}");
    private Injector injector;

    @BeforeEach
    void startWorkspace() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(
                        name -> "BOOKLOOM_REVIEW_MODE".equals(name) ? "manual" : null, name -> null));
        final Stage stage = FxToolkit.registerPrimaryStage();
        injector = Guice.createInjector(Modules.override(new CoreModules(startup), new UiModule())
                .with(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ChatModelFactory.class).toInstance(selection -> Result.ok(model));
                    }
                }));
        final AppShellView shell = injector.getInstance(AppShellView.class);
        onFx(() -> {
            final Scene scene = shell.createScene(1280, 800);
            stage.setScene(scene);
            stage.show();
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        FxToolkit.cleanupStages();
        WorkspaceTestBase.shutDown(injector);
    }

    // IF the fake's answer to Accept & continue differed from the real desk's, THEN the :ui tests would prove nothing.
    @Test
    void acceptAndContinue_manualPauseAfterAcceptedSegment_confirmsItAndTheRunDraftsTheNextOne() throws Exception {
        openBookAndStartManualRun();
        final String first = pausedSegment(null);
        final SegmentView beforeAccept = viewOf(first);

        onFx(() -> injector.getInstance(ReviewViewModel.class).accept());
        final String second = pausedSegment(first);
        final SegmentView afterAccept = viewOf(first);
        final SegmentView secondView = viewOf(second);

        assertThat(beforeAccept.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(beforeAccept.reviewed()).isFalse();
        assertThat(afterAccept.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(afterAccept.reviewed()).isTrue();
        assertThat(afterAccept.maskedMachineTarget()).isEqualTo(FIRST_TARGET);
        assertThat(secondView.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(secondView.maskedMachineTarget()).isEqualTo(SECOND_TARGET);
        assertThat(model.requests()).hasSize(2);
    }

    private void openBookAndStartManualRun() throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Lighthouse.txt"), SOURCE_TEXT, StandardCharsets.UTF_8);
        onFx(() -> injector.getInstance(ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(CurrentProject.class).book().get() != null);
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        onFx(() -> {
            brief.setSourceLanguage("en");
            brief.setTargetLanguage("uk");
            brief.setDial(QualityDial.FAST);
        });
        waitUntil(() -> !brief.saving().get());
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("fake"));
        // Translating opens only once a book with usable languages is open.
        onFx(() -> injector.getInstance(AppShellView.class).activate(ViewNames.TRANSLATING));
        onFx(() -> injector.getInstance(TranslatingViewModel.class).start());
    }

    /** Waits for a review pause on a segment other than {@code previous} and for the panel to have it selected. */
    private String pausedSegment(final @Nullable String previous) throws Exception {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        final ReviewViewModel review = injector.getInstance(ReviewViewModel.class);
        waitUntil(() -> {
            final String paused = mirror.review().reviewPauseSegment().get();
            final SegmentView selected = review.selected().get();
            return paused != null
                    && !paused.equals(previous)
                    && selected != null
                    && selected.segmentId().equals(paused)
                    && review.acceptAvailable().get();
        });
        return onFx(() -> mirror.review().reviewPauseSegment().get());
    }

    private SegmentView viewOf(final String segmentId) {
        final OpenedBook opened =
                onFx(() -> injector.getInstance(CurrentProject.class).book().get());
        final Result<SegmentView> view = injector.getInstance(ReviewDesk.class)
                .segment(Objects.requireNonNull(opened).projectId(), segmentId);
        return Objects.requireNonNull(view.data(), () -> "no view: " + view.error());
    }

    private static <T> T onFx(final Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("the action on the FX thread did not complete", failure);
        }
    }

    private static void onFx(final Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    private static void waitUntil(final Supplier<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::get));
    }

    /** A model that answers the replies it was given, in order, and remembers what it was asked. */
    private static final class FakeModel implements ChatModel {

        private final Queue<String> replies = new ConcurrentLinkedQueue<>();
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        FakeModel(final String... replies) {
            this.replies.addAll(List.of(replies));
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            return Result.ok(new ChatResponse(replies.remove(), FinishReason.STOP));
        }

        List<ChatRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
