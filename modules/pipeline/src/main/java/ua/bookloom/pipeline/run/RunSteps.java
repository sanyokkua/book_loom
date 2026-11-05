package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.memory.ChapterSummaryCall;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The calls a chunk is made of.
 *
 * @param translator the draft step, which each chunk gates through its own protected spans
 * @param loop the quality loop, holding the reviewer and the self-heal calls
 * @param gate the document's placeholder gate, which each chunk's protected-span gate wraps
 * @param calls the seam every call goes through
 * @param splitter the sentence splitter an oversized segment is drafted in pieces with
 * @param batch the drafter of a chunk's segments in batches, whose size adapts over the run
 * @param planner the planner of the run's translate-family calls, bound to the run's fixed frame
 * @param summary the call that summarises a chapter once its last segment is committed
 */
public record RunSteps(
        SegmentTranslator translator,
        QualityLoop loop,
        GateFunction gate,
        ModelCalls calls,
        SentenceSplitter splitter,
        BatchDrafter batch,
        TranslatePlanner planner,
        ChapterSummaryCall summary) {

    /** Rejects a missing step. */
    public RunSteps {
        Objects.requireNonNull(translator, "translator");
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(splitter, "splitter");
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(planner, "planner");
        Objects.requireNonNull(summary, "summary");
    }
}
