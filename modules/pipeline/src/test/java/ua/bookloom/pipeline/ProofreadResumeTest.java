package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ProofreadProjects.decide;
import static ua.bookloom.pipeline.ProofreadProjects.decideAll;
import static ua.bookloom.pipeline.ProofreadProjects.decided;
import static ua.bookloom.pipeline.ProofreadProjects.lines;
import static ua.bookloom.pipeline.ProofreadProjects.locked;
import static ua.bookloom.pipeline.ProofreadProjects.ok;
import static ua.bookloom.pipeline.ProofreadProjects.update;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProofreadCoverage;
import ua.bookloom.api.pipeline.ProofreadForecast;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.api.pipeline.ProofreadRoundStarted;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.dial.DialParameterProfiles;
import ua.bookloom.pipeline.proofread.ProofreadTallies;
import ua.bookloom.pipeline.revision.DeferralRegister;
import ua.bookloom.pipeline.run.RunStores;

/**
 * The Proofread stage's windows across a stop, a pause and a Continue (design D15 "Resume"): a window already read with
 * the fingerprint it has now is not read again, a stopped stage continues at its first unread window, a pause during
 * the code part lets it finish and pauses before the first window's call, a stop during a window's call stores no
 * verdict, and a window that keeps timing out is left unread while the run completes. Invented en→uk lines, every
 * segment decided before the run, so each run goes straight to Proofread.
 */
class ProofreadResumeTest {

