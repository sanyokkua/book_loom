package ua.bookloom.ui;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
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
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.pipeline.SetupAssistant;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.RecoveryState;
import ua.bookloom.ui.state.ReviewViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.SettingsViewModel;
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
            case BOOK_READING -> new ImportPreparations(injector, projects).readABook();
            case BOOK_ALREADY_STORED -> new ImportPreparations(injector, projects).dropAKnownBook();
            case LANGUAGE_MISMATCH -> openAMismatchedBook();
            case RUN_COMPLETED -> completeARun();
            case RUN_PROVIDER_FAILED -> pauseARunOnAProviderError();
            case RUN_WAITING_FOR_PROVIDER -> waitForTheProvider();
            case RUN_STARTED -> startARun();
            case REVIEW_SELECTED -> selectAFlaggedSegment();
            case BOOK_REPORTED -> reportAFinishedBook();
            case GLOSSARY_LISTED -> listAGlossary();
            case GLOSSARY_WHILE_TRANSLATING -> listAGlossaryAndStartARun();
            case GLOSSARY_WHILE_HIDDEN -> listAGlossaryAndHideThePass();
            case BOOK_WITHOUT_LANGUAGE -> openABookWithoutLanguage();
            case BRIEF_SUGGESTED -> suggestTheBrief();
            case PROJECTS_LISTED -> listProjects();
            case PROJECTS_EMPTY -> awaitNode("projects-empty-card");
            case PROVIDER_FORM_DIRTY -> new SettingsPreparations(injector).typeIntoTheForm();
            case PROVIDER_FOLD_OPEN -> new SettingsPreparations(injector).openTheFold();
        }
    }

    // Projects listed after the screen was first shown: go away and come back, as a person does, then wait for the
    // card.
    private void listProjects() throws TimeoutException {
        final Path source = Path.of("/books/earth-gravity.epub");
        projects.willList(List.of(ProjectFixtures.summary(
                ProjectFixtures.project(
                        ScriptedProjectService.storedProjectOf(source).id(),
                        "earth-gravity.epub",
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        ua.bookloom.api.project.NameOrigin.AUTO,
                        "Earth Gravity · EN → UK",
                        UiTestInjector.NOW),
                10,
                ProjectFixtures.run(
                        ScriptedProjectService.storedProjectOf(source).id(), JobState.PAUSED),
                null)));
        final Navigator navigator = injector.getInstance(Navigator.class);
        onFx(() -> navigator.navigate(ViewNames.SETTINGS));
        onFx(() -> navigator.navigate(ViewNames.PROJECTS));
        awaitNode("projects-screen");
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> navigator.content().get().lookup(".card") != null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitNode(final String id) throws TimeoutException {
        final Navigator navigator = injector.getInstance(Navigator.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> navigator.content().get().lookup("#" + id) != null));
        WaitForAsyncUtils.waitForFxEvents();
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
        scriptSegments();
        openABook();
        final StructureChecksViewModel checks = injector.getInstance(StructureChecksViewModel.class);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> checks.state().get() instanceof StructureChecks.Finished));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // The segment browser lists the first chapter: a heading, a paragraph and a numeral kept as is, in two chunks.
    private void scriptSegments() {
        projects.onSegments(
                "unit-0",
                Result.ok(List.of(
                        row("s-1", "ch1 · p01", SegmentKind.HEADING, "Letter 1", false, 1),
                        row("s-2", "ch1 · p02", SegmentKind.PARAGRAPH, "You will rejoice to hear.", false, 1),
                        row("s-3", "ch1 · p03", SegmentKind.PARAGRAPH, "2", true, 2))));
    }

    private static SegmentPreview row(
            final String id,
            final String locator,
            final SegmentKind kind,
            final String text,
            final boolean verbatim,
            final int chunk) {
        return new SegmentPreview(id, locator, kind, text, text, 3, verbatim, false, chunk, 2, false);
    }

    // The names screen loads the stored glossary as the book opens; one row is enough to show a term cell.
    private void listAGlossary() throws TimeoutException {
        glossary.willAnswer(Result.ok(List.of(
                new GlossaryEntry("e1", "p1", "Frankenstein", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                        .withSuggestedGender(Gender.MALE),
                new GlossaryEntry("e2", "p1", "Victor", null, TermType.CHARACTER, Gender.MALE, false)
                        .withSuggestedTarget("Віктор"))));
        openABook();
    }

    private void listAGlossaryAndStartARun() throws TimeoutException {
        listAGlossary();
        startARun();
    }

    private void listAGlossaryAndHideThePass() throws TimeoutException {
        listAGlossary();
        HiddenPassPreparation.hide(injector);
    }

    // A diary that declares no language: the brief's source is Auto.
    private void openABookWithoutLanguage() throws TimeoutException {
        final Path source = Path.of("Diary.txt");
        projects.on(source, Result.ok(BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1)));
        openAndAwait(source, ImportState.Detected.class);
    }

    // The model answers for the diary: a genre it could not support, and the language it read the passages in.
    private void suggestTheBrief() throws TimeoutException {
        openABookWithoutLanguage();
        final BookBriefViewModel brief = injector.getInstance(BookBriefViewModel.class);
        onFx(() -> {
            injector.getInstance(SettingsViewModel.class).model().set("gemma3:12b");
            return null;
        });
        scriptTheDiaryAnswer();
        onFx(() -> {
            brief.styleSuggestion().ask();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> brief.styleSuggestion().pending().get() != null));
        pressApply();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> brief.styleSuggestion().languageProposal().get() != null));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void scriptTheDiaryAnswer() {
        ((ScriptedSetupAssistant) injector.getInstance(SetupAssistant.class))
                .answersBrief(Result.ok(new BriefSuggestion(
                        "Diary",
                        Register.NEUTRAL,
                        null,
                        null,
                        NarratorPerson.UNSPECIFIED,
                        Gender.UNKNOWN,
                        Map.of(BriefField.GENRE, new FieldEvidence(FieldEvidence.Basis.UNCERTAIN, List.of(), 1, 4)),
                        "en")));
    }

    // What the dialog's Apply button does: the offer's action writes the brief and ends the dialog.
    private void pressApply() {
        onFx(() -> {
            java.util.Objects.requireNonNull(injector.getInstance(ua.bookloom.ui.state.ActivityTracker.class)
                            .running()
                            .getFirst()
                            .progress()
                            .outcome()
                            .offer())
                    .apply()
                    .run();
            return null;
        });
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
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishOutcome(RunState.COMPLETED, report, null);
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

    // A run with a call waiting and two rows in it, so the strip, the live list and the prompt context are drawn.
    private void startARun() {
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub", null);
        final List<PromptSection> prompt = LiveCallFixtures.smallPrompt();
        mirror.live()
                .publishCalls(LiveRowFixtures.calls(
                        LiveCallFixtures.waiting(2, 2, "You will rejoice to hear.", prompt),
                        LiveRowFixtures.rows(2, LiveRow.Stage.IN_CALL)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void pauseARunOnAProviderError() {
        final AppError unreachable =
                AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening at http://localhost:11434.");
        final StateMirror mirror = injector.getInstance(StateMirror.class);
        mirror.publishRunStarted("Frankenstein.epub", null);
        mirror.publishRunState(RunState.PAUSED);
        mirror.review().publishProviderError(unreachable);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void waitForTheProvider() {
        pauseARunOnAProviderError();
        injector.getInstance(StateMirror.class)
                .review()
                .publishRecovery(new RecoveryState(
                        RecoveryWaiting.Status.WAITING,
                        6,
                        LocalTime.of(2, 14),
                        Instant.parse("2026-10-02T03:00:00Z"),
                        ErrorCode.unreachable,
                        252));
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
