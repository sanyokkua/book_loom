package ua.bookloom.pipeline.heal;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The text a segment stands on while it is decided: the draft's, or the one repair's.
 *
 * @param machine the target the segment ends with
 * @param qa the evaluation of that target
 * @param rounds how many repair rounds the segment used: one when the repair changed it, otherwise zero
 * @param reason the error to record against a flagged segment, or {@code null}
 * @param maskedText the masked text {@code machine} stands on, the base a repair rewrites
 */
record Resolution(
        MachineTarget machine,
        QaResult qa,
        int rounds,
        @Nullable AppError reason,
        String maskedText) {

    /** Rejects a missing component. */
    Resolution {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(maskedText, "maskedText");
    }
}
