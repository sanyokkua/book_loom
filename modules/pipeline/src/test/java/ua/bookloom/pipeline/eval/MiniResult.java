package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.pipeline.eval.MiniExpectations.CheckKind.Level;

/**
 * One expectation scored.
 *
 * @param id the expectation's id
 * @param defect the defect it guards
 * @param passed whether the run met it
 * @param detail what was found, in a line: the reason a failing expectation failed, or what held
 * @param level how hard its failure counts; a glossary expectation is soft, compared with the baseline only
 * @param blocks the blocks it concerns; empty when it concerns the glossary
 * @param failingBlocks the blocks among them that did not meet it
 */
record MiniResult(
        String id,
        String defect,
        boolean passed,
        String detail,
        Level level,
        List<String> blocks,
        List<String> failingBlocks) {

    /** Rejects a missing part and copies the lists. */
    MiniResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(level, "level");
        blocks = List.copyOf(blocks);
        failingBlocks = List.copyOf(failingBlocks);
    }
}
