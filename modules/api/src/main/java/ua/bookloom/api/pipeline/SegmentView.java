package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;

/**
 * A segment as the review panel and the Export screen read it — everything {@link ReviewDesk}'s queries expose,
 * so neither ever reads a stored record directly.
 *
 * @param segmentId the segment's stable id
 * @param locator the segment's human-readable {@code SegmentLocator} text
 * @param kind what block this segment was parsed from
 * @param status this segment's position in the status machine
 * @param maskedSource the segment's source text with the document's own placeholders still in place
 * @param displaySource the segment's source text as shown to a person
 * @param maskedMachineTarget the last machine target that passed every hard gate, masked form, or null until
 *     translated
 * @param userTarget the person's own edit in plain form, or null when no edit is held
 * @param maskedUserTarget the person's own edit in masked form, or null when no edit is held
 * @param findings the QA findings recorded against this segment
 * @param judgeScore the judge stage's score, or null when not judged
 * @param path how this segment reached its current target
 * @param reviewed true once a person accepted, saved, reverted, applied a proposal or retried this segment
 *     successfully
 * @param context the context snapshot a retry replays, or null when none was recorded
 * @param proposal a pending backward-revision proposal for this segment's edit, or null when none is pending
 * @param rejectedTarget the model's last refused reply in masked form, kept only while no machine target exists, or
 *     null — review shows it labelled as no usable translation rather than the source
 */
public record SegmentView(
        String segmentId,
        String locator,
        SegmentKind kind,
        SegmentStatus status,
        String maskedSource,
        String displaySource,
        @Nullable String maskedMachineTarget,
        @Nullable String userTarget,
        @Nullable String maskedUserTarget,
        List<QaFinding> findings,
        @Nullable Double judgeScore,
        SegmentPath path,
        boolean reviewed,
        @Nullable ContextSnapshot context,
        @Nullable String proposal,
        @Nullable String rejectedTarget) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies {@code findings}.
     */
    public SegmentView {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(displaySource, "displaySource");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(path, "path");
        findings = List.copyOf(findings);
    }

    /** A segment view with no refused reply kept. */
    public SegmentView(
            final String segmentId,
            final String locator,
            final SegmentKind kind,
            final SegmentStatus status,
            final String maskedSource,
            final String displaySource,
            @Nullable final String maskedMachineTarget,
            @Nullable final String userTarget,
            @Nullable final String maskedUserTarget,
            final List<QaFinding> findings,
            @Nullable final Double judgeScore,
            final SegmentPath path,
            final boolean reviewed,
            @Nullable final ContextSnapshot context,
            @Nullable final String proposal) {
        this(
                segmentId,
                locator,
                kind,
                status,
                maskedSource,
                displaySource,
                maskedMachineTarget,
                userTarget,
                maskedUserTarget,
                findings,
                judgeScore,
                path,
                reviewed,
                context,
                proposal,
                null);
    }
}
