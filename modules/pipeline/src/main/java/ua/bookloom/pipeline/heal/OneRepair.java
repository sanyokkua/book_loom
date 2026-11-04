package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.GateNotes;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.Candidate;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.NeverWorse;
import ua.bookloom.pipeline.qa.Verdict;
import ua.bookloom.pipeline.qa.WindowFacts;

/**
 * The one repair (design D13): a text that still holds a BLOCK or REPAIR finding gets exactly one call naming every
 * such defect with the span it quotes, and the repair replaces the text only when it passed the gate and
 * {@link NeverWorse} keeps it — the acceptance rule alone never decides it. A segment drafted whole is repaired through
 * its {@link RepairCall}, one drafted in pieces by drafting its pieces again with the defects. Nothing else is tried:
 * the outcome is read from the findings on the text that stays ({@code specs/quality-gates/spec.md} "Give a failing
 * segment one repair at Balanced and Max"). At Fast ({@code RepairMode.MARKS_ONLY}) no repair is made: only a text whose
 * formatting marks the code restore could not put back is drafted once more, plainly, under the same keep rule ("Make
 * no fixing call at Fast and mark the segment instead"). Built fresh per chunk by {@link SegmentHealer}.
 */
@Slf4j
final class OneRepair {

    private static final String REASON_KEEP = "keep";
    private static final String REASON_NONE = "none";

    /** The fixing call a segment gets, if any. */
    private enum Fixing {

        /** No call: the outcome is read on the text in place. */
        NONE("none"),

        /** The plain draft once more, for formatting marks still lost after the code restore (Fast). */
        REDRAFT("redraft"),

        /** The one repair naming every BLOCK and REPAIR defect (Balanced and Max). */
        REPAIR("repair");

        private final String label;

        Fixing(final String label) {
            this.label = label;
        }

        /** The step's label in the step list and the log. */
        String label() {
            return label;
        }
    }

    private final ReplyEvaluator evaluator;
    private final LoopSettings settings;
    private final ModelCalls calls;

    OneRepair(final ReplyEvaluator evaluator, final LoopSettings settings, final ModelCalls calls) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    /**
     * The text a segment ends with after its one repair.
     *
     * @param standing the text that stays, its evaluation and what the reviewer left on it
     * @param rejected the repair's refused reply, protected spans put back, kept when no target passed; {@code null}
     *     when the repair made none
     * @param reason why the repair stayed or did not, for the log: {@code keep}, a never-worse veto, {@code gate},
     *     {@code unreadable}, {@code flag-now}, or {@code none} when no repair was made
     */
    record Repaired(Resolution standing, @Nullable String rejected, String reason) {

        /** Rejects a missing component. */
        Repaired {
            Objects.requireNonNull(standing, "standing");
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * Whether the segment was given a content-changing call: the one repair, or the reviewer's change of it.
         *
         * @return {@code true} when the repair call was made or the reviewer changed the text, {@code false} otherwise
         */
        boolean repaired() {
            return !REASON_NONE.equals(reason) || standing.rounds() > 0;
        }
    }

    /**
     * Repairs the text in place once when it holds a defect and the settings allow a fixing call.
     *
     * @param outcome the drafted segment
     * @param standing the text in place: the draft's, or the one the reviewer's answer left
     * @return the text that stays, or the error the repair call answered, which ends the step
     */
    Result<Repaired> repair(final DraftOutcome.Drafted outcome, final Resolution standing) {
        final String segmentId = outcome.segment().id();
        final List<QaFinding> defects = SegmentFindings.defects(standing.qa());
        final Fixing fixing = standing.verifiedBlockersLeft() > 0 ? Fixing.NONE : fixing(outcome, defects);
        log.debug(
                "Fixing decision segment={} defects={} verifiedBlockersLeft={} mode={} repairs={} canRepair={} fixing={}",
                segmentId,
                defects.size(),
                standing.verifiedBlockersLeft(),
                settings.dial().repair(),
                settings.repairs(),
                canRepair(outcome),
                fixing);
        if (fixing == Fixing.NONE) {
            return Result.ok(new Repaired(standing, null, REASON_NONE));
        }
        final String stepKey = RepairSteps.begin(calls, segmentId, RepairSteps.REPAIR_SUFFIX, fixing.label());
        // A re-draft of lost marks is the plain draft again: it names no defect.
        final List<QaFinding> asked = fixing == Fixing.REDRAFT ? List.of() : defects;
        final RepairOutcome ran = evaluator.classify(outcome, send(outcome, standing, asked));
        RepairSteps.end(calls, stepKey, ran);
        return settled(outcome, standing, defects, ran);
    }

    private Result<Repaired> settled(
            final DraftOutcome.Drafted outcome,
            final Resolution standing,
            final List<QaFinding> defects,
            final RepairOutcome ran) {
        final String segmentId = outcome.segment().id();
        return switch (ran) {
            case RepairOutcome.StepError(var error) -> Result.err(error);
            case RepairOutcome.FlagNow(var error) ->
                Result.ok(refused(segmentId, standing, defects, null, error, "flag-now"));
            case RepairOutcome.Failed(var gateFinding, var rejectedForm) ->
                Result.ok(refused(
                        segmentId, standing, defects, rejectedForm, null, gateFinding == null ? "unreadable" : "gate"));
            case RepairOutcome.Evaluated evaluated -> Result.ok(judged(outcome, standing, defects, evaluated));
        };
    }

