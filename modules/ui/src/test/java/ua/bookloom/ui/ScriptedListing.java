package ua.bookloom.ui;

import static ua.bookloom.ui.ScriptedProjects.projectOf;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectSummary;

/** What {@link ScriptedProjectService#list()} answers, and a record of who asked and on which thread. */
final class ScriptedListing {

    private final AtomicInteger calls = new AtomicInteger();
    private final List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
    private final Map<Path, Integer> decided = new ConcurrentHashMap<>();
    private volatile @Nullable AppError failure;
    private volatile Supplier<List<ProjectSummary>> answer = this::decidedSummaries;

    Result<List<ProjectSummary>> list() {
        calls.incrementAndGet();
        onFxThread.add(Platform.isFxApplicationThread());
        final AppError failed = failure;
        return failed != null ? Result.err(failed) : Result.ok(answer.get());
    }

    void decide(final Path source, final int count) {
        decided.put(Objects.requireNonNull(source, "source"), count);
    }

    void failWith(final AppError error) {
        failure = Objects.requireNonNull(error, "error");
    }

    void willList(final List<ProjectSummary> summaries) {
        final List<ProjectSummary> fixed = List.copyOf(summaries);
        answer = () -> fixed;
    }

    int calls() {
        return calls.get();
    }

    List<Boolean> onFxThread() {
        return List.copyOf(onFxThread);
    }

    private List<ProjectSummary> decidedSummaries() {
        return decided.entrySet().stream()
                .map(e -> new ProjectSummary(
                        projectOf(ScriptedProjectService.ID_PREFIX + e.getKey(), e.getKey()),
                        e.getValue(),
                        e.getValue(),
                        null,
                        null))
                .toList();
    }
}
