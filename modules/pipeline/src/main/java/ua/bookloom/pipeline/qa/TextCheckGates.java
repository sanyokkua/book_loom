package ua.bookloom.pipeline.qa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.GenderCheck;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.prompt.LanguageRules;

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
        final String target = NameRemoval.targetWithoutNames(input);
        final List<CheckResult> results =
                new ArrayList<>(TextChecks.run(
                                NameRemoval.sourceWithoutNames(input),
                                target,
                                input.sourceLanguage(),
                                input.targetLanguage())
                        .stream()
                        .map(TextCheckGates::resultOf)
                        .toList());
        genderResult(input, target).ifPresent(results::add);
        return List.copyOf(results);
    }

    // One notice per segment that names every wrong word, so the repair prompt gets them all in a single finding.
    private static Optional<CheckResult> genderResult(final SoftCheckInput input, final String target) {
        if (!input.narrator().hasCheckableGender()) {
            return Optional.empty();
        }
        final GenderCheck check = GenderChecks.named(LanguageRules.bundled().genderCheck(input.targetLanguage()));
        final List<CheckFinding> found = check.find(target, input.narrator().gender());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        log.debug(
                "Gender check found {} word(s) for the narrator {}",
                found.size(),
                input.narrator().gender());
        final String note = found.stream().map(CheckFinding::note).collect(Collectors.joining(" "));
        final CheckName name = CheckName.GENDER;
        return Optional.of(CheckResult.passWithNotice(
                name, new QaFinding(name.findingKind(), Severity.LOW, note, name.raisedBy())));
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
            case GENDER -> CheckName.GENDER;
        };
    }
}
