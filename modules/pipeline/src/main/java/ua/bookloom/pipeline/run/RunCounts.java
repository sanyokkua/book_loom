package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ChunkPosition;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobStage;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/**
 * The figures every progress snapshot carries: the decided and pending counts, how many of the accepted needed a
 * repair and how many were kept as they are with no call, and the chunk the run is in. They are read from the store at each read of the work list, so a run that
 * resumes a stopped one starts from the project's own counts, and they move per decision in between. A record kept as
 * source by choice is in none of them.
 *
 * <p>Used from the job thread only, which is why the state is plain fields.
 */
@Slf4j
final class RunCounts {

    private int accepted;
    private int flagged;
    private int pending;
    private int autoAccepted;
    private int repairedAccepted;
    private int keptVerbatim;
    private int chunk;
    private int chunks;

    /**
     * Starts the counts over from the store, outside any chunk.
     *
     * @param counts the stored per-status counts, kept records left out
     * @param records every stored record of the project
     * @param kept the auxiliary kinds the brief keeps as source
     */
    void reset(final SegmentCounts counts, final List<SegmentRecord> records, final Set<SegmentKind> kept) {
        accepted = counts.accepted() + counts.revised();
        flagged = counts.flagged();
        pending = counts.pending();
        repairedAccepted = countAccepted(records, kept, SegmentPath.REPAIRED);
        keptVerbatim = countAccepted(records, kept, SegmentPath.VERBATIM);
        autoAccepted = accepted - repairedAccepted - keptVerbatim;
        chunk = 0;
        chunks = 0;
    }

    /**
     * Moves one segment from pending to its decision.
     *
     * @param status {@code ACCEPTED} or {@code FLAGGED}
     * @param path how it got there; a repaired acceptance and one kept as it is are each counted apart from the rest
     */
    void decided(final SegmentStatus status, final SegmentPath path) {
        pending--;
        if (status != SegmentStatus.ACCEPTED) {
            flagged++;
            return;
        }
        accepted++;
        switch (path) {
            case REPAIRED -> repairedAccepted++;
            case VERBATIM -> keptVerbatim++;
            default -> autoAccepted++;
        }
        log.debug(
                "Counted acceptance path={} auto={} repaired={} verbatim={}",
                path,
                autoAccepted,
                repairedAccepted,
                keptVerbatim);
    }

    /**
     * Records the chunk the run has entered.
     *
     * @param position the 1-based chunk within its unit
     * @param count the unit's chunk count
     */
    void enterChunk(final int position, final int count) {
        chunk = position;
        chunks = count;
        log.debug("Entered chunk {}/{}", position, count);
    }

    ChunkPosition position(final int section, final int sections) {
        return new ChunkPosition(section, sections, chunk, chunks);
    }

    JobProgress progress(final JobStage stage, final int section, final int sections) {
        log.debug(
                "Built progress stage={} section={}/{} chunk={}/{} accepted={} auto={} repaired={} verbatim={}"
                        + " flagged={} pending={}",
                stage,
                section,
                sections,
                chunk,
                chunks,
                accepted,
                autoAccepted,
                repairedAccepted,
                keptVerbatim,
                flagged,
                pending);
        return new JobProgress(
                stage,
                section,
                sections,
                accepted,
                flagged,
                pending,
                chunk,
                chunks,
                autoAccepted,
                repairedAccepted,
                keptVerbatim);
    }

    int accepted() {
        return accepted;
    }

    int flagged() {
        return flagged;
    }

    int pending() {
        return pending;
    }

    private static int countAccepted(
            final List<SegmentRecord> records, final Set<SegmentKind> kept, final SegmentPath path) {
        return (int) records.stream()
                .filter(record -> isAccepted(record.status()) && !record.isKeptAsSource(kept))
                .filter(record -> record.path() == path)
                .count();
    }

    private static boolean isAccepted(final SegmentStatus status) {
        return status == SegmentStatus.ACCEPTED || status == SegmentStatus.REVISED;
    }
}
