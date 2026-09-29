package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.CheckpointPort;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.persistence.PersistenceModule;

/**
 * The decisions a run has made and not yet stored, with the memory entries they write, and the promise that a stored
 * one is never stored again.
 */
class PendingCommitTest {

    private static final String PROJECT = "p1";
    private static final String EDIT = "Він рвучко відчинив двері.";

    private final List<ChunkCommit> commits = new CopyOnWriteArrayList<>();
    private SegmentRepository segments;
    private PendingCommit pending;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new PersistenceModule());
        segments = injector.getInstance(SegmentRepository.class);
        final CheckpointPort checkpoint = injector.getInstance(CheckpointPort.class);
        segments.saveAll(PROJECT, List.of(record("s0", SegmentStatus.PENDING), record("s1", SegmentStatus.PENDING)));
        pending = new PendingCommit(recording(checkpoint), PROJECT);
    }

    // Committing s0 again with its old whole record would erase the edit the person saved while the run was paused.
    @Test
    void flush_afterCommittedId_neverIncludesItAgain() {
        pending.decided(record("s0", SegmentStatus.ACCEPTED), null);
        pending.flush();
        segments.update(
                PROJECT, "s0", stored -> stored.withUserTarget(EDIT, EDIT).withStatus(SegmentStatus.REVISED));
        pending.decided(record("s1", SegmentStatus.ACCEPTED), null);

        pending.flush();

        assertThat(commits).hasSize(2);
        assertThat(commits.getLast().segments())
                .extracting(SegmentRecord::segmentId)
                .containsExactly("s1");
        final SegmentRecord edited = stored("s0");
        assertThat(edited.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(edited.userTarget()).isEqualTo(EDIT);
    }

    // A decision made twice for one id must not overwrite what the first commit and any later edit left.
    @Test
    void flush_committedIdDecidedAgain_withholdsIt() {
        pending.decided(record("s0", SegmentStatus.ACCEPTED), null);
        pending.flush();
        pending.decided(record("s0", SegmentStatus.FLAGGED), null);

        final Result<Integer> flushed = pending.flush();

        assertThat(flushed.isOk()).isTrue();
        assertThat(commits).hasSize(1);
        assertThat(stored("s0").status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void flush_nothingPending_commitsNothing() {
        final Result<Integer> flushed = pending.flush();

        assertThat(flushed.data()).isZero();
        assertThat(commits).isEmpty();
    }

    @Test
    void flush_twoDecisions_commitsBothInOneCommitInDecisionOrder() {
        pending.decided(record("s1", SegmentStatus.ACCEPTED), null);
        pending.decided(record("s0", SegmentStatus.FLAGGED), null);

        pending.flush();

        assertThat(commits).hasSize(1);
        assertThat(commits.getFirst().segments())
                .extracting(SegmentRecord::segmentId)
                .containsExactly("s1", "s0");
    }

    @Test
    void flush_decisionsWithMemoryEntries_commitsTheEntriesWithTheirRecords() {
        pending.decided(record("s0", SegmentStatus.ACCEPTED), entry("s0"));
        pending.decided(record("s1", SegmentStatus.FLAGGED), null);

        pending.flush();

        assertThat(commits.getFirst().tmEntries()).extracting(TmEntry::id).containsExactly("s0-entry");
    }

    @Test
    void flush_committedIdDecidedAgain_withholdsItsMemoryEntryToo() {
        pending.decided(record("s0", SegmentStatus.ACCEPTED), entry("s0"));
        pending.flush();
        pending.decided(record("s0", SegmentStatus.ACCEPTED), entry("s0"));
        pending.decided(record("s1", SegmentStatus.ACCEPTED), entry("s1"));

        pending.flush();

        assertThat(commits.getLast().tmEntries()).extracting(TmEntry::id).containsExactly("s1-entry");
    }

    private static TmEntry entry(final String segmentId) {
        return new TmEntry(segmentId + "-entry", PROJECT, segmentId + "-hash", "context", "Yes.", "Так.");
    }

    private SegmentRecord stored(final String segmentId) {
        return Objects.requireNonNull(segments.find(PROJECT, segmentId).data(), "find")
                .orElseThrow();
    }

    private CheckpointPort recording(final CheckpointPort real) {
        return commit -> {
            commits.add(commit);
            return real.commit(commit);
        };
    }

    private static SegmentRecord record(final String segmentId, final SegmentStatus status) {
        return new SegmentRecord(
                PROJECT,
                segmentId,
                "u0",
                0,
                SegmentKind.PARAGRAPH,
                status,
                null,
                null,
                null,
                null,
                0.0,
                null,
                new ArrayList<>(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }
}
