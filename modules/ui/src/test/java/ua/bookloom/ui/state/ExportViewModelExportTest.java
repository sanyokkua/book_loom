package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ViewNames;

/** What pressing Export book does: the request it builds, the work it moves off the FX thread and the outcome it keeps. */
class ExportViewModelExportTest extends ExportViewModelTestBase {

    private static final int SIZE = 958_464;

    @TempDir
    private Path dir;

    private Path destination;
    private ExecutorService worker;

    @BeforeEach
    void openTheBook() throws IOException {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
        destination = dir.resolve("Frankenstein.uk.epub");
        Files.write(destination, new byte[SIZE]);
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
        setOverwrite(true);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void export_success_publishesTheOutcomeWithItsSizeAndMarksTheStepDone() {
        setOverwrite(true);

        export();

        final ExportOutcome outcome = onFx(() -> exports.outcome().get());
        assertThat(outcome.report().destination()).isEqualTo(destination);
        assertThat(outcome.sizeBytes()).isEqualTo(SIZE);
        assertThat(onFx(() -> progress.done())).contains(ViewNames.EXPORT);
        assertThat(onFx(() -> exports.failure().get())).isEmpty();
    }

    @Test
    void export_default_buildsTheRequestFromTheChoices() {
        export();

        assertThat(exportService.requests())
                .containsExactly(new ExportRequest("p1", destination, false, Set.of(SideFile.GLOSSARY_CSV), false));
        assertThat(exportService.models()).containsOnlyNulls();
    }

    @Test
    void export_maxDialWithAModelChosen_passesAModelCreatedOffTheFxThread() throws TimeoutException {
        onFx(() -> {
            brief.setDial(QualityDial.MAX);
            settings.model().set("gemma3:12b");
            return null;
        });
        useWorker();

        export();
        awaitOutcome();

        assertThat(exportService.requests().getFirst().consistencyPass()).isTrue();
        assertThat(exportService.models().getFirst()).isNotNull();
        assertThat(models.selections()).hasSize(1);
        assertThat(models.askedOnFxThread()).containsExactly(false);
    }

    @Test
    void export_maxDialWithNoModelChosen_passesNoModel() {
        onFx(() -> {
            brief.setDial(QualityDial.MAX);
            return null;
        });

        export();

        assertThat(exportService.requests().getFirst().consistencyPass()).isTrue();
        assertThat(exportService.models()).containsOnlyNulls();
        assertThat(models.selections()).isEmpty();
    }

    @Test
    void export_balancedDialWithAModelChosen_passesNoModel() {
        onFx(() -> {
            settings.model().set("gemma3:12b");
            return null;
        });

        export();

        assertThat(exportService.models()).containsOnlyNulls();
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

    @Test
    void export_whileTheJobRuns_isUnavailableAndReturnsWhenItAnswers() {
        final QueuedExecutor queued = new QueuedExecutor();
        exports = onFx(() -> newExports(queued));
        setOverwrite(true);
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
        setOverwrite(true);
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

    // IF a model that cannot be created failed the whole export, THEN a provider that is down would block a book
    // that needs no model to be written.
    @Test
    void export_modelCannotBeCreated_writesTheBookWithNoModel() {
        models = ScriptedChatModelFactory.failing(
                AppError.of(ErrorCode.unreachable, "Provider down", "The provider is not reachable."));
        exports = onFx(() -> newExports(new DirectExecutor()));
        onFx(() -> {
            brief.setDial(QualityDial.MAX);
            settings.model().set("gemma3:12b");
            return null;
        });
        setOverwrite(true);

        export();

        assertThat(models.selections()).hasSize(1);
        assertThat(exportService.models()).containsOnlyNulls();
        assertThat(exportService.requests().getFirst().consistencyPass()).isTrue();
        assertThat(onFx(() -> exports.outcome().get())).isNotNull();
        assertThat(onFx(() -> exports.failure().get())).isEmpty();
    }

    // IF the file just written were offered as a free name, THEN a second press would be refused by the job with a
    // message that does not say why; IF it were said as an error, THEN a red line would sit beside the green checks of
    // an export that went well.
    @Test
    void export_success_holdsTheFileJustWrittenWithANeutralNoteNotARefusal() throws IOException {
        Files.delete(destination);
        refresh();
        exportService.writeFiles(true);

        export();

        assertThat(onFx(() -> exports.refusal().get())).isEmpty();
        assertThat(onFx(() -> exports.currentNote().get()))
                .isEqualTo(
                        "This is the file just exported. To write it again, turn on Replace or choose another name.");
        assertThat(onFx(() -> exports.exportAvailable().get())).isFalse();
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
    void export_thenReplaceChanged_forgetsTheOutcome() {
        exportedOnce();

        setOverwrite(false);

        assertThat(onFx(() -> exports.outcome().get())).isNull();
    }

    @Test
    void export_thenSideFileChosen_forgetsTheOutcome() {
        exportedOnce();

        onFx(() -> {
            exports.setSideFile(SideFile.QUALITY_REPORT, true);
            return null;
        });

        assertThat(onFx(() -> exports.outcome().get())).isNull();
    }

    @Test
    void export_thenConsistencySwitched_forgetsTheOutcome() {
        exportedOnce();

        onFx(() -> {
            exports.setConsistencyPass(true);
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
        setOverwrite(true);
        export();
        assertThat(onFx(() -> exports.outcome().get())).isNotNull();
    }
}
