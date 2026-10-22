package ua.bookloom.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.RollingSummary;

/** The rolling summaries in a map, newest per project, without :persistence (not a :ui edge). */
public final class MemorySummaries implements SummaryRepository {

    private final Map<String, RollingSummary> latest = new ConcurrentHashMap<>();

    @Override
    public Result<RollingSummary> save(final RollingSummary summary) {
        latest.put(summary.projectId(), summary);
        return Result.ok(summary);
    }

    @Override
    public Result<Optional<RollingSummary>> latest(final String projectId) {
        return Result.ok(Optional.ofNullable(latest.get(projectId)));
    }

    /** Saves a summary of {@code projectId} whose texts are the given ones at {@code version}. */
    public void put(final String projectId, final String source, final String target, final int version) {
        save(new RollingSummary(projectId, null, source, target, version, null, 0));
    }
}
