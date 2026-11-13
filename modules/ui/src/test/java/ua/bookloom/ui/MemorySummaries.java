package ua.bookloom.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;

/** The chapter summaries in a map, first saved first per project, without :persistence (not a :ui edge). */
public final class MemorySummaries implements SummaryRepository {

    private final Map<String, Map<String, ChapterSummary>> byProject = new ConcurrentHashMap<>();

    @Override
    public Result<ChapterSummary> save(final ChapterSummary summary) {
        byProject.compute(summary.projectId(), (id, held) -> {
            final Map<String, ChapterSummary> chapters =
                    held == null ? new LinkedHashMap<>() : new LinkedHashMap<>(held);
            chapters.put(summary.chapterKey(), summary);
            return chapters;
        });
        return Result.ok(summary);
    }

    @Override
    public Result<List<ChapterSummary>> list(final String projectId) {
        return Result.ok(List.copyOf(byProject.getOrDefault(projectId, Map.of()).values()));
    }

    /** Saves the summary of the chapter {@code chapterKey} ({@code <unit>:<first segment>}) of {@code projectId}. */
    public void put(final String projectId, final String chapterKey, final String text) {
        final String unitId = chapterKey.substring(0, chapterKey.indexOf(':'));
        save(new ChapterSummary(projectId, chapterKey, unitId, 0, 1, text, "hash-" + chapterKey));
    }
}
