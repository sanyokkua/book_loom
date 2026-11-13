package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.Messages;

/** What pressing Export book does: the request it builds, the work it moves off the FX thread and the outcome it keeps. */
class ExportViewModelExportTest extends ExportViewModelTestBase {

    @TempDir
    private Path dir;

    private Path destination;
    private ExecutorService worker;

    @BeforeEach
    void openTheBook() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        destination = dir.resolve("Frankenstein.uk.epub");
        exportService.writeFiles(true);
        worker = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopTheWorker() {
        worker.shutdownNow();
    }

    private void export() {
        onFx(() -> {
            exports.export();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitOutcome() throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                5, TimeUnit.SECONDS, () -> onFx(() -> exports.outcome().get() != null));
    }

    private void useWorker() {
        exports = onFx(() -> newExports(worker));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void export_success_publishesTheOutcomeWithItsSizeAndMarksTheStepDone() {
        export();

        final ExportOutcome outcome = onFx(() -> exports.outcome().get());
        assertThat(outcome.report().destination()).isEqualTo(destination);
        assertThat(outcome.sizeBytes()).isEqualTo(1);
        assertThat(onFx(() -> progress.done())).contains(ViewNames.EXPORT);
        assertThat(onFx(() -> exports.failure().get())).isEmpty();
    }

    @Test
    void export_default_buildsTheRequestFromTheChoices() {
        export();

        assertThat(exportService.requests())
                .containsExactly(new ExportRequest(
                        "p1", destination, false, Set.of(SideFile.GLOSSARY_CSV, SideFile.QUALITY_REPORT)));
    }

    // IF a model were created for the export, THEN a loaded model would be asked for a book that needs none.
    @Test
    void export_maxDialWithAModelChosen_createsNoModel() {
        onFx(() -> {
            brief.setDial(QualityDial.MAX);
            settings.model().set("gemma3:12b");
            return null;
        });

        export();

        assertThat(models.selections()).isEmpty();
    }

    @Test
    void export_validationFailure_isShownInPlaceAndPublishesNoOutcome() {
        exportService.failWith(AppError.of(
                ErrorCode.validation,
                "Export refused",
                "/books/Frankenstein.epub is the book being translated; choose another file."));

        export();

        assertThat(onFx(() -> exports.failure().get())).contains("/books/Frankenstein.epub");
        assertThat(onFx(() -> exports.outcome().get())).isNull();
        assertThat(onFx(() -> progress.done())).doesNotContain(ViewNames.EXPORT);
    }

    // IF the refusal kept the service's English words, THEN a Ukrainian Export screen would refuse in English.
    @Test
    void export_validationFailureInUkrainian_isShownInUkrainian() {
        messages = new Messages(() -> Locale.forLanguageTag("uk"));
        exports = onFx(() -> newExports(new DirectExecutor()));
        exportService.failWith(AppError.of(ErrorCode.validation, "Export refused", "Choose another file."));

        export();

        assertThat(onFx(() -> exports.failure().get()))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }

    @Test
    void export_whileTheJobRuns_isUnavailableAndReturnsWhenItAnswers() {
        final QueuedExecutor queued = new QueuedExecutor();
        exports = onFx(() -> newExports(queued));
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(onFx(() -> exports.exportAvailable().get())).isTrue();

        export();

        assertThat(onFx(() -> exports.exportAvailable().get())).isFalse();
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(onFx(() -> exports.exportAvailable().get())).isTrue();
        assertThat(onFx(() -> exports.outcome().get())).isNotNull();
    }

    @Test
    void export_whileTheJobRuns_asksTheServiceOnceEvenIfPressedAgain() {
        final QueuedExecutor queued = new QueuedExecutor();
        exports = onFx(() -> newExports(queued));
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        export();
        export();
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(exportService.requests()).hasSize(1);
    }

    @Test
    void export_onAWorker_runsTheJobOffTheFxThread() throws TimeoutException {
        useWorker();

        export();
        awaitOutcome();

        assertThat(exportService.ranOnFxThread()).containsExactly(false);
    }

    // IF the file just written were said as an error, THEN a red line would sit beside the green checks of an export
    // that went well; IF it blocked Export, THEN asking before replacing would never be reached.
    @Test
    void export_success_holdsTheFileJustWrittenWithANeutralNoteNotARefusal() {
        refresh();

        export();

        assertThat(onFx(() -> exports.refusal().get())).isEmpty();
        assertThat(onFx(() -> exports.currentNote().get()))
                .isEqualTo("This is the file just exported. Exporting again asks before replacing it.");
        assertThat(onFx(() -> exports.exportAvailable().get())).isTrue();
    }

    // IF a result stayed after the choices that produced it changed, THEN its tiles and Open buttons would describe a
    // file the next press will not write.
    @Test
    void export_thenDestinationEdited_forgetsTheOutcome() {
        exportedOnce();

        editDestination(dir.resolve("Other.epub").toString());

        assertThat(onFx(() -> exports.outcome().get())).isNull();
    }

    @Test
    void export_thenSideFileChosen_forgetsTheOutcome() {
        exportedOnce();

        onFx(() -> {
            exports.setSideFile(SideFile.BILINGUAL_HTML, true);
            return null;
        });

        assertThat(onFx(() -> exports.outcome().get())).isNull();
    }

    @Test
    void export_thenNothingChanges_keepsTheOutcome() {
        exportedOnce();

        refresh();

        assertThat(onFx(() -> exports.outcome().get())).isNotNull();
    }

    private void exportedOnce() {
        export();
        assertThat(onFx(() -> exports.outcome().get())).isNotNull();
    }
}
