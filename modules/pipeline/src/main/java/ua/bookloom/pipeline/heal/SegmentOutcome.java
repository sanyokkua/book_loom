package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;

/**
 * One segment's decision from the quality loop ({@code specs/quality-gates/spec.md} "Record each segment's findings
 * for review and repair").
 *
 * @param segmentId the decided segment's id
 * @param status {@link SegmentStatus#ACCEPTED} or {@link SegmentStatus#FLAGGED}; the quality loop never produces
 *     another status
 * @param machineTarget the last target that passed every hard gate, restored through {@code DocumentPort.unmask};
 *     {@code null} when none ever did
 * @param maskedMachineTarget {@code machineTarget}'s masked form, still carrying its {@code ⟦gN⟧} tokens;
 *     {@code null} exactly when {@code machineTarget} is
 * @param confidence the last evaluated target's blended confidence, in {@code [0,1]}
 * @param judgeScore the score of the verdict that last decided this segment, or {@code null} when the judge was
 *     off, never ran for it, or that verdict was unreadable
 * @param findings every finding recorded against this segment, de-duplicated
 * @param path {@link SegmentPath#DRAFT} when decided with no round used — accepted, flagged at once by the draft
 *     step, or flagged by a review retry, which runs none; {@link SegmentPath#REPAIRED} when at least one self-heal round ran, whatever the final status
 * @param repairRounds how many self-heal rounds this segment used
 * @param flagReason the error a {@code FlagNow} self-heal reply or a {@code FlaggedAtOnce} draft outcome flagged
 *     this segment with; {@code null} for every other FLAGGED or ACCEPTED segment
 * @param rejectedTarget the model's last refused reply in masked form, kept only when no target passed every hard
 *     gate, so review shows the model's words instead of the source; {@code null} otherwise
 */
public record SegmentOutcome(
        String segmentId,
        SegmentStatus status,
        @Nullable String machineTarget,
        @Nullable String maskedMachineTarget,
        double confidence,
        @Nullable Double judgeScore,
        List<QaFinding> findings,
        SegmentPath path,
        int repairRounds,
        @Nullable AppError flagReason,
        @Nullable String rejectedTarget) {

    /** Validates the invariants a caller is entitled to assume and defensively copies the list component. */
    public SegmentOutcome {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(path, "path");
        findings = List.copyOf(findings);
    }

    /** A decision with no rejected reply kept. */
    public SegmentOutcome(
            final String segmentId,
            final SegmentStatus status,
            @Nullable final String machineTarget,
            @Nullable final String maskedMachineTarget,
            final double confidence,
            @Nullable final Double judgeScore,
            final List<QaFinding> findings,
            final SegmentPath path,
            final int repairRounds,
            @Nullable final AppError flagReason) {
        this(
                segmentId,
                status,
                machineTarget,
                maskedMachineTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                flagReason,
                null);
    }

    /**
     * Keeps {@code rejected} as this decision's rejected reply when no target passed every hard gate.
     *
     * @param rejected the refused reply in masked form, or {@code null} when none was kept
     * @return this decision unchanged when it holds a machine target or {@code rejected} is null, else one carrying it
     */
    public SegmentOutcome keepingRejected(@Nullable final String rejected) {
        if (machineTarget != null || rejected == null) {
            return this;
        }
        return new SegmentOutcome(
                segmentId,
                status,
                null,
                null,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                flagReason,
                rejected);
    }
}
