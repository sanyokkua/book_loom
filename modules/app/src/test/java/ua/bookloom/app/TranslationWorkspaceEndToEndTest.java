package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.util.Modules;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.AppShellView;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.RunNotice;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.TranslatingViewModel;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The translation workspace, driven from opening a book to the export report through the real injector.
 *
 * <p>Every screen has its own test against fakes; this is the one test that proves the parts are connected. The
 * viewmodels, the runner, the document port, the translation engine and the model factory are the production ones,
 * over a generated book in a temporary directory. The single substitute is the provider registry, which lists only
 * {@code pseudo}: the settings screen offers only the two local presets and {@code pseudo} is not one of them, so
 * this test supplies the model choice to the viewmodels directly rather than picking it in the window. The model
 * factory recognises {@code pseudo} itself, so nothing else is replaced.
 *
 * <p>Infrastructure: it proves an integration, not a product requirement.
 */
class TranslationWorkspaceEndToEndTest {

    private static final long WAIT_SECONDS = 30;
    private static final double WINDOW_WIDTH = 1280;
    private static final double WINDOW_HEIGHT = 800;
    private static final Set<RunState> TERMINAL_STATES =
            EnumSet.of(RunState.COMPLETED, RunState.FAILED, RunState.STOPPED);
    private static final String SOURCE_TEXT =
            "It was a dark night.\n\nThe rain fell on the old house.\n\nNobody came.\n";
    private static final String TRANSLATED_TEXT =
            "IT WAS A DARK NIGHT.\n\nTHE RAIN FELL ON THE OLD HOUSE.\n\nNOBODY CAME.\n";
    private static final int SEGMENT_COUNT = 3;

    @TempDir
    private Path dataDir;

    @TempDir
    private Path booksDir;

    private Injector injector;
    private AppShellView shell;

    @BeforeEach
    void startWorkspace() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        final StartupContext startup = new StartupContext(AppPaths.of(dataDir, logDir), AppEnvironment.DEV);
        final Stage stage = FxToolkit.registerPrimaryStage();
        // Built here, not through BookLoomApplication.init(): that hard-codes its modules, and this test needs to
        // replace one. It therefore skips AppLifecycle's two phases, which hold nothing yet; when persistence fills
        // phase two this test must run it too.
        injector = Guice.createInjector(
                Modules.override(new CoreModules(startup), new UiModule()).with(new PseudoOnlyModule()));
        shell = injector.getInstance(AppShellView.class);
        onFx(() -> {
            final Scene scene = shell.createScene(WINDOW_WIDTH, WINDOW_HEIGHT);
            stage.setScene(scene);
            stage.show();
            shell.activate(ViewNames.IMPORT);
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        FxToolkit.cleanupStages();
        Optional.ofNullable(injector)
                .map(built -> built.getInstance(Key.get(ExecutorService.class, BackgroundExecutor.class)))
                .ifPresent(ExecutorService::shutdownNow);
    }

    // IF any two of the parts (import, brief, settings, runner, engine, document port, export screen) were not
    // connected, THEN the book would not reach the export report as the written translation, at the chosen path.
    @Test
    void run_generatedTxtBookThroughTheWorkspace_writesTheBookAndReportsItOnTheExportScreen() throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Letter.txt"), SOURCE_TEXT, StandardCharsets.UTF_8);
        // Neither the proposed name (Letter.de.txt) nor the default language, so a brief that is ignored cannot pass.
        final Path chosen = booksDir.resolve("Brief-translated.txt");

        openBook(book);
        chooseBrief("de", booksDir.resolve("Letter.de.txt"), chosen);
        chooseModel();
        runToTheEnd();

        assertThat(onFx(() -> injector.getInstance(StateMirror.class).accepted().get()))
                .isEqualTo(SEGMENT_COUNT);
        assertThat(onFx(() -> injector.getInstance(StateMirror.class).flagged().get()))
                .isEqualTo(0);
        assertThat(onFx(() -> injector.getInstance(StateMirror.class).total().get()))
                .isEqualTo(SEGMENT_COUNT);
        assertThat(onFx(() ->
                        injector.getInstance(StateMirror.class).remaining().get()))
                .isEqualTo(0);
        assertExportReport(chosen);
        assertThat(Files.readString(chosen, StandardCharsets.UTF_8)).isEqualTo(TRANSLATED_TEXT);
        assertThat(booksDir.resolve("Letter.de.txt")).doesNotExist();
        assertReopensWithSegments(chosen, SEGMENT_COUNT);
    }

