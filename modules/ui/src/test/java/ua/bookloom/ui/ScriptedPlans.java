package ua.bookloom.ui;

import static ua.bookloom.ui.ScriptedProjects.notScripted;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ChapterTally;

/**
 * What {@link ScriptedProjectService#plan} and {@link ScriptedProjectService#chapterTally} answer, and a record of who
 * asked for a plan and on which thread. A chapter tally answers that no chapter is finished until a test scripts one.
 */
final class ScriptedPlans {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
    private volatile Result<BookPlan> answer = notScripted();
    private volatile ChapterTally tally = new ChapterTally(0, 0, 0, 0);

    Result<BookPlan> plan(final String projectId) {
        calls.add(Objects.requireNonNull(projectId, "projectId"));
        onFxThread.add(Platform.isFxApplicationThread());
        return answer;
    }

    void answer(final Result<BookPlan> next) {
        answer = Objects.requireNonNull(next, "next");
    }

    Result<ChapterTally> tally(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return Result.ok(tally);
    }

    void tally(final ChapterTally next) {
        tally = Objects.requireNonNull(next, "next");
    }

    List<String> calls() {
        return List.copyOf(calls);
    }

    List<Boolean> onFxThread() {
        return List.copyOf(onFxThread);
    }
}
