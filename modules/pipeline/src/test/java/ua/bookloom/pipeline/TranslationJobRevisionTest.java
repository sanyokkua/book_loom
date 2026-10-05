package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.S3;
import static ua.bookloom.pipeline.ChunkRunFixtures.T3;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.pauses;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.stages;
import static ua.bookloom.pipeline.ChunkRunFixtures.target;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.Finished;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PauseReason;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.StageStarted;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;
import ua.bookloom.pipeline.revision.DeferralRegister;

/**
 * The run's backward revision on the Max dial: it follows the last decision as its own stage, the Balanced dial has
 * none, and a pause asked for as it starts lets the deterministic sweep finish and holds the first revision call.
 */
class TranslationJobRevisionTest {

    private static final String HALE_LEFT = "Book.md:0";
    private static final String SAM_LEFT = "Book.md:1";
    private static final String WIND = "Book.md:2";
    private static final String REVISION = "revision";
    private static final String SUMMARY = "summary";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // On Max the revision stage starts after the last decision and before the run finishes.
    @Test
    void run_maxDial_revisesAfterLastDecision() {
        final TestProject project = bookWithDeferrals(QualityDial.MAX);
        final ScriptedChatModel model = model();
        final TranslationJobImpl translation = job(project, model);
        final List<String> events = described(translation);

        final JobReport report = report(translation.run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(events).containsSubsequence("decided " + WIND, "stage REVISE", "finished");
        assertThat(events.getLast()).isEqualTo("finished");
        assertThat(stored(project, HALE_LEFT).machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(stored(project, SAM_LEFT).machineTarget()).isEqualTo("Сем пішла.");
        assertThat(stored(project, SAM_LEFT).status()).isEqualTo(SegmentStatus.REVISED);
    }

    // Balanced never revises backwards, so no revision stage runs and the deferrals stay as they were.
    @Test
    void run_balancedDial_hasNoReviseStage() {
        final TestProject project = bookWithDeferrals(QualityDial.BALANCED);
        final ScriptedChatModel model = model();
        final TranslationJobImpl translation = job(project, model);
        final List<JobStage> stages = stages(translation);

        assertThat(report(translation.run()).end()).isEqualTo(JobState.COMPLETED);

        assertThat(stages).containsExactly(JobStage.PREP, JobStage.TRANSLATE);
        assertThat(formats(model)).doesNotContain(REVISION);
        assertThat(stored(project, HALE_LEFT).machineTarget()).isEqualTo("Хейл пішов.");
    }

    // A pause asked for as revision starts lets the term substitution finish and holds the revision call until resume.
    @Test
    void run_pauseOnReviseStart_sweepsThenPausesBeforeRevisionCall() {
        final TestProject project = bookWithDeferrals(QualityDial.MAX);
        final ScriptedChatModel model = model();
        final TranslationJobImpl translation = job(project, model);
        translation.subscribe(event -> pauseOnRevise(translation, event));
        final LinkedBlockingQueue<Paused> pauses = pauses(translation);

        final Future<Result<JobReport>> run = executor().submit(translation::run);
        final Paused pause = awaitPaused(pauses);

        assertThat(pause.reason()).isEqualTo(PauseReason.REQUESTED);
        assertThat(pause.progress().stage()).isEqualTo(JobStage.REVISE);
        assertThat(translation.state()).isEqualTo(JobState.PAUSED);
        assertThat(stored(project, HALE_LEFT).machineTarget()).isEqualTo("Гейл пішов.");
        assertThat(formats(model)).doesNotContain(REVISION);

        translation.resume();

        assertThat(report(await(run)).end()).isEqualTo(JobState.COMPLETED);
        assertThat(formats(model)).containsOnlyOnce(REVISION);
        assertThat(stored(project, SAM_LEFT).machineTarget()).isEqualTo("Сем пішла.");
    }

    /**
     * A three-paragraph book whose first two are decided before the run: the first used {@code Хейл}, since renamed
     * {@code Гейл} and locked, and the second names {@code Sam}, whose gender was unknown then and is female now.
     */
    private TestProject bookWithDeferrals(final QualityDial dial) {
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "Hale went away.\n\nSam went away.\n\n" + S3),
                brief("en", "uk", dial));
        final GlossaryEntry hale = character(project, "Hale", "Хейл", Gender.MALE, true);
        final GlossaryEntry sam = character(project, "Sam", "Сем", Gender.UNKNOWN, false);
        ok(project.stores().glossary().add(hale));
        ok(project.stores().glossary().add(sam));
        decide(project, HALE_LEFT, "Хейл пішов.");
        decide(project, SAM_LEFT, "Сем пішов.");
        DeferralRegister.unknownGender(project.id(), segment(project, SAM_LEFT), List.of(sam))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
        final GlossaryEntry renamed = character(project, "Hale", "Гейл", Gender.MALE, true);
        ok(project.stores().glossary().update(renamed));
        DeferralRegister.termChanged(
                        hale, renamed, ok(project.stores().segments().all(project.id())))
                .forEach(deferral -> ok(project.deferrals().add(deferral)));
        ok(project.stores().glossary().update(character(project, "Sam", "Сем", Gender.FEMALE, false)));
        return project;
    }

    private static ScriptedChatModel model() {
        return new ScriptedChatModel()
                .answer(target(T3))
                .answerTo(REVIEW, reviewed())
                .answerTo(REVIEW, reviewed())
                .answerTo(SUMMARY, summaryReply())
                .answerTo(REVISION, target("Сем пішла."));
    }

    private static void pauseOnRevise(final TranslationJobImpl translation, final JobEvent event) {
        if (event instanceof StageStarted started && started.stage() == JobStage.REVISE) {
            translation.pause();
        }
    }

    private static List<String> described(final TranslationJobImpl translation) {
        final List<String> events = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> events.add(describe(event)));
        return events;
    }

    private static String describe(final JobEvent event) {
        return switch (event) {
            case SegmentDecided decided -> "decided " + decided.segmentId();
            case StageStarted started -> "stage " + started.stage();
            case Finished finished -> "finished";
            default -> event.getClass().getSimpleName();
        };
    }

    private static GlossaryEntry character(
            final TestProject project,
            final String term,
            final String target,
            final Gender gender,
            final boolean locked) {
        return new GlossaryEntry(
                GlossaryIds.of(project.id(), term), project.id(), term, target, TermType.CHARACTER, gender, locked);
    }

    private static void decide(final TestProject project, final String segmentId, final String target) {
        ok(project.stores()
                .segments()
                .update(
                        project.id(),
                        segmentId,
                        record -> record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget(target, target)));
    }

    private static Segment segment(final TestProject project, final String segmentId) {
        return Objects.requireNonNull(project.stores().openProjects().get(project.id()), "open book").units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(candidate -> candidate.id().equals(segmentId))
                .findFirst()
                .orElseThrow();
    }

    private static Result<ChatResponse> summaryReply() {
        return Result.ok(new ChatResponse(
                "{\"summary\":{\"source\":\"The wind rose.\",\"target\":\"Вітер здійнявся.\"},\"facts\":[]}",
                FinishReason.STOP));
    }

    private static <T> T ok(final Result<T> result) {
        return Objects.requireNonNull(result.data(), () -> "expected ok, got " + result.error());
    }
}
