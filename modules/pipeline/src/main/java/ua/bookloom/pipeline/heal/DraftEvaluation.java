package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * A draft's first evaluation, in one place so the quality loop that decides it and the run that announces its
 * confidence as soon as it is drafted can never compute two different figures.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DraftEvaluation {

    /**
     * Evaluates a draft against every hard gate and soft check.
     *
     * @param outcome the non-null drafted reply; one that failed the placeholder gate carries that failure in
     * @param settings the non-null loop settings of its chunk
     * @return the draft's QA result, whose confidence is the one its drafted event carries
     */
    public static QaResult evaluate(final DraftOutcome.Drafted outcome, final LoopSettings settings) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(settings, "settings");
        log.debug(
                "Evaluating draft segmentId={} gatePassed={}",
                outcome.segment().id(),
                outcome.restoredTarget() != null);
        final List<CheckResult> given = outcome.restoredTarget() == null
                ? List.of(failedGateFrom(Objects.requireNonNull(outcome.gateFinding())))
                : List.<CheckResult>of();
        logTraceDraftTarget(outcome);
        return QaEvaluation.evaluate(
                given,
                outcome.segment(),
                outcome.maskedSource(),
                outcome.maskedReply(),
                Objects.requireNonNullElse(outcome.maskedForm(), outcome.maskedReply()),
                settings,
                outcome.lockedRenderings());
    }

    /** Rebuilds the draft's own hard-gate failure as a {@link CheckResult}, from the finding it already raised. */
    private static CheckResult failedGateFrom(final QaFinding gateFinding) {
        return CheckResult.hardGateFailed(checkNameFor(gateFinding.raisedBy()), gateFinding.note());
    }

    private static CheckName checkNameFor(final String raisedBy) {
        return switch (raisedBy) {
            case "placeholder" -> CheckName.PLACEHOLDER;
            case "locked-term" -> CheckName.LOCKED_TERM;
            case "kept-run" -> CheckName.KEPT_RUN;
            default -> throw new IllegalArgumentException("no hard-gate CheckName for raisedBy=" + raisedBy);
        };
    }

    private static void logTraceDraftTarget(final DraftOutcome.Drafted outcome) {
        if (log.isTraceEnabled()) {
            log.trace(
                    "Draft target segment={} reply={} maskedForm={} restored={}",
                    outcome.segment().id(),
                    outcome.maskedReply(),
                    outcome.maskedForm(),
                    outcome.restoredTarget());
        }
    }
}
