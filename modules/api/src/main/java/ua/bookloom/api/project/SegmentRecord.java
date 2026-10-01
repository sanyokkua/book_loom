package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;

/**
 * A segment's stored translation decision — its machine target and the person's own edit, each held in plain and
 * masked form, and a reviewed mark — everything the review desk, export and a run read and write.
 *
 * @param projectId the owning project's id
 * @param segmentId the segment's stable id
 * @param unitId the owning unit's id
 * @param ord this segment's position within its unit, in document order
 * @param kind what block this segment was parsed from
 * @param status this segment's position in the status machine
 * @param machineTarget the last target that passed every hard gate, or null until translated
 * @param maskedMachineTarget {@code machineTarget} after protected-span restore and before {@code DocumentPort.unmask}
 *     — the document's own {@code ⟦gN⟧} tokens still in place, locked names shown as their renderings — or null
 *     until translated
 * @param userTarget the person's own edit in plain form, or null when no edit is held
 * @param maskedUserTarget the person's own edit in masked form — what the review editor shows and saves — or null
 *     when no edit is held
 * @param confidence the QA/judge confidence in {@code [0,1]}
 * @param judgeScore the judge stage's score, or null when not judged
 * @param findings the QA findings recorded against this segment
 * @param path how this segment reached its current target
 * @param repairRounds how many repair rounds this segment has gone through
 * @param reviewed true once a person accepted, saved, reverted, applied a proposal or retried this segment successfully
 * @param context the context snapshot a retry replays, or null when none was recorded
 * @param rejectedTarget the model's last refused reply in masked form, kept only while no machine target exists, so
 *     review shows the model's words labelled as unusable instead of the source; null otherwise
 */
public record SegmentRecord(
        String projectId,
        String segmentId,
        String unitId,
        int ord,
        SegmentKind kind,
        SegmentStatus status,
        @Nullable String machineTarget,
        @Nullable String maskedMachineTarget,
        @Nullable String userTarget,
        @Nullable String maskedUserTarget,
        double confidence,
        @Nullable Double judgeScore,
        List<QaFinding> findings,
        SegmentPath path,
        int repairRounds,
        boolean reviewed,
        @Nullable ContextSnapshot context,
        @Nullable String rejectedTarget) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies {@code findings} into an
     * unmodifiable list, so a caller-held mutable list cannot corrupt this record after construction.
     */
    public SegmentRecord {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(path, "path");
        findings = List.copyOf(findings);
    }

    /** A record with no rejected reply kept — every record a person's action or a passing draft produces. */
    public SegmentRecord(
            final String projectId,
            final String segmentId,
            final String unitId,
            final int ord,
            final SegmentKind kind,
            final SegmentStatus status,
            @Nullable final String machineTarget,
            @Nullable final String maskedMachineTarget,
            @Nullable final String userTarget,
            @Nullable final String maskedUserTarget,
            final double confidence,
            @Nullable final Double judgeScore,
            final List<QaFinding> findings,
            final SegmentPath path,
            final int repairRounds,
            final boolean reviewed,
            @Nullable final ContextSnapshot context) {
        this(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                null);
    }

    /**
     * Returns a record with a replacement rejected reply, preserving every other field.
     *
     * @param rejectedTarget the refused reply in masked form, or null to clear it
     * @return a new record with the supplied rejected reply
     */
    public SegmentRecord withRejectedTarget(@Nullable final String rejectedTarget) {
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }

    /**
     * Returns the target text this segment currently exports with.
     *
     * @return the user's edit when present, else the machine target, or empty when neither exists
     */
    public Optional<String> effectiveTarget() {
        return userTarget != null ? Optional.of(userTarget) : Optional.ofNullable(machineTarget);
    }

    /**
     * Reports whether this record is an auxiliary segment the brief keeps as source rather than translating, the
     * one rule every repository read and count applies.
     *
     * @param keptAuxiliaryKinds the non-null auxiliary kinds the brief keeps as source
     * @return {@code true} for a record of the auxiliary unit whose kind is in the set, whatever its status
     */
    public boolean isKeptAsSource(final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        return unitId.equals(Unit.AUXILIARY_ID) && keptAuxiliaryKinds.contains(kind);
    }

    /**
     * Returns a record with a replacement status, preserving every other field.
     *
     * @param status the non-null replacement status
     * @return a new record with the supplied status
     */
    public SegmentRecord withStatus(final SegmentStatus status) {
        Objects.requireNonNull(status, "status");
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }

    /**
     * Returns a record with a replacement machine target, plain and masked forms changed together, preserving every
     * other field.
     *
     * @param machineTarget the replacement plain machine target, or null to clear it
     * @param maskedMachineTarget the replacement masked machine target, or null to clear it
     * @return a new record with the supplied machine target
     */
    public SegmentRecord withMachineTarget(
            @Nullable final String machineTarget, @Nullable final String maskedMachineTarget) {
        @Nullable final String keptRejected = machineTarget == null ? rejectedTarget : null;
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                keptRejected);
    }

    /**
     * Returns a record with a replacement user edit, plain and masked forms changed together, preserving every
     * other field.
     *
     * @param userTarget the replacement plain user edit, or null to clear it
     * @param maskedUserTarget the replacement masked user edit, or null to clear it
     * @return a new record with the supplied user edit
     */
    public SegmentRecord withUserTarget(@Nullable final String userTarget, @Nullable final String maskedUserTarget) {
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }

    /**
     * Returns a record with a replacement findings list, preserving every other field.
     *
     * @param findings the non-null replacement findings, defensively copied
     * @return a new record with the supplied findings
     */
    public SegmentRecord withFindings(final List<QaFinding> findings) {
        Objects.requireNonNull(findings, "findings");
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }

    /**
     * Returns a record with a replacement path, preserving every other field.
     *
     * @param path the non-null replacement path
     * @return a new record with the supplied path
     */
    public SegmentRecord withPath(final SegmentPath path) {
        Objects.requireNonNull(path, "path");
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }

    /**
     * Returns a record with a replacement reviewed mark, preserving every other field.
     *
     * @param reviewed the replacement reviewed mark
     * @return a new record with the supplied reviewed mark
     */
    public SegmentRecord withReviewed(final boolean reviewed) {
        return new SegmentRecord(
                projectId,
                segmentId,
                unitId,
                ord,
                kind,
                status,
                machineTarget,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                confidence,
                judgeScore,
                findings,
                path,
                repairRounds,
                reviewed,
                context,
                rejectedTarget);
    }
}
