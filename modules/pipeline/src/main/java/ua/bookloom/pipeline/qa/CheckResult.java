package ua.bookloom.pipeline.qa;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;

/**
 * One check's outcome: its margin between 0 and 1, whether it passed, whether it was skipped, whether a failure
 * blocks acceptance outright (owner decision D-3), and the finding a failure raises.
 *
 * @param check which check this outcome belongs to
 * @param margin the check's contribution to confidence; {@code 0.0} for a failed check, {@code 1.0} for a skipped
 *     one
 * @param passed {@code true} when the check was skipped or met its threshold, {@code false} when it failed
 * @param skipped {@code true} when the check did not apply to this segment
 * @param blocking {@code true} when a failure of this check blocks acceptance regardless of confidence; always
 *     {@code false} for a passed or skipped check, and for a failed echo below the echo floor
 * @param finding the finding a failure raises, or {@code null} for a passed or skipped check
 */
public record CheckResult(
        CheckName check,
        double margin,
        boolean passed,
        boolean skipped,
        boolean blocking,
        @Nullable QaFinding finding) {

    private static final double MIN_MARGIN = 0.0;
    private static final double MAX_MARGIN = 1.0;

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public CheckResult {
        Objects.requireNonNull(check, "check");
        if (margin < MIN_MARGIN || margin > MAX_MARGIN) {
            throw new IllegalArgumentException("margin must be within [0,1], but was " + margin);
        }
    }

    /**
     * Builds the outcome of a check that did not apply to this segment.
     *
     * @param check which check was skipped
     * @return a skipped, passed result with margin 1.0 and no finding
     */
    public static CheckResult skip(final CheckName check) {
        return new CheckResult(check, MAX_MARGIN, true, true, false, null);
    }

    /**
     * Builds the outcome of a check that met its threshold.
     *
     * @param check which check passed
     * @param margin the check's margin, between 0 and 1
     * @return a passed, non-skipped result with no finding
     */
    public static CheckResult pass(final CheckName check, final double margin) {
        return new CheckResult(check, margin, true, false, false, null);
    }

    /**
     * Builds the outcome of a check that failed outright, blocking acceptance (D-3).
     *
     * @param check which check failed
     * @param note a human-readable explanation of the failure
     * @return a failed, blocking result with margin 0.0 and a {@code medium} finding
     */
    public static CheckResult fail(final CheckName check, final String note) {
        Objects.requireNonNull(note, "note");
        return new CheckResult(
                check,
                MIN_MARGIN,
                false,
                false,
                true,
                new QaFinding(check.findingKind(), Severity.MEDIUM, note, check.raisedBy()));
    }

    /**
     * Builds the outcome of a failed echo check whose source display text falls below the 20-code-point echo
     * floor: it lowers confidence but does not block acceptance.
     *
     * @param check which check failed (always {@link CheckName#ECHO})
     * @param note a human-readable explanation of the failure
     * @return a failed, non-blocking result with margin 0.0 and a {@code low} finding
     */
    public static CheckResult failNonBlocking(final CheckName check, final String note) {
        Objects.requireNonNull(note, "note");
        return new CheckResult(
                check,
                MIN_MARGIN,
                false,
                false,
                false,
                new QaFinding(check.findingKind(), Severity.LOW, note, check.raisedBy()));
    }
}
