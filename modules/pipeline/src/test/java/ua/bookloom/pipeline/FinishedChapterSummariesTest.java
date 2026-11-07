package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.SummaryBooks.CHAPTER_SEGMENTS;
import static ua.bookloom.pipeline.SummaryBooks.epubBook;
import static ua.bookloom.pipeline.SummaryBooks.isSummary;
import static ua.bookloom.pipeline.SummaryBooks.marker;
import static ua.bookloom.pipeline.SummaryBooks.model;
import static ua.bookloom.pipeline.SummaryBooks.storedFor;
import static ua.bookloom.pipeline.SummaryBooks.userText;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.SegmentDecided;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A chapter whose last segments were decided by someone else while the run was at work (the review desk, a skip) is
 * summarised once the run next commits a chunk, not only on the next Continue.
 */
class FinishedChapterSummariesTest {

    private static final int CHAPTERS = 4;
    private static final String DECIDED_OUTSIDE = "Рішення поза запуском.";
    private static final String CHAPTER_2 = "ch02.xhtml";
    private static final String CHAPTER_2_KEY = CHAPTER_2 + ":0";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    // Decides chapter 2's segments the way the desk does, as soon as the run has decided its first segment.
    private static void decideChapter2Outside(final TestProject project) {
        IntStream.range(0, CHAPTER_SEGMENTS)
                .forEach(index -> project.stores()
                        .segments()
                        .update(
                                project.id(),
                                CHAPTER_2 + ":" + index,
                                record -> record.withMachineTarget(DECIDED_OUTSIDE, DECIDED_OUTSIDE)
                                        .withStatus(SegmentStatus.ACCEPTED)));
    }

    private static JobReport runDecidingChapter2Outside(final TestProject project, final ScriptedChatModel model) {
        final TranslationJobImpl job = TranslationJobTestSupport.job(project, model);
        final AtomicBoolean done = new AtomicBoolean();
        job.subscribe(event -> {
            if (event instanceof SegmentDecided && done.compareAndSet(false, true)) {
                decideChapter2Outside(project);
            }
        });
        return TranslationJobTestSupport.report(job.run());
    }

    @Test
    void run_chapterDecidedOutsideTheRun_summarisedAfterALaterCommit() {
        final TestProject project = epubBook(tempDir, CHAPTERS, true);
        final ScriptedChatModel model = model();

        final JobReport report = runDecidingChapter2Outside(project, model);

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(storedFor(project, CHAPTER_2_KEY)).as("chapter 2's summary").isPresent();
        final List<String> summaryAsks = model.requests().stream()
                .filter(SummaryBooks::isSummary)
                .map(SummaryBooks::userText)
                .toList();
        assertThat(summaryAsks).filteredOn(text -> text.contains(marker(2))).hasSize(1);
        assertThat(summaryAsks).as("one per finished chapter but the last").hasSize(CHAPTERS - 1);
    }

    @Test
    void run_lastChapter_neverSummarised() {
        final TestProject project = epubBook(tempDir, CHAPTERS, true);
        final ScriptedChatModel model = model();

        runDecidingChapter2Outside(project, model);

        assertThat(model.requests().stream()
                        .filter(request ->
                                isSummary(request) && userText(request).contains(marker(CHAPTERS))))
                .isEmpty();
    }
}
