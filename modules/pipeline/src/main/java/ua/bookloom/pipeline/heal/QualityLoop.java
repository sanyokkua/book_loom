package ua.bookloom.pipeline.heal;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Starts one chunk's quality loop: evaluates every drafted outcome and hands them to a {@link ChunkDecider} that
 * decides one segment per call ({@code specs/quality-gates/spec.md} "Read a segment's outcome from the findings left
 * after the one repair").
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class QualityLoop {

    /**
     * Starts the chunk's quality loop.
     *
     * @param outcomes the chunk's draft outcomes, in document order
     * @param settings the chunk's review mode, dial, call frame, name policy and glossary terms
     * @param gate the placeholder-gate function every repaired candidate goes through
     * @param calls the seam every model call of this loop is sent through
     * @return a decider that yields one {@link SegmentOutcome} per {@code nextDecision()} call
     */
    public ChunkDecider start(
            final List<DraftOutcome> outcomes,
            final LoopSettings settings,
            final GateFunction gate,
            final ModelCalls calls) {
        Objects.requireNonNull(outcomes, "outcomes");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(calls, "calls");
        log.debug("Starting quality loop chunkSize={}", outcomes.size());
        final EvaluatedDrafts drafts = evaluateDrafts(outcomes, settings, gate);
        return new ChunkDecider(drafts.outcomes(), drafts.qa(), new SegmentHealer(settings, gate, calls));
    }

    /** The chunk's outcomes, each draft whose quote marks were repaired replaced by the repaired one, with their QA. */
    private record EvaluatedDrafts(List<DraftOutcome> outcomes, Map<Integer, QaResult> qa) {}

    private EvaluatedDrafts evaluateDrafts(
            final List<DraftOutcome> outcomes, final LoopSettings settings, final GateFunction gate) {
        final List<DraftOutcome> current = new ArrayList<>(outcomes);
        final Map<Integer, QaResult> results = new LinkedHashMap<>();
        for (int index = 0; index < outcomes.size(); index++) {
            if (outcomes.get(index) instanceof DraftOutcome.Drafted drafted) {
                final DraftJudge.Judged judged = DraftJudge.judge(drafted, settings, gate);
                current.set(index, judged.outcome());
                results.put(index, judged.qa());
            }
        }
        return new EvaluatedDrafts(current, results);
    }
}
