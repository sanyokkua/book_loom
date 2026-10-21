package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.withTarget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.persistence.SqlitePersistenceModule;
import ua.bookloom.persistence.sqlite.SqliteDatabase;

/**
 * A translation stopped or killed part-way is continued after a restart over the SQLite store: the reopened project
 * starts at its first pending segment and ends with the counts an uninterrupted run reaches.
 */
// `slow`: whole runs over a real database file; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class RestartContinueE2eTest {

    private static final int PARAGRAPHS = 12;
    private static final int DECIDED_BEFORE_STOP = 5;
    private static final int ACCEPTED_IN_BOOK = 10;
    private static final int FLAGGED_IN_BOOK = 2;
    private static final String DOOR_1 = "He opened the old door.";
    private static final String DOOR_2 = "She closed the old door.";

    @TempDir
    private Path dir;

    // The pseudo model flags the two echoed sentences, so a continued run must reproduce both flags, not only the sums.
    @Test
    void run_stoppedAfterFiveDecisions_reopenedProjectContinuesAtTheSixthAndEndsWithTheSameCounts() {
        final Path source = TestBooks.markdown(dir.resolve("Book.md"), book(), "en");
        final Oracle oracle = uninterrupted();
        final Injector first = boot();
        final String id = prepare(first, source);
        final TranslationJob stopping = newJob(first, id, pseudo());
        stopping.subscribe(event -> cancelAfterDecisions(stopping, event, DECIDED_BEFORE_STOP));
        assertThat(dataOf(stopping.run()).end()).isEqualTo(JobState.CANCELLED);
        drop(first);

        final Injector second = boot();
        final ProjectOpening.Opened opened = open(second, id);
        final SegmentCounts reopened = counts(second, id);
        final RecordingModel recording = new RecordingModel(pseudo());
        final JobReport finished = dataOf(newJob(second, id, recording).run());
        final SegmentCounts end = counts(second, id);
        drop(second);

        assertOracleFlagsTheEchoes(oracle);
        assertThat(opened.interrupted()).isFalse();
        assertThat(reopened).isEqualTo(new SegmentCounts(7, 4, 0, 1, 0));
        assertThat(finished.end()).isEqualTo(JobState.COMPLETED);
        assertThat(finished.segments()).isEqualTo(PARAGRAPHS);
        assertThat(items(recording.prompts().getFirst())).contains(">Para 5.<");
        assertThat(recording.prompts()).noneMatch(prompt -> mentionsAny(prompt, 0, DECIDED_BEFORE_STOP));
        assertThat(end).isEqualTo(new SegmentCounts(0, ACCEPTED_IN_BOOK, 0, FLAGGED_IN_BOOK, 0));
        assertThat(finished.flaggedSegments()).isEqualTo(oracle.flaggedSegments());
    }

    // A kill loses the decisions held for the unfinished chunk; they must come back as pending, not as skipped.
    @Test
    void run_killedMidChunk_reopenedProjectStillContinuesAtTheFirstPendingSegment() {
        final Path source = TestBooks.markdown(dir.resolve("Book.md"), book(), "en");
        final Oracle oracle = uninterrupted();
        final Injector first = boot();
        final String id = prepare(first, source);
        final GatedModel gated = new GatedModel(pseudo(), ">Para 10.<");
        final TranslationJob doomed = newJob(first, id, gated);
        final ExecutorService executor = TranslationJobTestSupport.executor();
        final Future<Result<JobReport>> zombie = executor.submit(doomed::run);
        try {
            gated.awaitEntered();
            drop(first);

            final Continued continued = continueAfterKill(id);
            assertThat(continued.opened().interrupted()).isTrue();
            assertThat(continued.runState()).isEqualTo("CANCELLED");
            assertThat(continued.reopened()).isEqualTo(new SegmentCounts(4, 7, 0, 1, 0));
            assertThat(continued.pendingIds()).containsExactly("Book.md:8", "Book.md:9", "Book.md:10", "Book.md:11");
            assertThat(continued.finished().end()).isEqualTo(JobState.COMPLETED);
            assertThat(items(continued.recording().prompts().getFirst())).contains(">Para 8.<");
            assertThat(continued.end()).isEqualTo(new SegmentCounts(0, oracle.accepted(), 0, oracle.flagged(), 0));
            assertThat(continued.finished().flaggedSegments()).isEqualTo(oracle.flaggedSegments());
        } finally {
            gated.release();
            TranslationJobTestSupport.await(zombie);
        }
    }

    // ---- helpers

    private static void assertOracleFlagsTheEchoes(final Oracle oracle) {
        assertThat(oracle.accepted()).isEqualTo(ACCEPTED_IN_BOOK);
        assertThat(oracle.flagged()).isEqualTo(FLAGGED_IN_BOOK);
        assertThat(oracle.flaggedSegments())
                .containsExactly(
                        new FlaggedSegment("Book.md:2", ErrorCode.validation),
                        new FlaggedSegment("Book.md:9", ErrorCode.validation));
    }

    private record Continued(
            ProjectOpening.Opened opened,
            String runState,
            SegmentCounts reopened,
            List<String> pendingIds,
            RecordingModel recording,
            JobReport finished,
            SegmentCounts end) {}

    private Continued continueAfterKill(final String id) {
        final Injector second = boot();
        final ProjectOpening.Opened opened = open(second, id);
        final String runState = latestRunState(second, id);
        final SegmentCounts reopened = counts(second, id);
        final List<String> pendingIds = pendingIds(second, id);
        final RecordingModel recording = new RecordingModel(pseudo());
        final JobReport finished = dataOf(newJob(second, id, recording).run());
        final SegmentCounts end = counts(second, id);
        drop(second);
        return new Continued(opened, runState, reopened, pendingIds, recording, finished, end);
    }

    private static String latestRunState(final Injector injector, final String id) {
        return Objects.requireNonNull(
                        injector.getInstance(RunRepository.class).latest(id).data())
                .orElseThrow()
                .state()
                .name();
    }

    private record Oracle(int accepted, int flagged, List<FlaggedSegment> flaggedSegments) {}

    private Oracle uninterrupted() {
        final Path source = TestBooks.markdown(oracleDir().resolve("Book.md"), book(), "en");
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new LlmModule(),
                new InMemoryPersistenceModule(),
                new PipelineModule(),
                new ReviewModeTestModule());
        final String id = prepare(injector, source);
        final JobReport report = dataOf(newJob(injector, id, pseudo()).run());
        return new Oracle(report.accepted(), report.flagged(), report.flaggedSegments());
    }

    private Path oracleDir() {
        try {
            return java.nio.file.Files.createDirectories(dir.resolve("oracle"));
        } catch (java.io.IOException cause) {
            throw new java.io.UncheckedIOException(cause);
        }
    }

    private Injector boot() {
        return Guice.createInjector(
                new DocumentModule(),
                new LlmModule(),
                new SqlitePersistenceModule(dir.resolve("bookloom.db"), Clock.systemUTC()),
                new PipelineModule(),
                new ReviewModeTestModule());
    }

    private static void drop(final Injector injector) {
        injector.getInstance(SqliteDatabase.class).close();
    }

    private static String prepare(final Injector injector, final Path source) {
        final ProjectService projects = injector.getInstance(ProjectService.class);
        final ImportedBook imported = dataOf(projects.create(source));
        final String id = Objects.requireNonNull(imported.projectId(), "project id");
        dataOf(projects.updateBrief(id, withTarget(Objects.requireNonNull(imported.brief()), "uk")));
        return id;
    }

    private static ProjectOpening.Opened open(final Injector injector, final String id) {
        return (ProjectOpening.Opened)
                dataOf(injector.getInstance(ProjectService.class).open(id));
    }

    private static TranslationJob newJob(final Injector injector, final String id, final ChatModel model) {
        return dataOf(
                injector.getInstance(TranslationEngine.class).newJob(new RunRequest(id, ReviewMode.UNATTENDED), model));
    }

    private static SegmentCounts counts(final Injector injector, final String id) {
        return dataOf(injector.getInstance(SegmentRepository.class)
                .countsByStatus(id, new AlsoTranslate(false, false, false, false).keptKinds()));
    }

    private static List<String> pendingIds(final Injector injector, final String id) {
        return dataOf(injector.getInstance(SegmentRepository.class).all(id)).stream()
                .filter(record -> record.status() == ua.bookloom.api.document.SegmentStatus.PENDING)
                .map(SegmentRecord::segmentId)
                .toList();
    }

    private static ChatModel pseudo() {
        return new PseudoChatModel(new ObjectMapper());
    }

    private static String book() {
        return IntStream.range(0, PARAGRAPHS)
                .mapToObj(RestartContinueE2eTest::paragraph)
                .collect(Collectors.joining("\n\n"));
    }

    private static String paragraph(final int index) {
        return switch (index) {
            case 2 -> DOOR_1;
            case 9 -> DOOR_2;
            default -> "Para " + index + ".";
        };
    }

    private static boolean mentionsAny(final String prompt, final int from, final int to) {
        return IntStream.range(from, to).anyMatch(index -> items(prompt).contains(">Para " + index + ".<"));
    }

    private static void cancelAfterDecisions(final TranslationJob job, final Object event, final int count) {
        if (event instanceof SegmentDecided decided
                && decided.progress().accepted() + decided.progress().flagged() == count) {
            job.cancel();
        }
    }

    private static <T> T dataOf(final Result<T> result) {
        assertThat(result.isOk()).as("result error %s", result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "result data");
    }

    // Prompts replay earlier pairs as context before the "Translate from" line; only what follows it is being
    // translated.
    private static String items(final String prompt) {
        return prompt.substring(Math.max(0, prompt.lastIndexOf("\nTranslate from ")));
    }

    private static String promptOf(final ChatRequest request) {
        return request.messages().stream().map(ChatMessage::content).collect(Collectors.joining("\n"));
    }

    /** Records every prompt it is sent and answers like the wrapped model. */
    private static final class RecordingModel implements ChatModel {

        private final ChatModel delegate;
        private final List<String> prompts = new CopyOnWriteArrayList<>();

        RecordingModel(final ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            prompts.add(promptOf(request));
            return delegate.chat(request);
        }

        List<String> prompts() {
            return prompts;
        }
    }

    /** Holds the first request that mentions the marker until released, as a process killed mid-call would never return. */
    private static final class GatedModel implements ChatModel {

        private final ChatModel delegate;
        private final String marker;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        GatedModel(final ChatModel delegate, final String marker) {
            this.delegate = delegate;
            this.marker = marker;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            if (items(promptOf(request)).contains(marker)) {
                entered.countDown();
                TranslationJobTestSupport.awaitIgnoringInterrupt(released, "the gated model");
            }
            return delegate.chat(request);
        }

        void awaitEntered() {
            TranslationJobTestSupport.awaitIgnoringInterrupt(entered, "the gated request");
        }

        void release() {
            released.countDown();
        }
    }
}
