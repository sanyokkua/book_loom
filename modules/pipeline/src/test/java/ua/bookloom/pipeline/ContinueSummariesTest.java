package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.SummaryBooks.CHAPTER_SEGMENTS;
import static ua.bookloom.pipeline.SummaryBooks.EARLIER;
import static ua.bookloom.pipeline.SummaryBooks.beforeFirstDraft;
import static ua.bookloom.pipeline.SummaryBooks.book;
import static ua.bookloom.pipeline.SummaryBooks.firstDraftOf;
import static ua.bookloom.pipeline.SummaryBooks.isSummary;
import static ua.bookloom.pipeline.SummaryBooks.marker;
import static ua.bookloom.pipeline.SummaryBooks.model;
import static ua.bookloom.pipeline.SummaryBooks.stoppedAfter;
import static ua.bookloom.pipeline.SummaryBooks.storedFor;
import static ua.bookloom.pipeline.SummaryBooks.switchSummaries;
import static ua.bookloom.pipeline.SummaryBooks.userText;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * Continue writes the summary of every finished chapter that has none, or whose stored one was written from other
 * source text, before the next draft ({@code specs/resume/spec.md} "Write the missing chapter summaries first"); the
 * plan holds one summary step per chapter.
 */
class ContinueSummariesTest {

    private static final int CHAPTERS = 4;
    private static final String CHAPTER_1 = "Book.md:0";
    private static final String CHAPTER_2 = "Book.md:4";
    private static final String CHAPTER_3 = "Book.md:8";
    private static final String CHAPTER_4 = "Book.md:12";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    /** One continued run: what it sent and announced. */
    private record Continued(List<ChatRequest> requests, List<JobEvent> events) {}

    private static Continued continueRun(final TestProject project) {
        final ScriptedChatModel model = model();
        final TranslationJobImpl job = TranslationJobTestSupport.job(project, model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        job.subscribe(events::add);
        final JobReport report = TranslationJobTestSupport.report(job.run());
        assertThat(report.end())
                .as("continued run ended with %s", report.error())
                .isEqualTo(JobState.COMPLETED);
        return new Continued(model.requests(), events);
    }

    private static List<StepChanged> endedSummarySteps(final List<JobEvent> events) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filter(change -> change.kind() == StepKind.SUMMARY && change.state() == StepState.ENDED)
                .toList();
    }

    private static List<Integer> keptChapters(final List<JobEvent> events) {
        return events.stream()
                .filter(ChapterSummaryKept.class::isInstance)
                .map(ChapterSummaryKept.class::cast)
                .map(ChapterSummaryKept::chapter)
                .toList();
    }

    @Test
    void continue_finishedChapterWithoutSummary_writesItBeforeNextDraft() {
        final TestProject project = book(tempDir, CHAPTERS, false);
        stoppedAfter(project, model(), 2 * CHAPTER_SEGMENTS);
        switchSummaries(project, true);

        final Continued run = continueRun(project);

        final List<ChatRequest> before = beforeFirstDraft(run.requests());
        assertThat(before).hasSize(2).allMatch(SummaryBooks::isSummary);
        assertThat(userText(before.get(0))).contains(marker(1)).doesNotContain(marker(2));
        assertThat(userText(before.get(1))).contains(marker(2)).doesNotContain(marker(1));
        assertThat(userText(firstDraftOf(run.requests(), 3))).contains(EARLIER);
        assertThat(keptChapters(run.events())).startsWith(1, 2);
        assertThat(storedFor(project, CHAPTER_1)).isPresent();
        assertThat(storedFor(project, CHAPTER_2)).isPresent();
    }

    // Chapter 1 was summarised by the first run; chapter 2 was cut off half-way, so it has no summary until it ends.
    @Test
    void continue_chapterInProgress_none() {
        final TestProject project = book(tempDir, CHAPTERS, true);
        stoppedAfter(project, model(), CHAPTER_SEGMENTS + 2);
        assertThat(storedFor(project, CHAPTER_1))
                .as("the first run kept chapter 1's")
                .isPresent();
        assertThat(storedFor(project, CHAPTER_2))
                .as("chapter 2 was not finished")
                .isEmpty();

        final Continued run = continueRun(project);

        assertThat(beforeFirstDraft(run.requests())).isEmpty();
        assertThat(run.requests().stream().filter(SummaryBooks::isSummary))
                .noneMatch(request -> userText(request).contains(marker(1)));
        assertThat(run.events()).contains(new StepsPlanned(StepKind.SUMMARY, CHAPTERS, false));
        assertThat(endedSummarySteps(run.events()))
                .extracting(StepChanged::itemRef, StepChanged::result)
                .containsExactly(
                        tuple(CHAPTER_1, StepResult.NO_CALL),
                        tuple(CHAPTER_2, StepResult.DONE),
                        tuple(CHAPTER_3, StepResult.DONE),
                        tuple(CHAPTER_4, StepResult.NO_CALL));
    }

    @Test
    void continue_sourceChanged_rewritten() {
        final TestProject project = book(tempDir, CHAPTERS, true);
        stoppedAfter(project, model(), CHAPTER_SEGMENTS);
        project.stores()
                .summaries()
                .save(new ChapterSummary(
                        project.id(), CHAPTER_1, "Book.md", 0, CHAPTER_SEGMENTS, "Старий підсумок.", "stale"));

        final Continued run = continueRun(project);
        final Continued again = continueRun(project);

        final List<ChatRequest> before = beforeFirstDraft(run.requests());
        assertThat(before).singleElement().matches(SummaryBooks::isSummary);
        assertThat(userText(before.getFirst())).contains(marker(1));
        assertThat(storedFor(project, CHAPTER_1))
                .hasValueSatisfying(summary ->
                        assertThat(summary.sourceHash()).isNotEqualTo("stale").matches("[0-9a-f]{64}"));
        assertThat(again.requests())
                .as("the rewritten hash is the one the book gives")
                .noneMatch(SummaryBooks::isSummary);
    }

    @Test
    void continue_switchOff_none() {
        final TestProject project = book(tempDir, CHAPTERS, false);
        stoppedAfter(project, model(), 2 * CHAPTER_SEGMENTS);

        final Continued run = continueRun(project);

        assertThat(run.requests()).noneMatch(SummaryBooks::isSummary);
        assertThat(run.events())
                .filteredOn(StepsPlanned.class::isInstance)
                .map(StepsPlanned.class::cast)
                .noneMatch(planned -> planned.kind() == StepKind.SUMMARY);
        assertThat(isSummary(run.requests().getFirst())).isFalse();
    }
}