    // Balanced and Max repair every BLOCK or REPAIR finding; Fast spends a call only on formatting marks the code
    // restore could not put back, and a review retry spends none. A segment the reviewer left a verified blocker on
    // spends none either (decided by the caller): no code check can clear that evidence, so the segment is flagged
    // whatever the repair did.
    private Fixing fixing(final DraftOutcome.Drafted outcome, final List<QaFinding> defects) {
        if (defects.isEmpty() || !settings.repairs() || !canRepair(outcome)) {
            return Fixing.NONE;
        }
        return switch (settings.dial().repair()) {
            case ONE -> Fixing.REPAIR;
            case MARKS_ONLY -> defects.stream().anyMatch(OneRepair::lostMarks) ? Fixing.REDRAFT : Fixing.NONE;
        };
    }

    private static boolean lostMarks(final QaFinding finding) {
        return CheckName.PLACEHOLDER.raisedBy().equals(finding.raisedBy());
    }

    private static boolean canRepair(final DraftOutcome.Drafted outcome) {
        return outcome.inPieces() || outcome.repairCall() != null;
    }

    private Result<RepairReply> send(
            final DraftOutcome.Drafted outcome, final Resolution standing, final List<QaFinding> defects) {
        final List<QaFinding> described = described(outcome.segment(), outcome.maskedSource(), standing, defects);
        if (outcome.inPieces()) {
            log.debug(
                    "Repairing the pieces of segment={} defects={}",
                    outcome.segment().id(),
                    defects.size());
            return Objects.requireNonNull(outcome.pieceRedraft()).redraft(described, calls);
        }
        return Objects.requireNonNull(outcome.repairCall()).send(standing.maskedText(), described, calls);
    }

    private Repaired judged(
            final DraftOutcome.Drafted outcome,
            final Resolution standing,
            final List<QaFinding> defects,
            final RepairOutcome.Evaluated evaluated) {
        final Verdict verdict = verdictOn(outcome, standing, defects, evaluated);
        if (verdict instanceof Verdict.Reject(final String reason)) {
            return refused(outcome.segment().id(), standing, defects, null, null, reason);
        }
        log.debug(
                "one repair segment={} findings={} keep=true reason={}",
                outcome.segment().id(),
                SegmentFindings.kindsOf(defects),
                REASON_KEEP);
        final MachineTarget repaired =
                MachineTarget.standingOn(evaluated.restoredTarget(), evaluated.maskedForm(), evaluated.qa());
        return new Repaired(
                new Resolution(
                        repaired.restored() != null ? repaired : standing.machine(),
                        evaluated.qa(),
                        standing.findings(),
                        counted(standing),
                        standing.verifiedBlockersLeft(),
                        null,
                        evaluated.maskedCandidate()),
                null,
                REASON_KEEP);
    }

    // A text in place that stands on no machine target is no translation — a refusal, an echo, a reply in the wrong
    // language or one whose marks were lost — so the never-worse text vetoes, which protect a translation's quotes,
    // clauses and names, have nothing to protect: the repair stays when it changed the text and is not worse by its
    // blockers.
    private Verdict verdictOn(
            final DraftOutcome.Drafted outcome,
            final Resolution standing,
            final List<QaFinding> defects,
            final RepairOutcome.Evaluated evaluated) {
        if (standing.machine().restored() == null) {
            final boolean same = evaluated.maskedCandidate().equals(standing.maskedText());
            final boolean worse = NeverWorse.isWorseByBlockers(evaluated.qa(), standing.qa());
            log.debug(
                    "Repair of a text that is no translation segment={} same={} worse={}",
                    outcome.segment().id(),
                    same,
                    worse);
            if (same) {
                return new Verdict.Reject("no-change");
            }
            return worse ? new Verdict.Reject("blockers") : new Verdict.Keep();
        }
        final WindowFacts facts = WindowFacts.of(
                outcome.maskedSource(),
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage(),
                settings.names(),
                defects.getFirst(),
                false);
        return NeverWorse.judge(
                new Candidate(standing.maskedText(), standing.qa(), List.of()),
                new Candidate(evaluated.maskedCandidate(), evaluated.qa(), List.of()),
                facts);
    }

    private static Repaired refused(
            final String segmentId,
            final Resolution standing,
            final List<QaFinding> defects,
            @Nullable final String rejected,
            @Nullable final AppError flag,
            final String reason) {
        log.debug(
                "one repair segment={} findings={} keep=false reason={}",
                segmentId,
                SegmentFindings.kindsOf(defects),
                reason);
        return new Repaired(
                new Resolution(
                        standing.machine(),
                        standing.qa(),
                        standing.findings(),
                        standing.rounds(),
                        standing.verifiedBlockersLeft(),
                        flag == null ? standing.reason() : flag,
                        standing.maskedText()),
                rejected,
                reason);
    }

    // A kept repair changed the text, so it counts once whatever the reviewer's edits did.
    private static int counted(final Resolution standing) {
        return Math.max(standing.rounds(), 1);
    }

    // A lost-marks finding names what the gate refused; the repair is told which tokens are missing or misplaced.
    private static List<QaFinding> described(
            final Segment segment, final String shown, final Resolution standing, final List<QaFinding> defects) {
        return defects.stream()
                .map(finding -> CheckName.PLACEHOLDER.raisedBy().equals(finding.raisedBy())
                        ? new QaFinding(
                                finding.kind(),
                                finding.severity(),
                                GateNotes.describe(
                                        shown,
                                        standing.maskedText(),
                                        segment.pairs(),
                                        segment.lineBreakTokens(),
                                        finding.note()),
                                finding.raisedBy())
                        : finding)
                .toList();
    }
}
