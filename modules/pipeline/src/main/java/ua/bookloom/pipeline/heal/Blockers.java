package ua.bookloom.pipeline.heal;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The deterministic blockers of one evaluated candidate, named by the check that raised each: a failed hard gate —
 * which includes every blocking text check — or a soft check that failed outright. A reviewer edit may keep or shrink
 * this set but never grow it.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Blockers {

    /**
     * The checks that block acceptance of an evaluated candidate.
     *
     * @param qa the candidate's evaluation; never null
     * @return the {@link QaFinding#raisedBy()} of every blocking result, in evaluation order; empty when none blocks
     */
    static Set<String> of(final QaResult qa) {
        Objects.requireNonNull(qa, "qa");
        final Set<String> names = new LinkedHashSet<>();
        qa.hardGates().stream().filter(result -> !result.passed()).forEach(result -> add(names, result));
        qa.soft().stream().filter(CheckResult::blocking).forEach(result -> add(names, result));
        return names;
    }

    private static void add(final Set<String> names, final CheckResult result) {
        final QaFinding finding = result.finding();
        names.add(finding == null ? result.check().raisedBy() : finding.raisedBy());
    }
}
