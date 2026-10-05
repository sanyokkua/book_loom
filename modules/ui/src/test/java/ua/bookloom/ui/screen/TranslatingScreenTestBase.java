package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import org.junit.jupiter.api.AfterEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ProgressFixtures;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedReviewDesk;
import ua.bookloom.ui.ScriptedTranslationEngine;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.RecordingJob;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StateMirror;

/**
 * What the translating screen tests share: a graph whose engine hands out one recording job, so a button pressed on
 * the screen can be seen to reach the job; a way to drive the screen through the state mirror, which is how a run
 * reaches it in the application; and lookups for the controls and the rendered log rows.
 *
 * <p>The mirror applies every publication one FX pulse later, so each driver here waits for the FX queue to drain
 * before it returns.
 */
abstract class TranslatingScreenTestBase extends ImportScreenTestBase {

    static final String MODEL = "gemma3:12b";
    static final Path BOOK = Path.of("Frankenstein.epub");
    static final List<String> BUTTON_IDS =
            List.of("translating-start", "translating-pause", "translating-resume", "translating-stop");

    final RecordingJob job = new RecordingJob();
    final ScriptedChatModelFactory models = ScriptedChatModelFactory.ok();
    final ScriptedReviewDesk desk = new ScriptedReviewDesk();
    final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
    final ScriptedLexiconService lexicon = new ScriptedLexiconService();
    final ScriptedTranslationEngine engine = ScriptedTranslationEngine.returning(job);
    private ReviewMode launchMode = ReviewMode.UNATTENDED;

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .models(models)
                .engine(engine)
                .glossary(glossary)
                .lexicon(lexicon)
                .reviewDesk(desk)
                .reviewMode(launchMode)
                .build();
    }

    /** Rebuilds the window from a graph launched with this review mode, as the launch flag would. */
    void launchWith(final ReviewMode mode) {
        launchMode = mode;
        useLocale(Locale.ENGLISH);
    }

    /** Lets a job that is still waiting for instructions end, so its thread does not outlive the test. */
    @AfterEach
    void releaseJob() {
        job.finish(Result.ok(cancelledReport()));
    }

    static JobReport cancelledReport() {
        return new JobReport(BookFormat.TXT, JobState.CANCELLED, 10, 3, 0, List.of(), null);
    }

    static JobReport completedReport() {
        return new JobReport(BookFormat.TXT, JobState.COMPLETED, 10, 10, 0, List.of(), null);
    }

    /** Shows the translating screen afresh, the way a person arrives from another screen. */
    void showTranslating() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
    }

    /** Opens the book and chooses a model, so that the start control has everything it needs. */
    void readyToStart() throws TimeoutException {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(SettingsViewModel.class).model().set(MODEL);
            return null;
        });
    }

    StateMirror mirror() {
        return injector.getInstance(StateMirror.class);
    }

    void publish(final RunState state) {
        mirror().publishRunState(state);
        WaitForAsyncUtils.waitForFxEvents();
    }

    void publishProgress(final int accepted, final int flagged, final int pending) {
        mirror().publishProgress(ProgressFixtures.progress(7, 11, accepted, flagged, pending));
        WaitForAsyncUtils.waitForFxEvents();
    }

    void publishLog(final LogEntry... entries) {
        mirror().publishLogEntries(List.of(entries));
        WaitForAsyncUtils.waitForFxEvents();
    }

    String labelText(final String id) {
        return ((Label) required(id)).getText();
    }

    ProgressBar progressBar() {
        return (ProgressBar) required("translating-progress");
    }

    /** The ids of the four run controls that a person can see and press. */
    List<String> enabledControls() {
        return BUTTON_IDS.stream()
                .filter(this::isShown)
                .filter(id -> !button(id).isDisabled())
                .toList();
    }

    /** The ids of the four run controls that a person can see but not press. */
    List<String> disabledControls() {
        return BUTTON_IDS.stream()
                .filter(this::isShown)
                .filter(id -> button(id).isDisabled())
                .toList();
    }

    /** The ids in a space-separated table cell; a dash stands for none. */
    static List<String> ids(final String cell) {
        return "-".equals(cell) ? List.of() : List.of(cell.trim().split("\\s+"));
    }

    @SuppressWarnings("unchecked")
    ListView<LogEntry> logList() {
        return (ListView<LogEntry>) required("translating-log");
    }

    /** The filled cells of the log the list actually rendered, in row order. */
    List<ListCell<LogEntry>> logCells() {
        WaitForAsyncUtils.waitForFxEvents();
        return allNodes(logList())
                .filter(node -> node instanceof ListCell<?>)
                .map(node -> {
                    @SuppressWarnings("unchecked")
                    final ListCell<LogEntry> cell = (ListCell<LogEntry>) node;
                    return cell;
                })
                .filter(cell -> cell.getItem() != null && cell.isVisible())
                .sorted(Comparator.comparingInt(ListCell::getIndex))
                .toList();
    }

    private static Stream<Node> allNodes(final Node root) {
        return Stream.concat(
                Stream.of(root),
                root instanceof javafx.scene.Parent parent
                        ? parent.getChildrenUnmodifiable().stream().flatMap(TranslatingScreenTestBase::allNodes)
                        : Stream.empty());
    }
}
