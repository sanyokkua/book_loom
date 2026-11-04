package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.numbered;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The batch size of a run is the model profile's size, whatever the dial or the review mode says: with the dial's
 * chunk cap gone, ten segments go out as batches of four, four and two (redesign group 10.8).
 */
class BatchStageTest {

    private static final int PROFILE_SIZE = 4;
    private static final int SEGMENTS = 10;
    private static final Pattern ITEM_ID = Pattern.compile("<s id=\"(\\d+)\">");

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject tenLines(final QualityDial dial) {
        final String book = IntStream.rangeClosed(1, SEGMENTS)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        return project(TestBooks.markdown(tempDir.resolve("Book.md"), book), brief("en", "uk", dial));
    }

    private static ScriptedChatModel profileFourModel() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .withBatchSize(PROFILE_SIZE)
                .answerTo(BATCH, batchReply(numbered(1, 4, 0)))
                .answerTo(BATCH, batchReply(numbered(5, 4, 0)))
                .answerTo(BATCH, batchReply(numbered(9, 2, 0)));
        IntStream.range(0, SEGMENTS).forEach(ignored -> model.answerTo(REVIEW, reviewed()));
        return model;
    }

    private static List<Integer> batchSizes(final ScriptedChatModel model) {
        return model.requests().stream()
                .filter(request -> BATCH.equals(formatName(request)))
                .map(BatchStageTest::itemCount)
                .toList();
    }

    private static String formatName(final ChatRequest request) {
        return Objects.requireNonNull(request.responseFormat(), "format").name();
    }

    private static int itemCount(final ChatRequest request) {
        final Matcher ids = ITEM_ID.matcher(userMessage(request));
        return (int) ids.results().count();
    }

    @ParameterizedTest
    @EnumSource(QualityDial.class)
    void size_profileFour_draftsTenSegmentsAsFourFourTwo(final QualityDial dial) {
        final ScriptedChatModel model = profileFourModel();
        final TestProject project = tenLines(dial);

        report(profiledJob(project, model, ReviewMode.UNATTENDED).run());

        assertThat(batchSizes(model)).containsExactly(4, 4, 2);
        assertThat(formats(model)).filteredOn(BATCH::equals).hasSize(3);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, SEGMENTS, 0, 0, 0));
    }

    @Test
    void manualReview_draftsInProfileBatches() {
        final ScriptedChatModel model = profileFourModel();
        final TestProject project = tenLines(QualityDial.FAST);
        final TranslationJobImpl translation = profiledJob(project, model, ReviewMode.MANUAL);
        final List<String> pausedAt = new CopyOnWriteArrayList<>();
        final List<Integer> batchCallsAtPause = new CopyOnWriteArrayList<>();
        translation.subscribe(event -> {
            if (event instanceof Paused paused) {
                pausedAt.add(paused.segmentId());
                batchCallsAtPause.add(batchSizes(model).size());
                translation.resume();
            }
        });

        report(translation.run());

        assertThat(batchSizes(model)).containsExactly(4, 4, 2);
        assertThat(pausedAt)
                .containsExactlyElementsOf(IntStream.range(0, SEGMENTS)
                        .mapToObj(index -> "Book.md:" + index)
                        .toList());
        assertThat(batchCallsAtPause).containsExactly(1, 1, 1, 1, 2, 2, 2, 2, 3, 3);
    }

    @Test
    void profileSize_notPassedByPacing_isReadFromModelProfile() {
        final ScriptedChatModel model = profileFourModel();
        final TestProject project = tenLines(QualityDial.FAST);
        final TranslationJobImpl translation = new TranslationJobImpl(
                new RunRequest(project.id(), ReviewMode.UNATTENDED),
                new JobTools(project.documents(), model, new ObjectMapper(), new PromptTemplates()),
                project.stores(),
                new RunChecks(
                        TranslationJobTestSupport.qualityLoop(),
                        TranslationJobTestSupport.SPLITTER,
                        ConsistencyPassFixture.over(project),
                        WordValidator.none()),
                new RunPacing(Clock.systemUTC(), new UuidIdSource(), RecoveryTimer.REAL),
                new RunLogFacts(CallLedger.none(), "unknown"));

        report(translation.run());

        assertThat(batchSizes(model)).first().isEqualTo(PROFILE_SIZE);
    }

    private static TranslationJobImpl profiledJob(
            final TestProject project, final ScriptedChatModel model, final ReviewMode mode) {
        return batchedJob(project, model, new RunRequest(project.id(), mode), PROFILE_SIZE);
    }
}
