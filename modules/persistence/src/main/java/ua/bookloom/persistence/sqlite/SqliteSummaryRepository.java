package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.ChapterSummary;

/**
 * Stores each chapter summary as one {@code project_docs} document named by its chapter key, so removing the project
 * removes them by the schema's cascade.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteSummaryRepository implements SummaryRepository {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<ChapterSummary> save(final ChapterSummary summary) {
        Objects.requireNonNull(summary, "summary");
        log.debug(
                "Chapter summary save projectId={} chapter={} segments={}",
                summary.projectId(),
                summary.chapterKey(),
                summary.segmentCount());
        return db.write("chapter summary save", h -> {
            ProjectDocs.write(h, codec, summary.projectId(), DocKind.CHAPTER_SUMMARY, summary.chapterKey(), summary);
            return summary;
        });
    }

    @Override
    public Result<List<ChapterSummary>> list(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        final Result<List<ChapterSummary>> listed = db.read(
                "chapter summary list",
                h -> ProjectDocs.readAllInWriteOrder(
                        h, codec, projectId, DocKind.CHAPTER_SUMMARY, ChapterSummary.class));
        log.debug(
                "Chapter summary list projectId={} count={}",
                projectId,
                listed.isOk() ? Objects.requireNonNull(listed.data()).size() : -1);
        return listed;
    }
}
