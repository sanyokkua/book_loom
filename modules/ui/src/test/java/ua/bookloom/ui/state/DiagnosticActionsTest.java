package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.DiagnosticLogs;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingFileChooser;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.RecordingToasts.Raised;
import ua.bookloom.ui.SessionInfo;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The About dialog's diagnostic actions: the log folder opened in the file manager, its path copied, and a bundle
 * written where the person chose — off the FX thread, its outcome reported back on it. Everything stays local.
 */
class DiagnosticActionsTest extends FxTestBase {

    @TempDir
    private Path tempDir;

    private final RecordingFileRevealer revealer = new RecordingFileRevealer();
    private final RecordingFileChooser chooser = new RecordingFileChooser();
    private final RecordingToasts toasts = new RecordingToasts();
    private final RecordingErrorPresenter errors = new RecordingErrorPresenter();
    private final List<String> clipboard = new CopyOnWriteArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final SessionInfo session = new SessionInfo();
    private Path logs;
    private DiagnosticActions actions;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need: the outcome of a bundle comes back through Platform.runLater.
    }

    @BeforeEach
    void createActions() throws IOException {
        logs = Files.createDirectory(tempDir.resolve("logs"));
        Files.writeString(logs.resolve("bookloom-trace.log"), "trace\n");
        clock.advance(Duration.ofHours(9).plusMinutes(5).plusSeconds(7));
        actions = new DiagnosticActions(
                DiagnosticLogs.of(logs, true),
                session,
                revealer,
                chooser,
                toasts,
                errors,
                "1.2.3",
                new DirectExecutor(),
                clock,
                clipboard::add);
    }

    @Test
    void openFolder_asksTheFileManagerToOpenTheLogFolder() {
        actions.openFolder();

        assertThat(revealer.opened()).containsExactly(logs);
    }

    @Test
    void copyPath_putsTheLogFolderOnTheClipboardAndSaysSo() {
        actions.copyPath();

        assertThat(clipboard).containsExactly(logs.toString());
        assertThat(toasts.feedback()).containsExactly(new Raised("info", MessageKey.ABOUT_LOG_PATH_COPIED, List.of()));
        assertThat(toasts.raised()).isEmpty();
    }

    @Test
    void saveBundle_dialogCancelled_writesNothingAndSaysNothing() {
        chooser.answer(null);

        actions.saveBundle();

        assertThat(chooser.initials())
                .singleElement()
                .satisfies(initial -> assertThat(initial.getFileName().toString())
                        .isEqualTo("bookloom-diagnostics-20260101-090507.zip"));
        assertThat(toasts.raised()).isEmpty();
        assertThat(toasts.feedback()).isEmpty();
    }

    @Test
    void saveBundle_fileChosen_writesTheZipAndReportsItsName() {
        final Path target = tempDir.resolve("report.zip");
        chooser.answer(target);
        session.update(Map.of("book", "Kobzar.fb2"));

        actions.saveBundle();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(target).isRegularFile();
        assertThat(toasts.feedback())
                .containsExactly(new Raised("success", MessageKey.ABOUT_BUNDLE_SAVED, List.of("report.zip")));
        assertThat(toasts.raised()).isEmpty();
        assertThat(actions.bundleSavedProperty().get()).isEqualTo("report.zip");
        assertThat(errors.presented()).isEmpty();
    }

    @Test
    void saveBundle_unwritableTarget_showsTheError() {
        chooser.answer(tempDir.resolve("missing-folder").resolve("report.zip"));

        actions.saveBundle();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(errors.presented()).singleElement().extracting("title").isEqualTo("Diagnostic bundle not saved");
        assertThat(toasts.raised()).isEmpty();
    }
}
