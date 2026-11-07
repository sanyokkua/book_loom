package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.RunRecord;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.persistence.GlossaryIndex;

/**
 * Writes one chunk's segment decisions, translation-memory entries, deferrals, glossary additions and run record in one write
 * transaction, so a failure anywhere rolls the whole chunk back and a reader never sees half of it (ADR-0034's
 * commit rule, {@code design.md} D9). The glossary additions are a read-modify-write of the stored glossary inside
 * the same transaction, so they never overwrite a person's edit.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteCheckpoint implements CheckpointPort {

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<Integer> commit(final ChunkCommit commit) {
        Objects.requireNonNull(commit, "commit");
        log.debug(
                "Commit projectId={} segments={} verdicts={} tmEntries={} deferrals={} resolvedDeferrals={} "
                        + "glossaryAdditions={}",
                commit.projectId(),
                commit.segments().size(),
                commit.verdictCount(),
                commit.tmEntries().size(),
                commit.deferrals().size(),
                commit.resolvedDeferralIds().size(),
                commit.glossaryAdditions().size());
        return db.write("checkpoint commit", h -> commitIn(h, commit)).flatMap(Function.identity());
    }

    private Result<Integer> commitIn(final Handle h, final ChunkCommit commit) {
        final String projectId = commit.projectId();
        final Optional<String> unknown = firstUnknownSegment(h, commit);
        if (unknown.isPresent()) {
            log.debug(
                    "Commit refused projectId={} unknownSegmentId={} code={}",
                    projectId,
                    unknown.get(),
                    ErrorCode.validation);
            return Result.err(SqliteSegmentRepository.unknownSegment(projectId, unknown.get()));
        }
        writeRows(h, commit);
        final int glossaryApplied = applyGlossaryAdditions(h, commit);
        log.debug(
                "Commit applied projectId={} glossaryApplied={} glossarySkipped={}",
                projectId,
                glossaryApplied,
                commit.glossaryAdditions().size() - glossaryApplied);
        return Result.ok(commit.segments().size()
                + commit.tmEntries().size()
                + commit.deferrals().size()
                + glossaryApplied);
    }

    private void writeRows(final Handle h, final ChunkCommit commit) {
        final String projectId = commit.projectId();
        for (final SegmentRecord segment : commit.segments()) {
            SqliteSegmentRepository.writeRow(h, codec, projectId, segment.segmentId(), segment);
        }
        for (final TmEntry entry : commit.tmEntries()) {
            SqliteTmRepository.upsert(h, codec, projectId, entry);
        }
        for (final Deferral deferral : commit.deferrals()) {
            SqliteDeferralRepository.insertIfAbsent(h, codec, projectId, deferral);
        }
        for (final String deferralId : commit.resolvedDeferralIds()) {
            SqliteDeferralRepository.resolveIn(h, projectId, deferralId);
        }
        final RunRecord run = commit.run();
        if (run != null) {
            SqliteRunRepository.saveIn(h, codec, run);
        }
    }

    private static Optional<String> firstUnknownSegment(final Handle h, final ChunkCommit commit) {
        if (commit.segments().isEmpty()) {
            return Optional.empty();
        }
        final Set<String> stored =
                new HashSet<>(h.createQuery("SELECT segment_id FROM segments WHERE project_id = :project")
                        .bind("project", commit.projectId())
                        .mapTo(String.class)
                        .list());
        return commit.segments().stream()
                .map(SegmentRecord::segmentId)
                .filter(id -> !stored.contains(id))
                .findFirst();
    }

    /**
     * Adds each new term; one whose lemma or a form is held as a lemma or a form, ignoring case, or was removed by the
     * person is skipped.
     */
    private int applyGlossaryAdditions(final Handle h, final ChunkCommit commit) {
        if (commit.glossaryAdditions().isEmpty()) {
            return 0;
        }
        GlossaryDocument doc = SqliteGlossaryRepository.load(h, codec, commit.projectId());
        final GlossaryIndex index = GlossaryIndex.of(doc.entries());
        int applied = 0;
        for (final GlossaryEntry entry : commit.glossaryAdditions()) {
            if (index.holderOf(entry).isPresent()
                    || GlossaryIndex.keysOf(entry).stream().anyMatch(doc::wasRemoved)) {
                log.trace("Glossary addition skipped projectId={} term={}", commit.projectId(), entry.term());
                continue;
            }
            doc = doc.added(entry);
            index.add(entry);
            applied++;
        }
        if (applied > 0) {
            SqliteGlossaryRepository.store(h, codec, commit.projectId(), doc);
        }
        return applied;
    }
}
