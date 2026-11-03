package ua.bookloom.ui;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.BriefChoices;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.ui.ConformanceCases.Overlay;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.control.ProgressDialog;
import ua.bookloom.ui.dialog.ExportCompleteDialog;
import ua.bookloom.ui.dialog.OpenAnotherBookPrompt;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.ChangeKind;
import ua.bookloom.ui.state.ChangeOperation;
import ua.bookloom.ui.state.ChangeOrigin;
import ua.bookloom.ui.state.ChangeResults;
import ua.bookloom.ui.state.ChangeRow;
import ua.bookloom.ui.state.ChangeSection;
import ua.bookloom.ui.state.EditableTarget;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.ResultsView;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.theme.ThemeController;

/**
 * Shows one {@link ConformanceCases.Screen} in the real shell, for every test that reads a screen's resolved look.
 *
 * <p>The cases of one class read the same screen many times in a row — part after part, block after block — and
 * none of them changes it, so a case that shows the screen the previous case of the same class showed reads that
 * shell instead of building the graph and preparing the screen again (which was most of these suites' time). A screen
 * with a dialog over it is always rebuilt: TestFX hides every window after each test.
 */
abstract class ConformanceTestBase extends ShellTestBase {

    // A long stored project name (pair and quality appended), so the open-another question is fitted with one.
    private static final String LONG_PROJECT_NAME = "Earth Gravity · EN → UK · 26b Max";

    private static @Nullable Showing lastShown;

    private final ScriptedProjectService projects = new ScriptedProjectService();
    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();

    private @Nullable Stage stage;
    private boolean adopted;

    /** The shell a case left showing, with the class and screen it was built for. */
    private record Showing(
            Class<?> owner,
            Screen screen,
            Locale locale,
            Injector injector,
            AppShellView shell,
            Navigator navigator,
            ThemeController themeController,
            Scene scene) {}

    @Override
    public void start(final Stage primary) {
        stage = primary;
        final Showing previous = lastShown;
        adopted = previous != null && previous.owner() == getClass();
        if (previous == null || !adopted) {
            super.start(primary);
            return;
        }
        injector = previous.injector();
        shell = previous.shell();
        navigator = previous.navigator();
        themeController = previous.themeController();
        scene = previous.scene();
        primary.setScene(scene);
        primary.show();
        primary.sizeToScene();
    }

    /** A model whose name carries a quant the scripted port judges good, for the ready card's chip. */
    static final String QUANT_MODEL = "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL";

    private final ScriptedModelCapabilities capabilities = quantCapabilities();