    private static final String PROOFREAD = "proofread";
    private static final int SIXTEEN = 16;
    /** Six marks, two lines apart, so each is its own window at any batch size. */
    private static final List<Integer> SIX_MARKS = List.of(0, 3, 6, 9, 12, 15);

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void continue_stoppedAfterWindow4_onlyWindows5And6Sent() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, SIX_MARKS);
        final ScriptedChatModel first = new ScriptedChatModel();
        final TranslationJobImpl stopped = job(project, first);
        stopped.subscribe(event -> stopAfterFourthWindow(stopped, event));

        assertThat(report(stopped.run()).end()).isEqualTo(JobState.CANCELLED);
        assertThat(proofreadCalls(first)).as("windows 1 to 4").isEqualTo(4);

        final ScriptedChatModel second = new ScriptedChatModel();
        assertThat(report(job(project, second).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(proofreadCalls(second)).as("windows 5 and 6 only").isEqualTo(2);
        assertThat(second.requests().getFirst().messages().getLast().content()).contains("Book.md:12");
    }

    // IF a reopened project could not say where Proofread stopped, THEN Continue would read as a fresh start; the
    // tally counts the four windows read from their stored fingerprints and names the fifth of six.
    @Test
    void tally_stoppedAfterWindow4_continuesAtWindow5Of6() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, SIX_MARKS);
        final TranslationJobImpl stopped = job(project, new ScriptedChatModel());
        stopped.subscribe(event -> stopAfterFourthWindow(stopped, event));
        assertThat(report(stopped.run()).end()).isEqualTo(JobState.CANCELLED);

        final Result<Optional<ProofreadTally>> tally = tallies(project).tally(project.id(), 2);

        assertThat(tally.data()).hasValue(new ProofreadTally(1, 6, 4, 5));
    }

    // IF a finished stage still reported windows left, THEN a finished book would offer to continue proofreading.
    @Test
    void tally_finishedBalancedRun_isEmpty() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, SIX_MARKS);
        assertThat(report(job(project, new ScriptedChatModel()).run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(tallies(project).tally(project.id(), 2).data()).isEmpty();
    }

    // IF the window were not told what the stage reads and which windows it read, THEN the live card could not list
    // them; a Balanced run announces its coverage at Start, its one round and each of its six windows.
    @Test
    void run_balancedSixMarks_announcesTheForecastTheRoundAndEachWindow() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, SIX_MARKS);
        final TranslationJobImpl translation = job(project, new ScriptedChatModel());
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        translation.subscribe(events::add);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(events)
                .filteredOn(ProofreadForecast.class::isInstance)
                .containsExactly(new ProofreadForecast(ProofreadCoverage.MARKED_AND_DOUBTFUL, 0));
        assertThat(events)
                .filteredOn(ProofreadRoundStarted.class::isInstance)
                .containsExactly(new ProofreadRoundStarted(1, 1, 6, 6));
        assertThat(events)
                .filteredOn(ProofreadWindowRead.class::isInstance)
                .hasSize(6)
                .first()
                .satisfies(event -> assertThat(((ProofreadWindowRead) event).items())
                        .extracting(ProofreadItem::segmentId, ProofreadItem::outcome)
                        .containsExactly(tuple("Book.md:0", ProofreadOutcome.UNCHANGED)));
    }

    private static ProofreadTallies tallies(final TestProject project) {
        final RunStores stores = project.stores();
        return new ProofreadTallies(
                stores.openProjects(),
                stores.projects(),
                stores.segments(),
                stores.glossary(),
                stores.lexicon(),
                project.deferrals(),
                new DialParameterProfiles());
    }

    // Max's whole-book round re-reads the marked lines too; a Continue with nothing changed reads neither round again.
    @Test
    void continue_finishedMaxRun_sendsNoRequest() {
        final TestProject project = flaggedBook(QualityDial.MAX, List.of(0, 9));
        final ScriptedChatModel first = new ScriptedChatModel();
        assertThat(report(job(project, first).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(proofreadCalls(first))
                .as("2 marked windows, then 16 lines in 8 windows of 2")
                .isEqualTo(10);

        final ScriptedChatModel second = new ScriptedChatModel();

        assertThat(report(job(project, second).run()).end()).isEqualTo(JobState.COMPLETED);
        assertThat(second.requests()).isEmpty();
    }

    // A pause asked for as the stage starts: the sweep finishes, and the run pauses before the first window's call.
    @Test
    void pause_duringSweep_completesThenPauses() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, List.of(3));
        renameHale(project);
        final ScriptedChatModel model = new ScriptedChatModel();
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.subscribe(event -> pauseOnProofread(translation, event));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);

        assertThat(stored(project, "Book.md:0").machineTarget())
                .as("swept before the pause")
                .isEqualTo("Гейл пішов.");
        assertThat(proofreadCalls(model)).isZero();
        translation.resume();
        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(proofreadCalls(model)).isEqualTo(1);
    }

    @Test
    void stop_inFlightWindow_noVerdict() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, List.of(3));
        final ScriptedChatModel model = new ScriptedChatModel().blockNthRequest(1);
        final TranslationJobImpl translation = job(project, model);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(1);
        translation.cancel();

        assertThat(report(await(run)).end()).isEqualTo(JobState.CANCELLED);
        assertThat(stored(project, "Book.md:3").proofread()).isNull();
        assertThat(stored(project, "Book.md:3").status()).isEqualTo(SegmentStatus.FLAGGED);
    }

    // The window's call times out three times: two pauses are its budget, the third failure leaves it unread with its
    // mark kept, and the next window is read.
    @Test
    void timeout_thirdTime_windowUnreadRunCompletes() {
        final TestProject project = flaggedBook(QualityDial.BALANCED, List.of(3, 9));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(PROOFREAD, Result.err(timeout()))
                .answerTo(PROOFREAD, Result.err(timeout()))
                .answerTo(PROOFREAD, Result.err(timeout()));
        final TranslationJobImpl translation = job(project, model);
        final LinkedBlockingQueue<Paused> pauses = pausesOf(translation);
        translation.pauseAt(Set.of(PausePoint.ON_ERROR));

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        awaitPaused(pauses);
        translation.resume();
        awaitPaused(pauses);
        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(project, "Book.md:3").proofread()).as("left unread").isNull();
        assertThat(stored(project, "Book.md:3").status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(stored(project, "Book.md:9").proofread())
                .extracting(ProofreadVerdict::outcome)
                .isEqualTo(ProofreadOutcome.UNCHANGED);
    }

    /** Sixteen lines, all decided before the run, those at {@code flagged} decided FLAGGED. */
    private TestProject flaggedBook(final QualityDial dial, final List<Integer> flagged) {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        "Hale went away.\n\n"
                                + lines(SIXTEEN - 1, line -> "The bell rang " + (line + 1) + " times at dawn.")),
                brief("en", "uk", dial));
        decideAll(project, SIXTEEN, line -> "Дзвін ударив " + line + " разів на світанку.");
        decide(project, "Book.md:0", "Хейл пішов.");
        flagged.forEach(line -> update(project, "Book.md:" + line, record -> record.withStatus(SegmentStatus.FLAGGED)));
        return project;
    }

    // The locked name Hale is renamed after line 0 was decided, which leaves a term deferral for the sweep.
    private static void renameHale(final TestProject project) {
        final GlossaryEntry hale = locked(project, "Hale", "Хейл");
        ok(project.stores().glossary().add(hale));
        final GlossaryEntry renamed = locked(project, "Hale", "Гейл");
        ok(project.stores().glossary().update(renamed));
        DeferralRegister.termChanged(hale, renamed, decided(project))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
    }

    private static void stopAfterFourthWindow(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof StepChanged changed
                && changed.kind() == StepKind.PROOFREAD
                && changed.state() == StepState.ENDED
                && changed.index() == 3) {
            translation.cancel();
        }
    }

    private static void pauseOnProofread(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof StageStarted started && started.stage() == JobStage.PROOFREAD) {
            translation.pause();
        }
    }

    private static long proofreadCalls(final ScriptedChatModel model) {
        return model.requests().stream()
                .filter(request -> PROOFREAD.equals(ScriptedChatModel.callName(request)))
                .count();
    }

    private static LinkedBlockingQueue<Paused> pausesOf(final TranslationJobImpl translation) {
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> capturePaused(pauses, event));
        return pauses;
    }

    private static AppError timeout() {
        return AppError.of(ErrorCode.timeout, "Provider failure", "The scripted provider timed out.");
    }
}
