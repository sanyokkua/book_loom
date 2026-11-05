package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;

/**
 * Stores chapter summaries over the shared {@link InMemoryStore}, one per project and chapter key, listed in the order
 * each chapter was first saved, as the SQLite adapter lists them.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemorySummaryRepository implements SummaryRepository {

    private final InMemoryStore store;

    @Override
    public Result<ChapterSummary> save(final ChapterSummary summary) {
        Objects.requireNonNull(summary, "summary");
        log.debug(
                "Chapter summary save projectId={} chapter={} segments={}",
                summary.projectId(),
                summary.chapterKey(),
                summary.segmentCount());
        try {
            // Copy on write, so a reader never sees a map being changed; a put on a key the map holds keeps its place,
            // so a re-saved chapter stays where it was first saved.
            store.summaries().compute(summary.projectId(), (id, held) -> {
                final Map<String, ChapterSummary> chapters =
                        held == null ? new LinkedHashMap<>() : new LinkedHashMap<>(held);
                chapters.put(summary.chapterKey(), summary);
                return chapters;
            });
            return Result.ok(summary);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<ChapterSummary>> list(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final List<ChapterSummary> listed = List.copyOf(
                    store.summaries().getOrDefault(projectId, Map.of()).values());
            log.debug("Chapter summary list projectId={} count={}", projectId, listed.size());
            return Result.ok(listed);
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Summary storage failure", "The chapter summary could not be stored.", null, cause);
        log.error("Unexpected summary repository failure code={}", error.code(), cause);
        return error;
    }
}
