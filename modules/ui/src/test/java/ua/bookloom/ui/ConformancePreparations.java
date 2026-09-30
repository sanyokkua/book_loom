package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;

/** What a conformance case does to the application before its view is read, one method per preparation. */
final class ConformancePreparations {

    private static final long WAIT_SECONDS = 10;

    private final Injector injector;
    private final ScriptedProjectService projects;

    ConformancePreparations(final Injector injector, final ScriptedProjectService projects) {
        this.injector = injector;
        this.projects = projects;
    }

    void prepare(final ConformanceCases.Preparation preparation) throws TimeoutException {
        switch (preparation) {
            case NONE -> {
                // nothing to do before the view is read
            }
            case BOOK_OPENED -> openABook();
            case BOOK_REFUSED -> refuseABook();
            case BOOK_DRM_BLOCKED -> blockABook();
            case BOOK_UNSUPPORTED -> refuseAnUnsupportedFile();
            case LANGUAGE_MISMATCH -> openAMismatchedBook();
            case RUN_COMPLETED -> completeARun();
            case RUN_PROVIDER_FAILED -> pauseARunOnAProviderError();
            case RUN_STARTED -> startARun();
            case BOOK_REPORTED -> reportAFinishedBook();
        }
    }

    private void openABook() throws TimeoutException {
        final Path source = Path.of("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        openAndAwait(source, ImportState.Detected.class);
    }

    private void refuseABook() throws TimeoutException {
        final Path source = Path.of("secret.epub");
        projects.on(
                source,
                Result.err(AppError.of(ErrorCode.validation, "This book is protected", "The book is encrypted.")));
        openAndAwait(source, ImportState.Refused.class);
    }

    private void blockABook() throws TimeoutException {
        final Path source = Path.of("Purchased_Novel.epub");
        projects.on(source, Result.ok(BookFixtures.drmProtected("EPUB", "Adobe ADEPT")));
        openAndAwait(source, ImportState.DrmBlocked.class);
    }

    private void refuseAnUnsupportedFile() throws TimeoutException {
        final Path source = Path.of("book.pdf");
        projects.on(source, Result.ok(BookFixtures.refused(InspectionVerdict.UNSUPPORTED, "PDF")));
        openAndAwait(source, ImportState.Unsupported.class);
    }

    private void openAMismatchedBook() throws TimeoutException {
        final Path source = Path.of("Witcher.epub");
        projects.on(
                source,
                Result.ok(BookFixtures.inspected(
                        "w",
                        BookFormat.EPUB,
                        "3.0",
                        "The Witcher",
                        "Sapkowski",
                        BookFixtures.evidence("en", "en", "uk", LanguageEvidence.Verdict.MISMATCH),
                        24,
                        100_000,
                        0,
                        0,
                        null)));
        openAndAwait(source, ImportState.Detected.class);
    }

    private void completeARun() {
        final JobReport report = new JobReport(BookFormat.TXT, JobState.COMPLETED, 10, 10, 0, List.of(), null);
        injector.getInstance(StateMirror.class).publishOutcome(RunState.COMPLETED, report, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void reportAFinishedBook() {
        final Path written = Path.of("/books/Frankenstein.uk.epub");
        final JobReport report = new JobReport(BookFormat.EPUB, JobState.COMPLETED, 1240, 1237, 3, List.of(), null);
        injector.getInstance(StateMirror.class).publishExportedFile(written);
        injector.getInstance(StateMirror.class).publishOutcome(RunState.COMPLETED, report, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void startARun() {
        injector.getInstance(StateMirror.class).publishRunStarted("Frankenstein.epub");
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void pauseARunOnAProviderError() {
        final AppError unreachable =
                AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening at http://localhost:11434.");
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunState(RunState.PAUSED);
        mirror.review().publishProviderError(unreachable);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openAndAwait(final Path source, final Class<? extends ImportState> expected) throws TimeoutException {
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        onFx(() -> {
            viewModel.open(source);
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> expected.isInstance(viewModel.state().get())));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
