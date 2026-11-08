package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
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
import ua.bookloom.persistence.memory.InMemoryStore.ProjectGlossary;

/**
 * Applies one chunk's decided segments, TM entries, deferrals, glossary additions and run record all together, under the
 * store's write lock, so a multi-record read can never observe half a chunk (ADR-0034, {@code design.md} D2).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryCheckpoint implements CheckpointPort {

    private final InMemoryStore store;

    @Override
    public Result<Integer> commit(final ChunkCommit commit) {
        Objects.requireNonNull(commit, "commit");
        final Lock writeLock = store.lock().writeLock();
        writeLock.lock();
        try {
            final Map<String, SegmentRecord> byId =
                    store.segments(commit.projectId()).byId();
            final AppError refusal = firstUnknownSegment(commit, byId);
            if (refusal != null) {
                return Result.err(refusal);
            }

            applyAll(commit, byId);
            final int glossaryApplied = applyGlossaryAdditions(commit);
            return Result.ok(logAndCount(commit, glossaryApplied));
        } catch (Exception cause) {
            return Result.err(internalError(cause));
        } finally {
            writeLock.unlock();
        }
    }

    private int logAndCount(final ChunkCommit commit, final int glossaryApplied) {
        final int applied = commit.segments().size()
                + commit.tmEntries().size()
                + commit.deferrals().size()
                + glossaryApplied;
        log.debug(
                "Commit applied projectId={} segments={} verdicts={} tmEntries={} deferrals={} resolvedDeferrals={} "
                        + "glossaryApplied={} glossarySkipped={}",
                commit.projectId(),
                commit.segments().size(),
                commit.verdictCount(),
                commit.tmEntries().size(),
                commit.deferrals().size(),
                commit.resolvedDeferralIds().size(),
                glossaryApplied,
                commit.glossaryAdditions().size() - glossaryApplied);
        return applied;
    }

    private @Nullable AppError firstUnknownSegment(final ChunkCommit commit, final Map<String, SegmentRecord> byId) {
        for (final SegmentRecord segment : commit.segments()) {
            if (!byId.containsKey(segment.segmentId())) {
                log.debug(
                        "Commit refused projectId={} unknownSegmentId={} code={}",
                        commit.projectId(),
                        segment.segmentId(),
                        ErrorCode.validation);
                return unknownSegment(commit.projectId(), segment.segmentId());
            }
        }
        return null;
    }

    private void applyAll(final ChunkCommit commit, final Map<String, SegmentRecord> byId) {
        for (final SegmentRecord segment : commit.segments()) {
            byId.put(segment.segmentId(), segment);
        }
        final Map<String, TmEntry> tm = store.tm(commit.projectId());
        for (final TmEntry entry : commit.tmEntries()) {
            tm.put(InMemoryStore.tmKey(entry.sourceHash(), entry.contextKey()), entry);
        }
        final Map<String, Deferral> deferrals = store.deferrals(commit.projectId());
        // Resolved first: a new proposal shares the key of the earlier one it supersedes.
        final Set<String> resolved = Set.copyOf(commit.resolvedDeferralIds());
        deferrals.values().removeIf(open -> resolved.contains(open.id()));
        for (final Deferral deferral : commit.deferrals()) {
            deferrals.putIfAbsent(
                    InMemoryStore.deferralKey(deferral.segmentId(), deferral.reason(), deferral.waitingOn()), deferral);
        }
        final RunRecord run = commit.run();
        if (run != null) {
            final RunRecord held = store.keepRun(run);
            log.debug(
                    "Commit run projectId={} runId={} state={} kept={}",
                    commit.projectId(),
                    run.runId(),
                    run.state(),
                    held.equals(run));
        }
    }

    private int applyGlossaryAdditions(final ChunkCommit commit) {
        final ProjectGlossary glossary = store.glossary(commit.projectId());
        final GlossaryIndex index = GlossaryIndex.of(glossary.allInInsertionOrder());
        int applied = 0;
        for (final GlossaryEntry entry : commit.glossaryAdditions()) {
            if (index.holderOf(entry).isPresent()
                    || GlossaryIndex.keysOf(entry).stream().anyMatch(glossary.removedKeys()::contains)) {
                log.trace("Glossary addition skipped projectId={} term={}", commit.projectId(), entry.term());
                continue;
            }
            glossary.put(entry);
            index.add(entry);
            applied++;
        }
        return applied;
    }

    private static AppError unknownSegment(final String projectId, final String segmentId) {
        return AppError.of(
                ErrorCode.validation,
                "Unknown segment",
                "No segment '" + segmentId + "' belongs to project '" + projectId + "'.");
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error =
                AppError.of(ErrorCode.internal, "Commit failure", "The chunk could not be committed.", null, cause);
        log.error("Unexpected checkpoint failure code={}", error.code(), cause);
        return error;
    }
}
