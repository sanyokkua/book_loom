package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.RecordingToasts;
import ua.bookloom.ui.ScriptedDocumentPort;

/**
 * What the book-brief view model tests share: a real {@link ImportViewModel} over the scripted document port and a
 * calling-thread executor, through which books are opened for real, and helpers that read and drive the brief view
 * model on the FX thread. The existence check runs on a calling-thread executor and its answer is published with
 * {@code Platform.runLater}, so every driver waits for the FX queue to drain. The toolkit runs so that the open's answer is published to the FX thread as it is in the
 * application.
 */
abstract class BookBriefViewModelTestBase extends ApplicationTest {

    ScriptedDocumentPort port;
    ImportViewModel imports;
    BookBriefViewModel brief;

    @Override
    public final void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    final void setUpViewModels() {
        port = ScriptedDocumentPort.idle();
        imports = onFx(() ->
                new ImportViewModel(port, new RecordingToasts(), new RecordingErrorPresenter(), new DirectExecutor()));
        brief = onFx(() -> new BookBriefViewModel(imports, new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Replaces the brief view model with a new one over the same import view model, as a later first visit would. */
    void recreateBrief() {
        brief = onFx(() -> new BookBriefViewModel(imports, new DirectExecutor()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Opens {@code document} as if it had been read from {@code source} and waits for the answer to be published. */
    void openBook(final Path source, final Document document) {
        port.on(source, Result.ok(document));
        onFx(() -> {
            imports.open(source);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void selectTarget(final String code) {
        onFx(() -> {
            brief.selectTarget(code);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void editDestination(final String text) {
        onFx(() -> {
            brief.editDestination(text);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void chooseDestination(final Path chosen) {
        onFx(() -> {
            brief.chooseDestination(chosen);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void setOverwrite(final boolean overwrite) {
        onFx(() -> {
            brief.setOverwrite(overwrite);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    void refresh() {
        onFx(() -> {
            brief.refresh();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    String destination() {
        return onFx(() -> brief.destination().get());
    }

    String target() {
        return onFx(() -> brief.targetLanguage().get());
    }

    boolean overwrite() {
        return onFx(() -> brief.overwrite().get());
    }

    boolean destinationExists() {
        return onFx(() -> brief.destinationExists().get());
    }

    Optional<String> sourceLanguage() {
        return onFx(brief::sourceLanguage);
    }

    Optional<TranslationRequest> request() {
        return onFx(brief::request);
    }
}
