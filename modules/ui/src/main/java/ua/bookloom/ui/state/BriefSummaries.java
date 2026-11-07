package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ChapterTally;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.StoryShape;

/**
 * The story shape and the Chapter summaries switch of the brief (design D6): the person's pick of the shape confirms
 * it, a turn of the switch forces it, and turning it on asks the project how many finished chapters the next Continue
 * would summarise first, so the Book Brief can say what the switch costs. The count is asked on the background
 * executor and published on the FX thread; a count that arrives after the switch was turned again, or after another
 * book was opened, is dropped. FX thread only.
 */
@Slf4j
public final class BriefSummaries {

    private final CurrentProject project;
    private final ProjectService projects;
    private final ExecutorService executor;
    private final BriefPolicyKeys.Edit edit;
    private final ReadOnlyIntegerWrapper pending = new ReadOnlyIntegerWrapper(0);
    // Raised by every turn of the switch and every opened book, so only the newest count is shown.
    private int asked;

    BriefSummaries(
            final CurrentProject project,
            final ProjectService projects,
            final ExecutorService executor,
            final BriefPolicyKeys.Edit edit) {
        this.project = Objects.requireNonNull(project, "project");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.edit = Objects.requireNonNull(edit, "edit");
        project.book().addListener((observed, was, now) -> forget());
    }

    /**
     * How many finished chapters will be summarised before the next passage, as counted when the person last turned
     * the switch on.
     *
     * @return a read-only property; 0 until the switch is turned on with finished chapters, and again once it is turned
     *     off or another book is opened
     */
    public ReadOnlyIntegerProperty pending() {
        return pending.getReadOnlyProperty();
    }

    /**
     * Sets whether the chapters tell one story or separate ones, which confirms the shape; an unforced switch follows
     * it.
     *
     * @param shape the person's answer
     */
    public void setStoryShape(final StoryShape shape) {
        Objects.requireNonNull(shape, "shape");
        edit.apply("story shape", shape.name(), BriefField.STORY_SHAPE, brief -> brief.withStoryShape(shape));
    }

    /**
     * Forces the Chapter summaries switch; no later suggestion changes it. Turning it on counts the finished chapters
     * it will summarise first.
     *
     * @param on whether chapter summaries are written
     */
    public void setChapterSummaries(final boolean on) {
        edit.apply("chapter summaries", Boolean.toString(on), null, brief -> brief.withChapterSummaries(on));
        asked++;
        pending.set(0);
        final OpenedBook book = project.book().get();
        if (on && book != null) {
            count(book.projectId(), asked);
        }
    }

    private void count(final String projectId, final int ask) {
        log.debug("counting the chapters to summarise of project {}", projectId);
        try {
            executor.execute(() -> {
                final Result<ChapterTally> tally = projects.chapterTally(projectId);
                Platform.runLater(() -> counted(projectId, ask, tally));
            });
        } catch (RejectedExecutionException _) {
            log.debug("the chapters of project {} are not counted: the executor is shut down", projectId);
        }
    }

    private void counted(final String projectId, final int ask, final Result<ChapterTally> tally) {
        final ChapterTally counted = tally.data();
        if (ask != asked) {
            log.debug("the chapter count of project {} is dropped: the switch was turned again", projectId);
            return;
        }
        if (counted == null) {
            log.debug("the chapters of project {} could not be counted; no line is shown", projectId);
            return;
        }
        log.debug(
                "project {} has {} chapters, {} finished, {} to summarise",
                projectId,
                counted.chapters(),
                counted.finished(),
                counted.toSummarise());
        pending.set(counted.toSummarise());
    }

    private void forget() {
        asked++;
        pending.set(0);
    }
}