    private static ScriptedModelCapabilities quantCapabilities() {
        final ScriptedModelCapabilities scripted = ScriptedModelCapabilities.idle();
        scripted.nameQuant(QUANT_MODEL, "UD-Q4_K_XL", ua.bookloom.api.llm.QuantTier.GOOD);
        return scripted;
    }

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .glossary(glossary)
                .capabilities(capabilities)
                .languages(new BundledChoices())
                .build();
    }

    protected final void show(final Screen screen) throws TimeoutException {
        show(screen, Locale.ENGLISH);
    }

    /** Shows the screen in another display language; the shell the previous case left is reused only for the same one. */
    protected final void show(final Screen screen, final Locale locale) throws TimeoutException {
        final Showing previous = lastShown;
        if (adopted
                && previous != null
                && previous.screen().equals(screen)
                && previous.locale().equals(locale)
                && screen.overlay() == Overlay.NONE) {
            return;
        }
        if (adopted) {
            final Stage primary = Objects.requireNonNull(stage, "stage");
            interact(() -> super.start(primary));
        }
        lastShown = null;
        if (!Locale.ENGLISH.equals(locale)) {
            useLocale(locale);
        }
        prepare(screen);
        lastShown = new Showing(getClass(), screen, locale, injector, shell, navigator, themeController, scene);
    }

    private void prepare(final Screen screen) throws TimeoutException {
        final ViewNames view = screen.view();
        if (view != null) {
            onFx(() -> shell.activate(view));
        }
        new ConformancePreparations(injector, projects, glossary).prepare(screen.preparation());
        switch (screen.overlay()) {
            case NONE -> {
                // nothing is open over the shell
            }
            case ABOUT -> onFx(((Button) required("shell-about"))::fire);
            case ERROR_DIALOG ->
                onFx(() -> injector.getInstance(ErrorPresenter.class)
                        .present(AppError.of(
                                ErrorCode.timeout, "Translation failed", "The provider did not answer in time.")));
            case REVIEW_DESK -> onFx(((Button) required("translating-review-flagged"))::fire);
            case ADD_TERM -> onFx(((Button) required("names-style-add"))::fire);
            case RESULTS -> onFx(this::showSampleResults);
            case EXPORT_COMPLETE ->
                onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(finishedExport()));
            case DELETE_PROVIDER -> new SettingsPreparations(injector).askToDelete();
            case UNSAVED_PROVIDER -> new SettingsPreparations(injector).askAboutUnsaved();
            case TEST_DIALOG -> new SettingsPreparations(injector).openTheTestDialog();
            case SWITCH_PROVIDER -> new SettingsPreparations(injector).askAboutSwitching();
            case REPLACE_RUN ->
                onFx(() -> injector.getInstance(OpenAnotherBookPrompt.class)
                        .ask(LONG_PROJECT_NAME, "Dracula.epub", RunState.RUNNING, () -> {}, () -> {}));
        }
    }

    private void showSampleResults() {
        final ModalHost host = injector.getInstance(ModalHost.class);
        final ResultsView view = new ResultsView(sampleResults(), () -> {}, host::hide);
        host.show(ProgressDialog.results(view, injector.getInstance(Messages.class)), false);
    }

    // A review that removed one name, rewrote another and gave a reason, so the Why column is checked too.
    private static ChangeResults sampleResults() {
        return new ChangeResults(
                ChangeOperation.NAME_REVIEW,
                false,
                List.of(
                        sampleItem(0, ChangeKind.REMOVED, "Hale", "—", "not a name"),
                        sampleItem(1, ChangeKind.CHANGED, "Well", "No target · character", null)),
                LiveCalls.EMPTY,
                List.of(new ChangeRow(
                        2,
                        ChangeKind.UNTARGETED,
                        "Mid",
                        "No target · character",
                        "—",
                        null,
                        ChangeOrigin.MODEL,
                        ChangeSection.NAMES,
                        new EditableTarget("e3", "", "character"))));
    }

    private static ChangeResults.Item sampleItem(
            final long id, final ChangeKind kind, final String term, final String after, final @Nullable String why) {
        return new ChangeResults.Item(
                new ChangeRow(id, kind, term, "No target · other", after, why, ChangeOrigin.MODEL, ChangeSection.NAMES),
                done -> done.accept(true));
    }

    // A partial export, so the card's untranslated-segments line is checked in both themes with the rest.
    private static ExportOutcome finishedExport() {
        final Path book = Path.of("/books/Frankenstein.uk.epub");
        return new ExportOutcome(
                new ExportReport(book, 10, 3, 0, 0, 8, 2, List.of(), 10, ConsistencySummary.NOT_RUN, 0), 958_464);
    }

    /**
     * Every language counts as having tested rules, as in the default graph, and a target's Book Brief rows (apostrophe,
     * quotation marks, address, declared conventions) come from the bundled language files, so the brief is checked with
     * the rows a Ukrainian target shows.
     */
    private static final class BundledChoices implements LanguageSupport {

        private static final LanguageSupport FILES = LanguageRules.bundled();

        @Override
        public boolean hasTestedRules(final String languageTag) {
            return true;
        }

        @Override
        public BriefChoices briefChoices(final String targetTag) {
            return FILES.briefChoices(targetTag);
        }
    }
}
