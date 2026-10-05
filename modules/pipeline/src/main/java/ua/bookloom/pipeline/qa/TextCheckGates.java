package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.TextChecks;

/**
 * Turns the deterministic text findings into gate results: a blocking finding fails like a hard gate, so the
 * segment is repaired or flagged before any reviewer reads it, and a soft finding passes with a low note for review.
 * Skipped for a kept foreign passage, whose letters are meant to differ from the target's.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TextCheckGates {

    static List<CheckResult> run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        if (ForeignMarking.isMarked(input)) {
            log.debug("Text checks skipped: a kept foreign passage");
            return List.of();
        }
        return TextChecks.run(
                        NameRemoval.sourceWithoutNames(input),
                        NameRemoval.targetWithoutNames(input),
                        input.sourceLanguage(),
                        input.targetLanguage())
                .stream()
                .map(TextCheckGates::resultOf)
                .toList();
    }

    private static CheckResult resultOf(final CheckFinding finding) {
        final CheckName check = checkOf(finding);
        return finding.blocking()
                ? CheckResult.hardGateFailed(check, finding.note())
                : CheckResult.passWithNotice(
                        check, new QaFinding(check.findingKind(), Severity.LOW, finding.note(), check.raisedBy()));
    }

    private static CheckName checkOf(final CheckFinding finding) {
        return switch (finding.kind()) {
            case MIXED_SCRIPT -> CheckName.SCRIPT_PURITY;
            case UNBALANCED_QUOTES -> CheckName.QUOTE_BALANCE;
            case LEFTOVER_LANGUAGE -> CheckName.LANGUAGE_IDENTITY;
            case DUPLICATE_WORD -> CheckName.DUPLICATE_WORD;
            case SPACING -> CheckName.SPACING;
        };
    }
}
