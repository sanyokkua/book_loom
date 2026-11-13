package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.SummaryBooks.CHAPTER_SEGMENTS;
import static ua.bookloom.pipeline.SummaryBooks.EARLIER;
import static ua.bookloom.pipeline.SummaryBooks.book;
import static ua.bookloom.pipeline.SummaryBooks.firstDraftOf;
import static ua.bookloom.pipeline.SummaryBooks.model;
import static ua.bookloom.pipeline.SummaryBooks.stored;
import static ua.bookloom.pipeline.SummaryBooks.switchSummaries;
import static ua.bookloom.pipeline.SummaryBooks.userText;
import static ua.bookloom.pipeline.TranslationJobTestSupport.await;
import static ua.bookloom.pipeline.TranslationJobTestSupport.awaitPaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.capturePaused;
import static ua.bookloom.pipeline.TranslationJobTestSupport.executor;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * The Chapter summaries switch changed on the Book Brief while a run is paused applies from the next Continue, which
 * resumes the same job ({@code specs/book-brief/spec.md}, the story shape and the chapter-summaries switch).
 */
class PausedSummariesSwitchTest {

    private static final int CHAPTERS = 3;
    // With the switch on, chapter 1's four drafts and its summary come first; the held request is chapter 2's second.
    private static final int SECOND_DRAFT_OF_CHAPTER_2_AFTER_SUMMARY = CHAPTER_SEGMENTS + 3;
    // With the switch off there is no summary call between the chapters.
    private static final int SECOND_DRAFT_OF_CHAPTER_2 = CHAPTER_SEGMENTS + 2;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    private record Resumed(ScriptedChatModel model, List<JobEvent> events, JobReport report, TestProject project) {}

    // Holds the given draft of chapter 2, pauses, stores the switch as given and resumes the same job.
    private Resumed pausedInChapter2(final boolean onAtStart, final int heldRequest) {
        final TestProject project = book(tempDir, CHAPTERS, onAtStart);
        final ScriptedChatModel model = model().blockNthRequest(heldRequest);
        final TranslationJobImpl translation = job(project, model);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();
        final LinkedBlockingQueue<Paused> pauses = new LinkedBlockingQueue<>();
        translation.subscribe(event -> {
            events.add(event);
            capturePaused(pauses, event);
        });
        final Future<Result<JobReport>> run = executor().submit(translation::run);
        model.awaitRequests(heldRequest);
        final ChatRequest held = model.requests().get(heldRequest - 1);
        assertThat(held.callKind()).as("the held request is a draft").isEqualTo(CallKind.DRAFT);
        assertThat(userText(held)).as("the held draft is chapter 2's").contains(SummaryBooks.marker(2));
        translation.pause();
        awaitPaused(pauses);
        switchSummaries(project, !onAtStart);
        translation.resume();
        return new Resumed(model, events, report(await(run)), project);
    }

    private static List<Integer> keptChapters(final List<JobEvent> events) {
        return events.stream()
                .filter(ChapterSummaryKept.class::isInstance)
                .map(event -> ((ChapterSummaryKept) event).chapter())
                .toList();
    }

    private static int plannedSummarySteps(final List<JobEvent> events) {
        return events.stream()
                .filter(StepsPlanned.class::isInstance)
                .map(StepsPlanned.class::cast)
                .filter(planned -> planned.kind() == StepKind.SUMMARY)
                .mapToInt(StepsPlanned::added)
                .sum();
    }

    private static long endedSummarySteps(final List<JobEvent> events) {
        return events.stream()
                .filter(StepChanged.class::isInstance)
                .map(StepChanged.class::cast)
                .filter(step -> step.kind() == StepKind.SUMMARY && step.state() == StepState.ENDED)
                .count();
    }

    @Test
    void resume_switchTurnedOffWhilePaused_writesNoFurtherSummary() {
        final Resumed resumed = pausedInChapter2(true, SECOND_DRAFT_OF_CHAPTER_2_AFTER_SUMMARY);

        assertThat(resumed.report().end()).isEqualTo(JobState.COMPLETED);
        assertThat(stored(resumed.project()))
                .extracting(ChapterSummary::chapterKey)
                .containsExactly("Book.md:0");
        assertThat(keptChapters(resumed.events())).containsExactly(1);
        assertThat(resumed.model().requests().stream().filter(SummaryBooks::isSummary))
                .hasSize(1);
        assertThat(userText(firstDraftOf(resumed.model().requests(), 3))).doesNotContain(EARLIER);
        assertThat(endedSummarySteps(resumed.events()))
                .as("every planned summary step ends")
                .isEqualTo(plannedSummarySteps(resumed.events()));
    }

    @Test
    void resume_switchTurnedOnWhilePaused_summarisesFinishedChapters() {
        final Resumed resumed = pausedInChapter2(false, SECOND_DRAFT_OF_CHAPTER_2);

        assertThat(resumed.report().end()).isEqualTo(JobState.COMPLETED);
        assertThat(keptChapters(resumed.events())).containsExactly(1, 2);
        assertThat(userText(firstDraftOf(resumed.model().requests(), 3)))
                .contains(EARLIER)
                .contains("Chapter 1: ")
                .contains("Chapter 2: ");
        assertThat(plannedSummarySteps(resumed.events())).isEqualTo(CHAPTERS);
        assertThat(endedSummarySteps(resumed.events())).isEqualTo(CHAPTERS);
    }
}
