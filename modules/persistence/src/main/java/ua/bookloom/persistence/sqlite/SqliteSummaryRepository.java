package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.RollingSummary;

/** Stores a project's rolling summary as one {@code project_docs} document, each save replacing the one before. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteSummaryRepository implements SummaryRepository {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<RollingSummary> save(final RollingSummary summary) {
        Objects.requireNonNull(summary, "summary");
        log.debug("Summary save projectId={} version={}", summary.projectId(), summary.version());
        return db.write("summary save", h -> {
            ProjectDocs.write(h, codec, summary.projectId(), DocKind.SUMMARY, summary);
            return summary;
        });
    }

    @Override
    public Result<Optional<RollingSummary>> latest(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Summary latest projectId={}", projectId);
        return db.read(
                "summary latest", h -> ProjectDocs.read(h, codec, projectId, DocKind.SUMMARY, RollingSummary.class));
    }
}
