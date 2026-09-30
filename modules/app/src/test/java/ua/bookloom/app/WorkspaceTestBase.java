package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.util.Modules;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The shared support of the workspace end-to-end tests: the real injector with the one substituted provider registry,
 * the window over it, and the steps that take a generated book from import to the end of its run.
 *
 * <p>The review mode is bound the way the launcher binds it: through {@link ReviewModeResolver}, into the
 * {@link StartupContext} the core modules read.
 */
@SuppressWarnings("NullAway.Init") // set by startWorkspace, which every test calls first
abstract class WorkspaceTestBase {

    protected static final long WAIT_SECONDS = 30;
    protected static final Set<RunState> TERMINAL_STATES =
            EnumSet.of(RunState.COMPLETED, RunState.FAILED, RunState.STOPPED);
    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 800;
    private static final String PAUSE_LOGGER = "ua.bookloom.pipeline.JobPauseLogger";

    @TempDir
    protected Path dataDir;

    @TempDir
    protected Path booksDir;

    protected Injector injector;
    protected AppShellView shell;
    private final ListAppender<ILoggingEvent> pauseLog = new ListAppender<>();

    /** Boots the injector for {@code mode} and shows the window on the Import screen. */
    protected void startWorkspace(final ReviewMode mode) throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final String flag = mode.name().toLowerCase(Locale.ROOT);
        final StartupContext startup = new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> "BOOKLOOM_REVIEW_MODE".equals(name) ? flag : null, name -> null));
        final Stage stage = FxToolkit.registerPrimaryStage();
        // Built here, not through BookLoomApplication.init(): that hard-codes its modules, and this test needs to
        // replace one. It therefore skips AppLifecycle's two phases, which hold nothing yet; when persistence fills
        // phase two this test must run it too.
        injector = Guice.createInjector(
                Modules.override(new CoreModules(startup), new UiModule()).with(new PseudoOnlyModule()));
        shell = injector.getInstance(AppShellView.class);
        watchPauses();
        onFx(() -> {
            final Scene scene = shell.createScene(WINDOW_WIDTH, WINDOW_HEIGHT);
            stage.setScene(scene);
            stage.show();
            shell.activate(ViewNames.IMPORT);
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PAUSE_LOGGER)).detachAppender(pauseLog);
        pauseLog.stop();
        FxToolkit.cleanupStages();
        shutDown(injector);
    }

    /**
     * Stops the background executor and absorbs what stopping it does on the FX thread. Cancelling a paused run changes
     * its state, and the view model's refresh of the pending count is then rejected by the very executor being shut
     * down; TestFX would otherwise hand that exception to whichever test next touches the FX thread.
     */
    static void shutDown(final @Nullable Injector built) {
        Optional.ofNullable(built)
                .map(injector -> injector.getInstance(Key.get(ExecutorService.class, BackgroundExecutor.class)))
                .ifPresent(ExecutorService::shutdownNow);
        WaitForAsyncUtils.waitForFxEvents();
        try {
            WaitForAsyncUtils.checkException();
        } catch (Throwable expected) {
            // The rejected refresh described above, from a run this test had already finished with.
        }
    }

    /** The messages the engine logged for each review pause of this run, in order. */
    protected List<String> pauseMessages() {
        return pauseLog.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void watchPauses() {
        final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PAUSE_LOGGER);
        logger.setLevel(Level.INFO);
        pauseLog.start();
        logger.addAppender(pauseLog);
    }

    protected void openBook(final Path book, final int segments) throws Exception {
        onFx(() -> injector.getInstance(ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(CurrentProject.class).book().get() != null);
        final OpenedBook opened =
                onFx(() -> injector.getInstance(CurrentProject.class).book().get());
        assertThat(opened).isNotNull();
        assertThat(opened.projectId()).isNotBlank();
        assertThat(opened.source()).isEqualTo(book);
        assertThat(opened.profile()).isNotNull();
        assertThat(Objects.requireNonNull(opened.profile()).stats().segments()).isEqualTo(segments);
    }

    /**
     * Chooses the source the TXT does not declare and a target, checks the proposed destination follows the target,
     * then replaces it with the person's own path.
     */
    protected void chooseBrief(final String language, final Path proposed, final Path chosen) throws Exception {
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        final ExportViewModel exports = injector.getInstance(ExportViewModel.class);
        assertThat(onFx(() -> brief.sourceUndeclared().get())).isTrue();
        assertThat(onFx(() -> brief.canContinue().get())).isFalse();
        onFx(() -> brief.setSourceLanguage("en"));
        onFx(() -> brief.setTargetLanguage(language));
        assertThat(onFx(() -> brief.canContinue().get())).isTrue();
        // The run reads the stored brief, so the saves the two choices started must have reached the project.
        waitUntil(() -> !brief.saving().get());
        assertThat(onFx(() -> exports.destination().get())).isEqualTo(proposed.toString());
        onFx(() -> exports.editDestination(chosen.toString()));
        assertThat(onFx(() -> exports.interimExport()))
                .hasValueSatisfying(request -> assertThat(request.destination()).isEqualTo(chosen));
    }

    protected void chooseModel() {
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("uppercase"));
        assertThat(onFx(() -> injector.getInstance(SettingsViewModel.class).selection()))
                .contains(new ModelSelection("pseudo", "uppercase"));
    }

    /** Starts the run and waits for it to end, or for the reason it could not start, so a failure names its cause. */
    protected void runToTheEnd() throws Exception {
        startRun();
        awaitCompletion();
    }

    protected void startRun() {
        onFx(() -> injector.getInstance(TranslatingViewModel.class).start());
    }

    protected void awaitCompletion() throws Exception {
        waitUntil(() -> TERMINAL_STATES.contains(runState()) || refusal() != null);
        assertThat(runState())
                .as(
                        "the run's failure: %s; a refused start: %s",
                        onFx(() -> failureOf(injector.getInstance(StateMirror.class))), refusal())
                .isEqualTo(RunState.COMPLETED);
    }

    protected void assertExportReport(final Path written, final int accepted, final int flagged) {
        onFx(() -> shell.activate(ViewNames.EXPORT));
        assertThat(exportText("#export-path .kv-value")).isEqualTo(written.toString());
        assertThat(exportText("#export-count-accepted")).isEqualTo(String.valueOf(accepted));
        assertThat(exportText("#export-count-flagged")).isEqualTo(String.valueOf(flagged));
        assertThat(onFx(() -> exportNode("#export-reveal")))
                .as("the reveal action is offered for the written file (never pressed here: it opens a file browser)")
                .isNotNull();
        assertThat(onFx(() -> exportNode("#export-empty"))).isNull();
    }

    protected void assertReopensWithSegments(final Path written, final int expectedSegments) {
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final Result<Document> reopened = documents.open(written);
        assertThat(reopened.isOk())
                .as("the written book opens again: %s", reopened.error())
                .isTrue();
        assertThat(segmentCount(Objects.requireNonNull(reopened.data()))).isEqualTo(expectedSegments);
        assertThat(documents.close(reopened.data()).isOk()).isTrue();
    }

    protected static int segmentCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    protected static String failureOf(final StateMirror mirror) {
        return Optional.ofNullable(mirror.failure().get())
                .map(AppError::toString)
                .orElse("none");
    }

    protected @Nullable RunNotice refusal() {
        return onFx(
                () -> injector.getInstance(TranslatingViewModel.class).notice().get());
    }

    protected RunState runState() {
        return onFx(() -> injector.getInstance(StateMirror.class).runState().get());
    }

    protected String exportText(final String selector) {
        return onFx(() -> ((Label) exportNode(selector)).getText());
    }

    protected Node exportNode(final String selector) {
        return FxToolkit.toolkitContext().getRegisteredStage().getScene().lookup(selector);
    }

    protected static <T> T onFx(final Callable<T> action) {
        try {
            return WaitForAsyncUtils.asyncFx(action).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("the action on the FX thread did not complete", failure);
        }
    }

    protected static void onFx(final Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
    }

    protected static void waitUntil(final Supplier<Boolean> condition) throws Exception {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::get));
    }

    /** The one substitution: the provider registry lists only {@code pseudo}, so the settings viewmodel selects it. */
    private static final class PseudoOnlyModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ProviderConfigs.class).toInstance(new PseudoOnlyProviderConfigs());
        }
    }

    /** A registry of exactly one provider, {@code pseudo}; its address is never contacted. */
    private static final class PseudoOnlyProviderConfigs implements ProviderConfigs {

        private final ProviderConfig pseudo = new ProviderConfig(
                "pseudo",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

        @Override
        public Result<ProviderConfig> register(final ProviderConfig config) {
            Objects.requireNonNull(config, "config");
            return Result.err(AppError.of(ErrorCode.validation, "Not supported", "This registry is fixed."));
        }

        @Override
        public Optional<ProviderConfig> find(final String id) {
            Objects.requireNonNull(id, "id");
            return all().stream().filter(config -> config.id().equals(id)).findFirst();
        }

        @Override
        public List<ProviderConfig> all() {
            return List.of(pseudo);
        }
    }
}