    private void openBook(final Path book) throws Exception {
        onFx(() -> injector.getInstance(ImportViewModel.class).open(book));
        waitUntil(() -> injector.getInstance(ImportViewModel.class).openedBook().get() != null);
        assertThat(onFx(() -> injector.getInstance(ImportViewModel.class)
                        .openedBook()
                        .get()
                        .source()))
                .isEqualTo(book);
        assertThat(onFx(() -> segmentCount(injector.getInstance(ImportViewModel.class)
                        .openedBook()
                        .get()
                        .document())))
                .isEqualTo(SEGMENT_COUNT);
    }

    /** Picks a language, checks the proposed destination follows it, then replaces it with the person's own path. */
    private void chooseBrief(final String language, final Path proposed, final Path chosen) {
        onFx(() -> injector.getInstance(BookBriefViewModel.class).selectTarget(language));
        assertThat(onFx(() -> injector.getInstance(BookBriefViewModel.class)
                        .destination()
                        .get()))
                .isEqualTo(proposed.toString());
        onFx(() -> injector.getInstance(BookBriefViewModel.class).editDestination(chosen.toString()));
        assertThat(onFx(() -> injector.getInstance(BookBriefViewModel.class).request()))
                .hasValueSatisfying(request -> assertThat(request)
                        .extracting(TranslationRequest::destination, TranslationRequest::targetLanguage)
                        .containsExactly(chosen, language));
    }

    private void chooseModel() {
        onFx(() -> injector.getInstance(SettingsViewModel.class).model().set("uppercase"));
        assertThat(onFx(() -> injector.getInstance(SettingsViewModel.class).selection()))
                .contains(new ModelSelection("pseudo", "uppercase"));
    }

    /** Starts the run and waits for it to end, or for the reason it could not start, so a failure names its cause. */
    private void runToTheEnd() throws Exception {
        onFx(() -> injector.getInstance(TranslatingViewModel.class).start());
        waitUntil(() -> TERMINAL_STATES.contains(runState()) || refusal() != null);
        assertThat(runState())
                .as(
                        "the run's failure: %s; a refused start: %s",
                        onFx(() -> failureOf(injector.getInstance(StateMirror.class))), refusal())
                .isEqualTo(RunState.COMPLETED);
    }

    private void assertExportReport(final Path written) {
        onFx(() -> shell.activate(ViewNames.EXPORT));
        assertThat(exportText("#export-path .kv-value")).isEqualTo(written.toString());
        assertThat(exportText("#export-count-accepted")).isEqualTo("3");
        assertThat(exportText("#export-count-flagged")).isEqualTo("0");
        assertThat(onFx(() -> exportNode("#export-reveal")))
                .as("the reveal action is offered for the written file (never pressed here: it opens a file browser)")
                .isNotNull();
        assertThat(onFx(() -> exportNode("#export-empty"))).isNull();
    }

    private void assertReopensWithSegments(final Path written, final int expectedSegments) {
        final DocumentPort documents = injector.getInstance(DocumentPort.class);
        final Result<Document> reopened = documents.open(written);
        assertThat(reopened.isOk())
                .as("the written book opens again: %s", reopened.error())
                .isTrue();
        assertThat(segmentCount(Objects.requireNonNull(reopened.data()))).isEqualTo(expectedSegments);
        assertThat(documents.close(reopened.data()).isOk()).isTrue();
    }

    private static int segmentCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static String failureOf(final StateMirror mirror) {
        return Optional.ofNullable(mirror.failure().get())
                .map(AppError::toString)
                .orElse("none");
    }

    private @Nullable RunNotice refusal() {
        return onFx(
                () -> injector.getInstance(TranslatingViewModel.class).notice().get());
    }

    private RunState runState() {
        return onFx(() -> injector.getInstance(StateMirror.class).runState().get());
    }

    private String exportText(final String selector) {
        return onFx(() -> ((Label) exportNode(selector)).getText());
    }

    private Node exportNode(final String selector) {
        return FxToolkit.toolkitContext().getRegisteredStage().getScene().lookup(selector);
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
