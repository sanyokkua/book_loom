package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.qa.LockedRendering;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Checks a context-matched memory target before any draft is made: through the chunk's gate, every check and τ, but
 * never the judge, so a memory entry can never put a broken or since-forbidden target into the book
 * ({@code specs/quality-gates/spec.md} "Accept a context-matched memory reuse without the judge"). A refusal only
 * means the segment is drafted instead, so the caller logs it and moves on; it is never routed like a model error.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReuseCheck {

    /**
     * Checks one stored target for reuse.
     *
     * @param segment the segment the target would be reused for
     * @param maskedSource the text a draft would be shown, protected spans behind tokens
     * @param lockedRenderings the locked glossary terms present in the segment
     * @param remaskedTarget the stored target with each protected span put back behind its token, as a draft reply
     *     would carry it
     * @param gate the chunk's gate: protected spans back, then the document's own markup
     * @param settings the chunk's languages, policies, glossary terms and review mode, whose threshold is τ
     * @return the reuse, ready to be decided in its turn; or {@code validation} naming the findings that refused it
     *     (none when only its confidence fell short), or the gate's own error when the gate itself failed
     */
    public static Result<DraftOutcome.Reused> check(
            final Segment segment,
            final String maskedSource,
            final List<LockedRendering> lockedRenderings,
            final String remaskedTarget,
            final GateFunction gate,
            final LoopSettings settings) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        Objects.requireNonNull(remaskedTarget, "remaskedTarget");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(settings, "settings");
        log.debug("Checking a memory reuse segmentId={} lockedTerms={}", segment.id(), lockedRenderings.size());
        return switch (gate.restore(segment, remaskedTarget)) {
            case GateResult.Restored restored ->
                evaluated(new Candidate(segment, maskedSource, lockedRenderings, remaskedTarget), restored, settings);
            case GateResult.GateFailed failed ->
                refused(segment.id(), failed.finding().raisedBy(), List.of(failed.finding()));
            case GateResult.StepError stepError -> {
                log.debug(
                        "Memory reuse not checked segmentId={}: the gate failed code={}",
                        segment.id(),
                        stepError.error().code());
                yield Result.err(stepError.error());
            }
        };
    }

    private static Result<DraftOutcome.Reused> evaluated(
            final Candidate candidate, final GateResult.Restored restored, final LoopSettings settings) {
        final QaResult qa = QaEvaluation.evaluate(
                List.of(),
                candidate.segment(),
                candidate.maskedSource(),
                candidate.remaskedTarget(),
                restored.maskedForm(),
                settings,
                candidate.lockedRenderings());
        final double tau = settings.reviewMode().threshold();
        final boolean accepted = AcceptanceRule.acceptsReuse(qa, tau);
        log.debug(
                "Memory reuse evaluated segmentId={} hardGatesPass={} failedOutright={} confidence={} tau={} accepted={}",
                candidate.segment().id(),
                qa.hardGatesPass(),
                qa.failedOutright(),
                qa.confidence(),
                tau,
                accepted);
        if (!accepted) {
            return refused(candidate.segment().id(), qa.hardGatesPass() ? "checks" : "hard-gate", qa.findings());
        }
        return Result.ok(new DraftOutcome.Reused(
                candidate.segment(),
                candidate.maskedSource(),
                candidate.lockedRenderings(),
                restored.maskedForm(),
                restored.restored(),
                qa));
    }

    private static Result<DraftOutcome.Reused> refused(
            final String segmentId, final String refusedBy, final List<QaFinding> findings) {
        final List<String> blocking = findings.stream()
                .filter(finding -> finding.severity() != Severity.LOW)
                .map(QaFinding::kind)
                .distinct()
                .toList();
        log.debug("Memory reuse refused segmentId={} by={} findings={}", segmentId, refusedBy, blocking);
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Stored translation not reused",
                "The translation memory's target did not pass this segment's checks, so the segment is drafted.",
                SafeDetails.empty().withQaFindings(blocking).render(),
                null));
    }

    /** The segment a stored target is checked for, and that target as a reply would carry it. */
    private record Candidate(
            Segment segment, String maskedSource, List<LockedRendering> lockedRenderings, String remaskedTarget) {}
}
