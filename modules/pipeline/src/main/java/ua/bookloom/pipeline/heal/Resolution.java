package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * How the reviewer's answer about one segment came out once its edits were verified and applied.
 *
 * @param machine the target the segment ends with: the draft's, or the edited, rewritten or fixed text
 * @param qa the evaluation of that target
 * @param findings what the reviewer left on the segment: an applied-edit finding per edit, a note per fluency or style
 *     remark, and an evidence finding per issue no edit could fix
 * @param rounds how many repair rounds the segment used: one when the reviewer's edits, a rewrite or a directed fix
 *     changed it, otherwise zero
 * @param verifiedBlockersLeft how many issues the reviewer evidenced with a quote the app found, that no edit or fix
 *     resolved
 * @param reason the error to record against a flagged segment, or {@code null}
 * @param maskedText the masked text {@code machine} stands on, the base a later directed fix rewrites
 */
record Resolution(
        MachineTarget machine,
        QaResult qa,
        List<QaFinding> findings,
        int rounds,
        int verifiedBlockersLeft,
        @Nullable AppError reason,
        String maskedText) {

    /** Copies the findings and rejects a missing component. */
    Resolution {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(qa, "qa");
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(maskedText, "maskedText");
        findings = List.copyOf(findings);
    }
}
