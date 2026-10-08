package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;
import ua.bookloom.pipeline.checks.GenderChecks;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.heal.DraftEvaluation;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The verdicts of a {@link RunCase}, all computed by the production checks over the project the case stands for — the
 * labels of the corpus are held against them, and the report measures a model's reply by the same code.
 */
final class RunVerdicts {

    private final RunCase runCase;
    private final EvalProject project;

    private RunVerdicts(final RunCase runCase, final EvalProject project) {
        this.runCase = runCase;
        this.project = project;
    }

    static RunVerdicts of(final RunCase runCase) {
        Objects.requireNonNull(runCase, "runCase");
        final EvalProject project = EvalProject.of(
                EvalProject.Setup.single(
                        runCase.language(),
                        RunCorpus.TARGET_LANGUAGE,
                        runCase.glossary(),
                        runCase.surroundings(),
                        runCase.source()),
                (kind, segmentId, request) -> {
                    throw new IllegalStateException("a verdict sends no model call");
                });
        return new RunVerdicts(runCase, project);
    }

    EvalProject project() {
        return project;
    }

    /** Whether the case's own production check fires on {@code candidate}. */
    boolean fires(final String candidate) {
        return switch (runCase.check()) {
            case QUOTES -> hasBlocking(candidate, FindingKind.UNBALANCED_QUOTES);
            case SCRIPT -> hasBlocking(candidate, FindingKind.MIXED_SCRIPT);
            case ALPHABET ->
                qa(candidate).findings().stream()
                        .anyMatch(finding -> CheckName.ALPHABET.raisedBy().equals(finding.raisedBy()));
            case GENDER ->
                !GenderChecks.named(RunCorpus.TARGET_LANGUAGE)
                        .find(DisplayText.of(candidate), narratorGender())
                        .isEmpty();
            case LENGTH ->
                qa(candidate).soft().stream()
                        .anyMatch(result -> result.check() == CheckName.LENGTH && !result.passed());
            case OMISSION -> hardGateFails(candidate, CheckName.SENTENCE_COUNT);
            case VOCATIVE -> hardGateFails(candidate, CheckName.VOCATIVE);
            case RESIDUE -> hasBlocking(candidate, FindingKind.PROTOCOL_LEAK);
            case NONE -> false;
        };
    }

    private boolean hardGateFails(final String candidate, final CheckName name) {
        return qa(candidate).hardGates().stream().anyMatch(result -> result.check() == name && !result.passed());
    }

    /** Whether the run would refuse {@code candidate} before any reviewer reads it: a failed hard gate or soft check. */
    boolean refuses(final String candidate) {
        final QaResult qa = qa(candidate);
        return !qa.hardGatesPass() || qa.failedOutright();
    }

    /** The blocking findings of the deterministic text checks, whatever the case's own check is. */
    boolean hasAnyBlockingFinding(final String candidate) {
        return findings(candidate).stream().anyMatch(CheckFinding::blocking);
    }

    private boolean hasBlocking(final String candidate, final FindingKind kind) {
        return findings(candidate).stream().anyMatch(finding -> finding.blocking() && finding.kind() == kind);
    }

    private List<CheckFinding> findings(final String candidate) {
        return TextChecks.run(
                DisplayText.of(runCase.source()),
                DisplayText.of(candidate),
                runCase.language(),
                RunCorpus.TARGET_LANGUAGE);
    }

    private Gender narratorGender() {
        final Narrator narrator = runCase.surroundings().narrator();
        return narrator == null ? Gender.UNKNOWN : narrator.gender();
    }

    private QaResult qa(final String candidate) {
        final var drafted = new DraftOutcome.Drafted(
                project.segment(0), runCase.source(), List.of(), candidate, candidate, candidate, null);
        return DraftEvaluation.evaluate(drafted, project.loop());
    }
}
