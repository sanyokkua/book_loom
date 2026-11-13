package ua.bookloom.pipeline.proofread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.run.RunEnd;
import ua.bookloom.pipeline.run.RunStores;

/**
 * Closes a finished round (design D15 "Resume"): a kept rewrite can answer the very mark its window was read for, so
 * the windows a Continue plans afterwards are shaped otherwise — a window shrinks, loses a reason, or a neighbour's text
 * changed — and their fingerprints no longer match what the round stored. Each window planned again now, whose every
 * focus segment this round read or skipped for at least the reasons it has now, is stamped read under the fingerprint
 * it has now, so a Continue does not read the same paragraphs again for nothing new. A window with a new reason, or a
 * focus segment the round did not answer, keeps its old stamp and is read by the next Continue.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
final class ReshapedWindows {

    private final Map<String, List<String>> covered = new HashMap<>();
    private final int round;

    /**
     * Starts the record of one round.
     *
     * @param round the round the windows are read in
     */
    ReshapedWindows(final int round) {
        this.round = round;
    }

    /**
     * Notes a window the round answered, read or skipped as already read, with the reasons it was read for.
     *
     * @param window the non-null window
     */
    void covered(final ProofreadWindow window) {
        window.focusIds().forEach(id -> covered.put(id, window.reasons().getOrDefault(id, List.of())));
    }

    /**
     * Stamps the windows planned again after the round that ask nothing the round did not answer.
     *
     * @param planned the non-null windows a Continue would plan for this round now
     * @param reading the non-null reading, whose records are replaced by the restamped ones
     * @param stores the non-null stores the restamped records are committed to
     * @param projectId the non-null project
     * @return empty once stamped, or a failed end when the commit failed
     */
    Optional<RunEnd> stamp(
            final List<ProofreadWindow> planned,
            final WindowReading reading,
            final RunStores stores,
            final String projectId) {
        final List<SegmentRecord> restamped = new ArrayList<>();
        planned.stream()
                .filter(window -> isAnswered(window, reading) && !reading.isRead(round, window))
                .forEach(window -> restamped.addAll(restamped(window, reading)));
        log.debug(
                "Proofread round {} planned again windows={} covered={} restamped segments={}",
                round,
                planned.size(),
                covered.size(),
                restamped.size());
        if (restamped.isEmpty()) {
            return Optional.empty();
        }
        final Result<Integer> committed = stores.checkpoint()
                .commit(new ChunkCommit(projectId, restamped, List.of(), List.of(), List.of(), List.of(), null));
        if (committed.isErr()) {
            final AppError error = Objects.requireNonNull(committed.error(), "error");
            log.debug("Proofread round {} restamp not committed code={}", round, error.code());
            return Optional.of(new RunEnd(JobState.FAILED, error));
        }
        restamped.forEach(record -> reading.records().put(record.segmentId(), record));
        return Optional.empty();
    }

    // Every focus segment was answered this round for at least the reasons the window gives it now.
    private boolean isAnswered(final ProofreadWindow window, final WindowReading reading) {
        final boolean answered = window.focusIds().stream().allMatch(id -> {
            final @Nullable List<String> readFor = covered.get(id);
            return readFor != null
                    && reading.record(id).proofread() != null
                    && readFor.containsAll(window.reasons().getOrDefault(id, List.of()));
        });
        log.trace("Proofread window {} answered this round={}", window.label(), answered);
        return answered;
    }

    private List<SegmentRecord> restamped(final ProofreadWindow window, final WindowReading reading) {
        final String now = reading.fingerprint(round, window);
        log.debug("Proofread window {} reshaped by this round's rewrites; restamped as read", window.label());
        return window.focusIds().stream()
                .map(reading::record)
                .map(record -> record.withProofread(Objects.requireNonNull(record.proofread(), record.segmentId())
                        .restamped(now)))
                .toList();
    }
}
