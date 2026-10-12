package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchDraftingJobTest.numbered;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** A batch reply that stops after its first item or is cut at the length cap leaves the next batch its size. */
class BatchSizeSignalJobTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private TestProject sixteenShortLines() {
        final String book = IntStream.rangeClosed(1, 16)
                .mapToObj(index -> "Short line " + index + ".")
                .collect(Collectors.joining("\n\n"));
        return project(TestBooks.markdown(tempDir.resolve("Book.md"), book), brief("en", "uk"));
    }

    private static String idsFrom(final int firstLocalId, final int lastLocalId, final int firstLine) {
        return items(IntStream.rangeClosed(firstLocalId, lastLocalId)
                .mapToObj(id -> entry(Integer.toString(id), "Короткий рядок " + (firstLine + id - 1) + "."))
                .toArray(String[]::new));
    }

    // IF the model answers only the first item of the batch and the re-ask brings the rest, THEN the next batch is as
    // large as before: the model's early stop says nothing about how many items it can number.
    @Test
    void run_firstItemOnlyReply_reaskedAndTheNextBatchKeepsItsSize() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(idsFrom(1, 1, 1)))
                .answerTo(BATCH, batchReply(idsFrom(2, 8, 1)))
                .answerTo(BATCH, batchReply(numbered(9, 8, 0)));
        final TestProject project = sixteenShortLines();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, BATCH, BATCH);
        assertThat(userMessage(model.requests().get(2))).contains("<s id=\"8\">Short line 16.</s>");
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 16, 0, 0, 0));
    }

    // IF the reply is cut at the length cap after three items, THEN the five missing ids are asked again and the
    // next batch keeps its size.
    @Test
    void run_replyCutAtTheLengthCap_reasksTheMissingIdsAndTheNextBatchKeepsItsSize() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, Result.ok(new ChatResponse(idsFrom(1, 3, 1), FinishReason.LENGTH)))
                .answerTo(BATCH, batchReply(idsFrom(4, 8, 1)))
                .answerTo(BATCH, batchReply(numbered(9, 8, 0)));
        final TestProject project = sixteenShortLines();

        report(batchedJob(project, model).run());

        assertThat(formats(model)).containsExactly(BATCH, BATCH, BATCH);
        assertThat(userMessage(model.requests().get(1)))
                .contains("<s id=\"4\">")
                .doesNotContain("<s id=\"3\">");
        assertThat(userMessage(model.requests().get(2))).contains("<s id=\"8\">Short line 16.</s>");
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 16, 0, 0, 0));
    }

    // IF a batch is answered, THEN the run's line names the segments it carried, the size the next batch may carry
    // and the ceiling the size can reach, so a size above the first one is not mistaken for a broken cap.
    @Test
    void run_batchAnswered_logsTheNextSizeAndTheCeilingBesideTheSegmentsCarried() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(numbered(1, 8, 0)))
                .answerTo(BATCH, batchReply(numbered(9, 8, 0)));
        final Logger log = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline.run.BatchStage");
        final Level before = log.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.setLevel(Level.INFO);
        log.addAppender(appender);

        try {
            report(batchedJob(sixteenShortLines(), model).run());
        } finally {
            log.detachAppender(appender);
            log.setLevel(before);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(message -> message.startsWith("Batch answered"))
                .first()
                .asString()
                .contains("segments=8", "nextSize=8", "maxSize=16");
    }
}
