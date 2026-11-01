package ua.bookloom.pipeline.glossary;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.TargetIssue;
import ua.bookloom.pipeline.glossary.TargetValidator.Verdict;

/**
 * The codes a {@link Verdict} crosses the port as: the refusals in rule order, then the review flags, each once. A
 * flag that repeats a refusal (a different word count is both) is left out, so the person reads it once.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TargetIssues {

    static List<TargetIssue> of(final Verdict verdict) {
        final Set<TargetIssue> issues = new LinkedHashSet<>();
        verdict.rejections().forEach(reason -> issues.add(refusal(reason)));
        verdict.flags().stream()
                .map(TargetIssues::flag)
                .filter(flag -> !(flag == TargetIssue.WORD_COUNT_DIFFERS && issues.contains(TargetIssue.WORD_COUNT)))
                .forEach(issues::add);
        return List.copyOf(issues);
    }

    /** The review flags of a verdict alone, as the table shows them on a row. */
    static List<TargetIssue> flagsOf(final Verdict verdict) {
        return verdict.flags().stream().map(TargetIssues::flag).toList();
    }

    private static TargetIssue refusal(final TargetValidator.Reason reason) {
        return switch (reason) {
            case FORBIDDEN_LETTER -> TargetIssue.FORBIDDEN_LETTER;
            case LATIN -> TargetIssue.LATIN;
            case STRESS_MARK -> TargetIssue.STRESS_MARK;
            case CAPITALISATION -> TargetIssue.CAPITALISATION;
            case WORD_COUNT -> TargetIssue.WORD_COUNT;
            case CONVENTION -> TargetIssue.CONVENTION;
            case RESPELLING -> TargetIssue.RESPELLING;
        };
    }

    private static TargetIssue flag(final TargetValidator.Flag flag) {
        return switch (flag) {
            case OBLIQUE_ENDING -> TargetIssue.OBLIQUE_ENDING;
            case WORD_COUNT_DIFFERS -> TargetIssue.WORD_COUNT_DIFFERS;
        };
    }
}
