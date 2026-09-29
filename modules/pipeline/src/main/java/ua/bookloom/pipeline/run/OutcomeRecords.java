package ua.bookloom.pipeline.run;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.Decision;
import ua.bookloom.pipeline.heal.SegmentOutcome;

/**
 * Turns a decision into the record a project stores, and reads a flagged record's reason back.
 *
 * <p>A segment flagged at once has no error field of its own: its reason is kept as a finding whose kind is the
 * error code's name, so the review desk and the report show it like any other finding.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class OutcomeRecords {

    static final String REPLY = "reply";

    /**
     * Applies a decision to the record it decides.
     *
     * @param stored the non-null pending record
     * @param decision the non-null decision made for it
     * @return the record with its status, machine target in plain and masked form, confidence and path set
     */
    public static SegmentRecord decided(final SegmentRecord stored, final Decision decision) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(decision, "decision");
        log.debug(
                "Converting decision segmentId={} status={}",
                stored.segmentId(),
                decision.segment().status());
        final Segment segment = decision.segment();
        final AppError reason = decision.flagReason();
        return new SegmentRecord(
                stored.projectId(),
                stored.segmentId(),
                stored.unitId(),
                stored.ord(),
                stored.kind(),
                segment.status(),
                segment.targetInner(),
                decision.maskedTarget(),
                stored.userTarget(),
                stored.maskedUserTarget(),
                segment.confidence(),
                stored.judgeScore(),
                reason == null ? List.of() : List.of(replyFinding(reason)),
                SegmentPath.DRAFT,
                stored.repairRounds(),
                stored.reviewed(),
                stored.context());
    }

    /**
     * Applies the quality loop's outcome for a segment to the record it decides.
     *
     * @param stored the non-null pending record
     * @param outcome the non-null outcome decided for it
     * @return the record with its status, both machine forms, confidence, judge score, findings, path and repair
     *     rounds set; a segment flagged at once also carries its reason as the {@code reply} finding
     */
    public static SegmentRecord decided(final SegmentRecord stored, final SegmentOutcome outcome) {
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(outcome, "outcome");
        log.debug(
                "Converting outcome segmentId={} status={} path={}",
                outcome.segmentId(),
                outcome.status(),
                outcome.path());
        final AppError reason = outcome.flagReason();
        return new SegmentRecord(
                stored.projectId(),
                stored.segmentId(),
                stored.unitId(),
                stored.ord(),
                stored.kind(),
                outcome.status(),
                outcome.machineTarget(),
                outcome.maskedMachineTarget(),
                stored.userTarget(),
                stored.maskedUserTarget(),
                outcome.confidence(),
                outcome.judgeScore(),
                withReplyFinding(outcome.findings(), reason),
                outcome.path(),
                outcome.repairRounds(),
                stored.reviewed(),
                stored.context());
    }

    /**
     * Reads the code a flagged record is reported under.
     *
     * @param flagged the non-null flagged record
     * @return the error code named by its reply finding, or {@code validation} when it has none — a segment flagged
     *     after repair rounds has only findings of the checks that failed
     */
    public static ErrorCode reportCode(final SegmentRecord flagged) {
        Objects.requireNonNull(flagged, "flagged");
        return flagged.findings().stream()
                .filter(finding -> REPLY.equals(finding.raisedBy()))
                .flatMap(finding -> codeNamed(finding.kind()))
                .findFirst()
                .orElse(ErrorCode.validation);
    }

    private static List<QaFinding> withReplyFinding(final List<QaFinding> findings, @Nullable final AppError reason) {
        return reason == null
                ? findings
                : Stream.concat(findings.stream(), Stream.of(replyFinding(reason)))
                        .toList();
    }

    private static QaFinding replyFinding(final AppError reason) {
        return new QaFinding(reason.code().name(), Severity.HIGH, reason.message(), REPLY);
    }

    private static Stream<ErrorCode> codeNamed(final String name) {
        return Stream.of(ErrorCode.values()).filter(code -> code.name().equals(name));
    }
}
