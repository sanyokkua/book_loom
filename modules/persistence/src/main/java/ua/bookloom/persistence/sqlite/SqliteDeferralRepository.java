package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.project.Deferral;

/**
 * Stores a project's open deferrals in {@code deferrals}, idempotent on project, segment, reason and waiting-on — the
 * table's key, with a missing waiting-on held as empty so two changed terms in one segment stay two deferrals.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteDeferralRepository implements DeferralRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO deferrals (project_id, segment_id, reason, waiting_on, json)
            VALUES (:project, :segment, :reason, :waitingOn, :json)
            ON CONFLICT (project_id, segment_id, reason, waiting_on) DO NOTHING
            """;
    private static final String SELECT_ONE = """
            SELECT json FROM deferrals
            WHERE project_id = :project AND segment_id = :segment AND reason = :reason AND waiting_on = :waitingOn
            """;
    private static final String NOT_WAITING = "";

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<Deferral> add(final Deferral deferral) {
        Objects.requireNonNull(deferral, "deferral");
        return db.write("deferral add", h -> {
            final boolean added = insertIfAbsent(h, codec, deferral.projectId(), deferral);
            log.debug(
                    "Deferral add projectId={} segmentId={} reason={} added={}",
                    deferral.projectId(),
                    deferral.segmentId(),
                    deferral.reason(),
                    added);
            return h.createQuery(SELECT_ONE)
                    .bind("project", deferral.projectId())
                    .bind("segment", deferral.segmentId())
                    .bind("reason", deferral.reason().name())
                    .bind("waitingOn", waitingOn(deferral.waitingOn()))
                    .mapTo(String.class)
                    .map(json -> codec.read(json, Deferral.class))
                    .one();
        });
    }

    @Override
    public Result<List<Deferral>> open(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Deferral open projectId={}", projectId);
        return db.read(
                "deferral open",
                h -> List.copyOf(h.createQuery("SELECT json FROM deferrals WHERE project_id = :project ORDER BY rowid")
                        .bind("project", projectId)
                        .mapTo(String.class)
                        .map(json -> codec.read(json, Deferral.class))
                        .list()));
    }

    @Override
    public Result<Boolean> resolve(final String projectId, final String deferralId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(deferralId, "deferralId");
        return db.write("deferral resolve", h -> {
            final boolean resolved = h.createUpdate(
                                    "DELETE FROM deferrals WHERE project_id = :project AND json_extract(json, '$.id') = :id")
                            .bind("project", projectId)
                            .bind("id", deferralId)
                            .execute()
                    > 0;
            log.debug("Deferral resolve projectId={} deferralId={} resolved={}", projectId, deferralId, resolved);
            return resolved;
        });
    }

    /**
     * Adds a deferral under a project unless one is open for its segment, reason and waiting-on; the checkpoint
     * shares it.
     *
     * @return {@code true} if it was added, {@code false} if one was already open
     */
    static boolean insertIfAbsent(
            final Handle h, final JsonCodec codec, final String projectId, final Deferral deferral) {
        return h.createUpdate(INSERT_IF_ABSENT)
                        .bind("project", projectId)
                        .bind("segment", deferral.segmentId())
                        .bind("reason", deferral.reason().name())
                        .bind("waitingOn", waitingOn(deferral.waitingOn()))
                        .bind("json", codec.write(deferral))
                        .execute()
                > 0;
    }

    private static String waitingOn(@Nullable final String waitingOn) {
        return waitingOn == null ? NOT_WAITING : waitingOn;
    }
}
