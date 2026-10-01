package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.InspectionVerdict;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RoundTrack;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StructureChecks;
import ua.bookloom.ui.state.StructureChecksViewModel;

/** What a conformance case does to the application before its view is read, one method per preparation. */
final class ConformancePreparations {

    private static final long WAIT_SECONDS = 10;

    private final Injector injector;
    private final ScriptedProjectService projects;
    private final ScriptedGlossaryService glossary;

    ConformancePreparations(
            final Injector injector, final ScriptedProjectService projects, final ScriptedGlossaryService glossary) {
        this.injector = injector;
        this.projects = projects;
        this.glossary = glossary;
    }

    void prepare(final ConformanceCases.Preparation preparation) throws TimeoutException {
        switch (preparation) {
            case NONE -> {
                // nothing to do before the view is read
            }
            case BOOK_OPENED -> openABook();
            case BOOK_CHECKED -> checkABook();
            case BOOK_REFUSED -> refuseABook();
            case BOOK_DRM_BLOCKED -> blockABook();
            case BOOK_UNSUPPORTED -> refuseAnUnsupportedFile();
            case LANGUAGE_MISMATCH -> openAMismatchedBook();
            case RUN_COMPLETED -> completeARun();
            case RUN_PROVIDER_FAILED -> pauseARunOnAProviderError();
            case RUN_STARTED -> startARun();
            case REVIEW_SELECTED -> selectAFlaggedSegment();
            case BOOK_REPORTED -> reportAFinishedBook();
            case GLOSSARY_LISTED -> listAGlossary();
        }
    }

    private void openABook() throws TimeoutException {
        final Path source = Path.of("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        openAndAwait(source, ImportState.Detected.class);
    }

    // The structure screen asks for its checks when a book is open: one oversized segment shows its warning.
    private void checkABook() throws TimeoutException {
        projects.onRoundTrip(Result.ok(new RoundTripReport(true, true, List.of(), 9, 9)));
        projects.onPlan(Result.ok(new BookPlan(Map.of("unit-0", 3), List.of("s-4"))));
        openABook();
        final StructureChecksViewModel checks = injector.getInstance(StructureChecksViewModel.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> checks.state().get() instanceof StructureChecks.Finished));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The names screen loads the stored glossary as the book opens; one row is enough to show a term cell.
    private void listAGlossary() throws TimeoutException {
        glossary.willAnswer(Result.ok(List.of(
                new GlossaryEntry("e1", "p1", "Frankenstein", null, TermType.CHARACTER, Gender.UNKNOWN, false))));
        openABook();
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

    // A paused run with one flagged segment selected in the review view model; the overlay opens the panel.
    private void selectAFlaggedSegment() throws TimeoutException {
        final ScriptedReviewDesk desk = (ScriptedReviewDesk) injector.getInstance(ReviewDesk.class);
        desk.willAnswerQueue(List.of(ReviewFixtures.lowScoreWithFinding()));
        desk.willAnswerSegment(ReviewFixtures.lowScoreWithFinding());
        desk.willAnswerCounts(new ReviewCounts(100, 99, 0, 1, 0, 0, 0, 0, 0));
        openABook();
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunState(RunState.PAUSED);
        mirror.live().publishFlaggedQueue(List.of(new FlaggedRow("s-1", "ch5 · p12", List.of(), null)));
        WaitForAsyncUtils.waitForFxEvents();
        final ReviewViewModel review = injector.getInstance(ReviewViewModel.class);
        onFx(() -> {
            review.select(ReviewFixtures.lowScore().segmentId());
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> review.selected().get() != null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The export screen's finished state comes from a real export through the view model, as a person's press does.
    private void reportAFinishedBook() throws TimeoutException {
        openABook();
        onFx(() -> {
            injector.getInstance(BookBriefViewModel.class).setTargetLanguage("uk");
            return null;
        });
        final ExportViewModel exports = injector.getInstance(ExportViewModel.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> exports.exportAvailable().get()));
        onFx(() -> {
            exports.export();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> exports.outcome().get() != null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // A run with one segment in progress whose draft was sent a context, so the live row's parts are drawn.
    private void startARun() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub");
        mirror.live()
                .publishLiveRows(new LiveRows(
                        null,
                        new LiveRow(
                                "s-2",
                                "ch1 · p02",
                                "You will rejoice to hear.",
                                null,
                                null,
                                null,
                                true,
                                false,
                                new RoundTrack(1, 3, 0.85, "meaning"),
                                new ContextSnapshot(List.of("Ви зрадієте."), List.of(), List.of(), "A letter.", ""))));
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
