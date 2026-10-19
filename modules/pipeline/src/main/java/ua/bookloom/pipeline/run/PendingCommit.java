package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;

/**
 * The records a run has decided and not yet stored, with the memory entries, deferrals and glossary additions that
 * belong to them, and the ids it has already stored.
 *
 * <p>The checkpoint replaces whole records, so sending a stored id again would put the machine's record over an
 * edit the person saved while the run was paused. A record whose id was already committed is therefore never
 * committed a second time.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
@Slf4j
public final class PendingCommit {

    private final CheckpointPort checkpoint;
    private final String projectId;
    private final List<SegmentRecord> decided = new ArrayList<>();
    private final Map<String, TmEntry> memoryEntries = new HashMap<>();
    private final Map<String, Deferral> deferrals = new LinkedHashMap<>();
    private final List<GlossaryEntry> glossaryAdditions = new ArrayList<>();
    private final Set<String> committed = new HashSet<>();

    /**
     * Creates the pending set of one run.
     *
     * @param checkpoint the non-null port decisions are committed through
     * @param projectId the non-null id of the project the run decides
     */
    public PendingCommit(final CheckpointPort checkpoint, final String projectId) {
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
    }

    /**
     * Holds a decided record, and the memory entry its acceptance writes, until the next flush.
     *
     * @param record the non-null decided record
     * @param memoryEntry the translation-memory entry committed with the record, or {@code null} when it writes none
     */
    public void decided(final SegmentRecord record, @Nullable final TmEntry memoryEntry) {
        Objects.requireNonNull(record, "record");
        decided.add(record);
        if (memoryEntry != null) {
            memoryEntries.put(record.segmentId(), memoryEntry);
        }
    }

    /**
     * Holds the deferrals a decision leaves until the next flush; one already held under the same id is kept once.
     *
     * @param recorded the non-null deferrals to commit
     */
    public void deferred(final List<Deferral> recorded) {
        Objects.requireNonNull(recorded, "recorded");
        recorded.forEach(deferral -> deferrals.putIfAbsent(deferral.id(), deferral));
    }

    /**
     * Holds proposed glossary entries until the next flush, whose commit skips any term the person holds or removed.
     *
     * @param proposals the non-null entries to add
     */
    public void proposed(final List<GlossaryEntry> proposals) {
        Objects.requireNonNull(proposals, "proposals");
        glossaryAdditions.addAll(proposals);
    }

    /**
     * Finds a record decided since the last flush, so a later draft of the same chunk reads the decision the run has
     * made but not yet stored.
     *
     * @param segmentId the non-null segment id
     * @return the decided record if one is held, or empty when none is — never held again once flushed
     */
    public Optional<SegmentRecord> held(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        return decided.stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .reduce((earlier, later) -> later);
    }

    /**
     * Commits everything decided since the last flush, in the order it was decided, with the memory entries those
     * decisions write and the deferrals and glossary additions held since, except a record whose id an earlier flush
     * already committed and its entry.
     *
     * <p>The records leave the pending set before the port is called, so a commit that fails is not retried by the
     * flush that ends the run: a run that cannot store its decisions ends, and must not fail a second time doing so.
     *
     * @return the number of items the commit applied, {@code 0} when nothing was due, or the port's failure
     */
    public Result<Integer> flush() {
        final List<SegmentRecord> taken = List.copyOf(decided);
        final Map<String, TmEntry> entries = Map.copyOf(memoryEntries);
        final List<Deferral> dueDeferrals = List.copyOf(deferrals.values());
        final List<GlossaryEntry> additions = List.copyOf(glossaryAdditions);
        decided.clear();
        memoryEntries.clear();
        deferrals.clear();
        glossaryAdditions.clear();
        final List<SegmentRecord> due = taken.stream()
                .filter(record -> !committed.contains(record.segmentId()))
                .toList();
        final List<TmEntry> dueEntries = entriesOf(due, entries);
        logFlush(taken, due, dueEntries, dueDeferrals, additions);
        if (due.isEmpty() && dueDeferrals.isEmpty() && additions.isEmpty()) {
            return Result.ok(0);
        }
        final Result<Integer> result =
                checkpoint.commit(new ChunkCommit(projectId, due, dueEntries, additions, dueDeferrals));
        if (result.isOk()) {
            due.forEach(record -> committed.add(record.segmentId()));
        }
        return result;
    }

    private void logFlush(
            final List<SegmentRecord> taken,
            final List<SegmentRecord> due,
            final List<TmEntry> dueEntries,
            final List<Deferral> dueDeferrals,
            final List<GlossaryEntry> additions) {
        final Map<DeferralReason, Long> deferralsByKind = dueDeferrals.stream()
                .collect(Collectors.groupingBy(Deferral::reason, TreeMap::new, Collectors.counting()));
        log.debug(
                "Flushing decisions projectId={} decided={} tmEntries={} deferralsByKind={} glossaryAdditions={}"
                        + " withheldAsCommitted={}",
                projectId,
                due.size(),
                dueEntries.size(),
                deferralsByKind,
                additions.size(),
                taken.stream()
                        .map(SegmentRecord::segmentId)
                        .filter(committed::contains)
                        .toList());
    }

    private static List<TmEntry> entriesOf(final List<SegmentRecord> due, final Map<String, TmEntry> entries) {
        return due.stream()
                .map(record -> entries.get(record.segmentId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
