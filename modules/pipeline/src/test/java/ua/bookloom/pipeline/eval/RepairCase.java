package ua.bookloom.pipeline.eval;

import java.util.Objects;
import ua.bookloom.pipeline.eval.EvalCase.RepairStep;

/**
 * A repair call of the real-run corpus: a Markdown source the real parser masks, the reply a run would refuse, and
 * two scripted answers of the repair call — one that the production document gate accepts, one it refuses.
 *
 * @param id the case id
 * @param step which repair call it is
 * @param markdown the source paragraph, parsed by the real document module so the gate sees genuine placeholders
 * @param rejected the refused reply: the raw reply of a structural repair, the target text of a placeholder repair
 * @param good a masked target the document gate restores
 * @param bad a masked target the document gate refuses
 * @param rationale what went wrong in the real shape
 */
record RepairCase(
        String id, RepairStep step, String markdown, String rejected, String good, String bad, String rationale) {

    /** Rejects missing parts. */
    RepairCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(rejected, "rejected");
        Objects.requireNonNull(good, "good");
        Objects.requireNonNull(bad, "bad");
    }
}
