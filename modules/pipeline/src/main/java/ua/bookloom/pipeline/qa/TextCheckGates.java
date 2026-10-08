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
import ua.bookloom.pipeline.checks.CharacterName;
import ua.bookloom.pipeline.checks.CharacterNames;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.GenderCheck;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.checks.WordValidator;
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

    static List<CheckResult> run(final SoftCheckInput input, final WordValidator words) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(words, "words");
        if (ForeignMarking.isMarked(input)) {
            log.debug("Text checks skipped: a kept foreign passage");
            return List.of();
        }
        final String target = NameRemoval.targetWithoutNames(input);
        final List<CheckResult> results = new ArrayList<>(TextChecks.run(
                        NameRemoval.sourceWithoutNames(input),
                        target,
                        input.sourceLanguage(),
                        input.targetLanguage(),
                        input.glossaryPairs())
                .stream()
                .map(TextCheckGates::resultOf)
                .toList());
        AlphabetCheck.find(input.sourceDisplayText(), target, input.targetLanguage()).stream()
                .map(TextCheckGates::resultOf)
                .forEach(results::add);
        genderResult(input, target).ifPresent(results::add);
        characterResult(input, target).ifPresent(results::add);
        wordResult(input, target, words).ifPresent(results::add);
        return List.copyOf(results);
    }

    // One low note per segment that names every doubtful word; it never blocks, because a real rare word can be
    // missing.
    private static Optional<CheckResult> wordResult(
            final SoftCheckInput input, final String target, final WordValidator words) {
        final List<CheckFinding> found = words.find(target, input.targetLanguage());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        log.debug("Word validator doubts {} word(s)", found.size());
        final String note = found.stream().map(CheckFinding::note).collect(Collectors.joining(" "));
        final CheckName name = CheckName.UNKNOWN_WORD;
        return Optional.of(CheckResult.passWithNotice(
                name, new QaFinding(name.findingKind(), Severity.LOW, note, name.raisedBy())));
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

    // One notice per segment that names every word that disagrees with a woman's name, like the narrator's notice.
    private static Optional<CheckResult> characterResult(final SoftCheckInput input, final String target) {
        final List<CharacterName> names = CharacterNames.of(input.characters(), input.glossaryPairs());
        if (names.isEmpty()) {
            return Optional.empty();
        }
        final GenderCheck check = GenderChecks.named(LanguageRules.bundled().genderCheck(input.targetLanguage()));
        final List<CheckFinding> found = check.findCharacters(target, names);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        log.debug("Character agreement found {} word(s) for {} character(s)", found.size(), names.size());
        final String note = found.stream().map(CheckFinding::note).collect(Collectors.joining(" "));
        final CheckName name = CheckName.NAME_GENDER;
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
            case UNKNOWN_WORD -> CheckName.UNKNOWN_WORD;
            case ALPHABET -> CheckName.ALPHABET;
            case SENTENCE_MISSING -> CheckName.SENTENCE_COUNT;
            case VOCATIVE_MISSING -> CheckName.VOCATIVE;
            case PROTOCOL_LEAK -> CheckName.PROTOCOL_LEAK;
            case NUMBER_CHANGED -> CheckName.NUMBER;
            case NAME_SWAP -> CheckName.NAME_SWAP;
            case NAME_GENDER -> CheckName.NAME_GENDER;
        };
    }
}
