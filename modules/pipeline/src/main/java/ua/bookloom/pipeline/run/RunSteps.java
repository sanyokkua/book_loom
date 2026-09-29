package ua.bookloom.pipeline.run;

import java.util.Objects;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.pipeline.SegmentTranslator;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The calls a chunk is made of.
 *
 * @param translator the draft step, which each chunk gates through its own protected spans
 * @param loop the quality loop, holding the judge and the self-heal calls
 * @param gate the document's placeholder gate, which each chunk's protected-span gate wraps
 * @param calls the seam every call goes through
 * @param splitter the sentence splitter an oversized segment is drafted in pieces with
 */
public record RunSteps(
        SegmentTranslator translator,
        QualityLoop loop,
        GateFunction gate,
        ModelCalls calls,
        SentenceSplitter splitter) {

    /** Rejects a missing step. */
    public RunSteps {
        Objects.requireNonNull(translator, "translator");
        Objects.requireNonNull(loop, "loop");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(calls, "calls");
        Objects.requireNonNull(splitter, "splitter");
    }
}
