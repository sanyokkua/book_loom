package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.project.RunRecord;

/**
 * Stores the run started last as one {@code project_docs} document: {@link #latest} is all a reader asks, so a saved
 * record replaces the stored one when it is the same run or was started no earlier, and is otherwise dropped.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteRunRepository implements RunRepository {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<RunRecord> save(final RunRecord run) {
        Objects.requireNonNull(run, "run");
        log.debug("Run save projectId={} runId={} state={}", run.projectId(), run.runId(), run.state());
        return db.write("run save", h -> {
            saveIn(h, codec, run);
            return run;
        });
    }

    @Override
    public Result<Optional<RunRecord>> latest(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Run latest projectId={}", projectId);
        return db.read("run latest", h -> ProjectDocs.read(h, codec, projectId, DocKind.RUN, RunRecord.class));
    }

    /** Keeps the record when it is the stored run or started no earlier than it; the checkpoint shares it. */
    static void saveIn(final Handle h, final JsonCodec codec, final RunRecord run) {
        final Optional<RunRecord> held = ProjectDocs.read(h, codec, run.projectId(), DocKind.RUN, RunRecord.class);
        final boolean keep = held.map(stored ->
                        stored.runId().equals(run.runId()) || !run.startedAt().isBefore(stored.startedAt()))
                .orElse(true);
        log.debug("Run store projectId={} runId={} kept={}", run.projectId(), run.runId(), keep);
        if (keep) {
            ProjectDocs.write(h, codec, run.projectId(), DocKind.RUN, run);
        }
    }
}
