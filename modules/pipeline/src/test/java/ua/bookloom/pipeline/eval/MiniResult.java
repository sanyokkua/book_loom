package ua.bookloom.pipeline.eval;

import java.util.Objects;

/**
 * One expectation scored.
 *
 * @param id the expectation's id
 * @param defect the defect it guards
 * @param passed whether the run met it
 * @param detail what was found, in a line: the reason a failing expectation failed, or what held
 */
record MiniResult(String id, String defect, boolean passed, String detail) {

    /** Rejects a missing id. */
    MiniResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defect, "defect");
        Objects.requireNonNull(detail, "detail");
    }
}
