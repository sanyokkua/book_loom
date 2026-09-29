package ua.bookloom.pipeline.heal;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.judge.JudgeCall;
import ua.bookloom.pipeline.judge.JudgeVerdict;
import ua.bookloom.pipeline.judge.JudgedPair;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Starts one chunk's quality loop: evaluates every drafted outcome, makes the chunk's one judge call when the dial
 * enables it — over the drafted pairs only, never a memory reuse — and hands both to a {@link ChunkDecider} that
 * decides one segment per call ({@code specs/quality-gates/spec.md} "Judge each chunk once when the quality dial
 * enables the judge").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class QualityLoop {

    private final JudgeCall judgeCall;
    private final DirectedFix directedFix;
    private final ReflectImprove reflectImprove;
    private final Polish polish;

    /**
     * Starts the chunk's quality loop.
     *
     * @param outcomes the chunk's draft outcomes, in document order
     * @param settings the chunk's review mode, dial, call frame, name policy and glossary terms
     * @param gate the placeholder-gate function every self-heal round's candidate goes through
     * @param calls the seam every model call of this loop is sent through
     * @return a decider that yields one {@link SegmentOutcome} per {@code nextDecision()} call, or the error the
     *     chunk's judge call answered
     */
    public Result<ChunkDecider> start(
            final List<DraftOutcome> outcomes,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(calls, "calls");
        log.debug(
                "Starting quality loop chunkSize={} judgeEnabled={}",
                outcomes.size(),
                settings.dial().judge());
        final Map<Integer, QaResult> initialQa = evaluateDrafts(outcomes, settings);
        final JudgeOutcome judgeOutcome = judgeChunk(outcomes, initialQa, settings, calls);
        if (judgeOutcome.error() != null) {
            return Result.err(judgeOutcome.error());
        }
        final SegmentHealer healer =
                new SegmentHealer(judgeCall, directedFix, reflectImprove, polish, settings, gate, calls);
        return Result.ok(new ChunkDecider(outcomes, initialQa, judgeOutcome.verdict(), healer));
    }

    private Map<Integer, QaResult> evaluateDrafts(final List<DraftOutcome> outcomes, final LoopSettings settings) {
        final Map<Integer, QaResult> results = new LinkedHashMap<>();
        for (int index = 0; index < outcomes.size(); index++) {
            if (outcomes.get(index) instanceof DraftOutcome.Drafted drafted) {
                results.put(index, evaluateDraft(drafted, settings));
            }
        }
        return results;
    }

    private QaResult evaluateDraft(final DraftOutcome.Drafted outcome, final LoopSettings settings) {
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

    /** Makes the chunk's judge call when the dial enables it and at least one pair qualifies. */
    private JudgeOutcome judgeChunk(
            final List<DraftOutcome> outcomes,
            final Map<Integer, QaResult> initialQa,
            final LoopSettings settings,
            final ModelCalls calls) {
        if (!settings.dial().judge()) {
            return new JudgeOutcome(null, null);
        }
        final List<JudgedPair> pairs = qualifyingPairs(outcomes, initialQa);
        logJudgeInput(outcomes, initialQa, pairs);
        if (pairs.isEmpty()) {
            return new JudgeOutcome(null, null);
        }
        final Result<JudgeVerdict> judged = judgeCall.judge(pairs, settings.frame(), settings.glossaryTerms(), calls);
        return judged.isErr() ? new JudgeOutcome(null, judged.error()) : new JudgeOutcome(judged.data(), null);
    }

    private static List<JudgedPair> qualifyingPairs(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa) {
        final List<JudgedPair> pairs = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            addQualifyingPair(pairs, outcomes.get(index), initialQa.get(index));
        }
        return pairs;
    }

    private static void addQualifyingPair(
            final List<JudgedPair> pairs, final DraftOutcome outcome, @Nullable final QaResult qa) {
        switch (outcome) {
            case DraftOutcome.Drafted drafted -> {
                if (Objects.requireNonNull(qa).hardGatesPass()) {
                    // The judge reads names, not the tokens the draft was shown them as.
                    pairs.add(new JudgedPair(
                            drafted.segment().id(),
                            drafted.segment().masked(),
                            Objects.requireNonNullElse(drafted.maskedForm(), drafted.maskedReply())));
                }
            }
            case DraftOutcome.FlaggedAtOnce ignored -> {}
            // Already accepted once between the same neighbours; the judge never sees a reuse.
            case DraftOutcome.Reused ignored -> {}
        }
    }

    private static void logJudgeInput(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa, final List<JudgedPair> pairs) {
        final List<String> included = pairs.stream().map(JudgedPair::segmentId).toList();
        final List<String> excluded = excludedFromJudge(outcomes, initialQa);
        log.debug("Judge input included={} excluded={}", included, excluded);
    }

    private static List<String> excludedFromJudge(
            final List<DraftOutcome> outcomes, final Map<Integer, QaResult> initialQa) {
        final List<String> excluded = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            addExcluded(excluded, outcomes.get(index), initialQa.get(index));
        }
        return excluded;
    }

    private static void addExcluded(
            final List<String> excluded, final DraftOutcome outcome, @Nullable final QaResult qa) {
        if (outcome instanceof DraftOutcome.FlaggedAtOnce flaggedAtOnce) {
            excluded.add(flaggedAtOnce.segment().id() + ":flagged-at-once");
        } else if (outcome instanceof DraftOutcome.Reused reused) {
            excluded.add(reused.segment().id() + ":reused");
        } else if (qa != null && !qa.hardGatesPass()) {
            excluded.add(outcome.segment().id() + ":hard-gate-failed");
        }
    }

    /**
     * The chunk judge call's outcome: a verdict (possibly {@code null} when the judge did not run), or the error
     * that ends {@link #start}. A plain local holder rather than {@link Result}, which never tolerates a
     * {@code null} success value.
     */
    private record JudgeOutcome(
            @Nullable JudgeVerdict verdict, @Nullable AppError error) {}
}
