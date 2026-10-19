package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.QaFinding;

/**
 * One segment's whole quality-gate outcome: every hard-gate result, every soft-check result, the blended confidence
 * ({@link Confidence#blend(List)}), every finding a failed check raised, and whether a soft check failed outright
 * (owner decision D-3).
 *
 * @param hardGates the refusal gate's result together with whatever placeholder/protected-span results the caller
 *     already computed, then one result per deterministic text finding — a blocking one fails, a soft one passes with
 *     a low finding
 * @param soft the five soft-check results, in {@link SoftChecks#run(SoftCheckInput)}'s fixed order
 * @param confidence the soft checks' blended confidence, in {@code [0,1]}; a hard-gate failure never lowers it
 * @param findings every finding a failed hard gate or soft check raised, hard gates first
 * @param failedOutright {@code true} exactly when a soft result is {@link CheckResult#blocking()} (D-3); a hard-gate
 *     failure blocks acceptance through {@link #hardGatesPass()} instead, never through this flag
 */
public record QaResult(
        List<CheckResult> hardGates,
        List<CheckResult> soft,
        double confidence,
        List<QaFinding> findings,
        boolean failedOutright) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies the three list components.
     */
    public QaResult {
        Objects.requireNonNull(hardGates, "hardGates");
        Objects.requireNonNull(soft, "soft");
        Objects.requireNonNull(findings, "findings");
        hardGates = List.copyOf(hardGates);
        soft = List.copyOf(soft);
        findings = List.copyOf(findings);
    }

    /**
     * Whether every hard gate passed.
     *
     * @return {@code true} when every {@link #hardGates()} entry {@link CheckResult#passed()}; {@code false}
     *     otherwise
     */
    public boolean hardGatesPass() {
        return hardGates.stream().allMatch(CheckResult::passed);
    }
}
