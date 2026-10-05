package ua.bookloom.pipeline.heal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.RoundStarted;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditOutcome;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewEdit;
import ua.bookloom.pipeline.reviewer.ReviewItem;

/**
 * Turns the reviewer's answer about one drafted segment into a {@link Resolution} by verifying it in code. An
 * {@code ok} changes nothing. Edits are applied one by one when {@link EditVerifier} accepts each; a segment whose edits
 * were refused gets one directed fix that names the reviewer's quote, and the issue counts as resolved only when the
 * fixed text no longer holds that quote and the checks pass. A rewrite replaces the draft only when the whole text passes
 * every check, otherwise the draft stays and the segment is flagged with the evidence. Built fresh per chunk by
 * {@link SegmentHealer}; calling it again for the same segment repeats the same steps, which is what makes a segment
 * whose directed fix call failed resumable.
 */
@Slf4j
final class ReviewResolver {

    private static final String REWRITE_KIND = "rewrite";

    private final EditApplier applier;
    private final DirectedFix directedFix;
    private final RoundEvaluator evaluator;
    private final LoopSettings settings;
    private final ModelCalls calls;

    ReviewResolver(
            final EditApplier applier,
            final DirectedFix directedFix,
            final RoundEvaluator evaluator,
            final LoopSettings settings,
            final ModelCalls calls) {
        this.applier = Objects.requireNonNull(applier, "applier");
        this.directedFix = Objects.requireNonNull(directedFix, "directedFix");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.calls = Objects.requireNonNull(calls, "calls");
    }

    Result<Resolution> resolve(final DraftOutcome.Drafted outcome, final QaResult initialQa, final ReviewItem item) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(initialQa, "initialQa");
        Objects.requireNonNull(item, "item");
        log.debug(
                "Resolving review segment={} status={} edits={}",
                item.segmentId(),
                item.status(),
                item.edits().size());
        final MachineTarget draft = MachineTarget.of(outcome, initialQa);
        return switch (item.status()) {
            case OK -> Result.ok(new Resolution(draft, initialQa, List.of(), 0, 0, null));
            case EDITS -> applyEdits(outcome, initialQa, draft, item.edits());
            case REWRITE -> tryRewrite(outcome, initialQa, draft, Objects.requireNonNull(item.rewrite()));
        };
    }

    private Result<Resolution> applyEdits(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            final MachineTarget draft,
            final List<ReviewEdit> edits) {
        final Set<String> baseline = Blockers.of(initialQa);
        final EditOutcome applied =
                applier.apply(outcome.maskedReply(), edits, text -> blockersOf(outcome, text), baseline, renderings());
        final List<QaFinding> findings = recordedEdits(applied);
        final Edited edited = evaluateEdited(outcome, initialQa, draft, applied);
        final int rounds = applied.applied().isEmpty() ? 0 : 1;
        if (applied.failed().isEmpty()) {
            return Result.ok(new Resolution(edited.machine(), edited.qa(), findings, rounds, 0, null));
        }
        return fixRefused(outcome, edited, applied.failed(), baseline, findings);
    }

    // The glossary lines read "source → target"; only the target is a rendering a terminology edit may use.
    private List<String> renderings() {
        return settings.glossaryPairs().stream()
                .map(line -> line.substring(line.indexOf('→') + 1).strip())
                .toList();
    }

    private Edited evaluateEdited(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            final MachineTarget draft,
            final EditOutcome applied) {
        if (applied.applied().isEmpty()) {
            return new Edited(draft, initialQa, outcome.maskedReply());
        }
        if (evaluator.evaluateRewrite(outcome, applied.text()) instanceof RoundOutcome.Evaluated evaluated) {
            return new Edited(machineOf(evaluated), evaluated.qa(), evaluated.maskedCandidate());
        }
        // Every edit passed the same evaluation one by one, so this cannot differ; keep the draft rather than guess.
        log.warn(
                "Edited text no longer evaluates segment={}; keeping the draft",
                outcome.segment().id());
        return new Edited(draft, initialQa, outcome.maskedReply());
    }

    private Result<Resolution> fixRefused(
            final DraftOutcome.Drafted outcome,
            final Edited edited,
            final List<EditOutcome.FailedEdit> refused,
            final Set<String> baseline,
            final List<QaFinding> findings) {
        final List<QaFinding> evidence =
                refused.stream().map(ReviewResolver::evidenceOf).toList();
        announceFix(outcome, evidence);
        final RoundOutcome result = evaluator.classify(
                outcome,
                directedFix.fix(
                        outcome.segment(), settings.frame(), outcome.maskedSource(), edited.text(), evidence, calls));
        if (result instanceof RoundOutcome.StepError stepError) {
            return Result.err(stepError.error());
        }
        if (result instanceof RoundOutcome.Evaluated fixed && resolved(fixed, refused, baseline)) {
            log.debug(
                    "Refused edits resolved by the directed fix segment={}",
                    outcome.segment().id());
            return Result.ok(new Resolution(machineOf(fixed), fixed.qa(), findings, 1, 0, null));
        }
        log.debug(
                "Refused edits left unresolved segment={} count={}",
                outcome.segment().id(),
                refused.size());
        final List<QaFinding> all = new ArrayList<>(findings);
        all.addAll(evidence);
        return Result.ok(new Resolution(edited.machine(), edited.qa(), all, 1, refused.size(), null));
    }

    private void announceFix(final DraftOutcome.Drafted outcome, final List<QaFinding> evidence) {
        log.debug(
                "Directed fix for refused edits segment={} count={}",
                outcome.segment().id(),
                evidence.size());
        calls.announce(new RoundStarted(
                outcome.segment().id(),
                1,
                settings.dial().repairRounds(),
                null,
                evidence.getFirst().kind()));
    }

    private static boolean resolved(
            final RoundOutcome.Evaluated fixed,
            final List<EditOutcome.FailedEdit> refused,
            final Set<String> baseline) {
        return fixed.qa().hardGatesPass()
                && baseline.containsAll(Blockers.of(fixed.qa()))
                && refused.stream()
                        .noneMatch(edit -> EditVerifier.occursIn(
                                fixed.maskedCandidate(), edit.edit().quote()));
    }

    private Result<Resolution> tryRewrite(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            final MachineTarget draft,
            final String rewrite) {
        final RoundOutcome result = evaluator.evaluateRewrite(outcome, rewrite);
        if (result instanceof RoundOutcome.StepError stepError) {
            return Result.err(stepError.error());
        }
        if (result instanceof RoundOutcome.Evaluated evaluated && AcceptanceRule.accepts(evaluated.qa(), 0)) {
            log.debug("Rewrite accepted segment={}", outcome.segment().id());
            final QaFinding note = new QaFinding(
                    REWRITE_KIND, Severity.LOW, "The reviewer rewrote this segment.", SegmentOutcomes.REVIEWER);
            return Result.ok(new Resolution(machineOf(evaluated), evaluated.qa(), List.of(note), 1, 0, null));
        }
        final String why = result instanceof RoundOutcome.Evaluated evaluated
                ? "it fails " + String.join(", ", Blockers.of(evaluated.qa()))
                : "it breaks a placeholder token";
        log.debug("Rewrite rejected segment={} why={}", outcome.segment().id(), why);
        final QaFinding evidence = new QaFinding(
                REWRITE_KIND,
                Severity.MEDIUM,
                "The reviewer asked for a rewrite of this segment, but " + why + ", so the draft was kept.",
                SegmentOutcomes.REVIEWER);
        return Result.ok(new Resolution(draft, initialQa, List.of(evidence), 0, 1, null));
    }

    private Optional<Set<String>> blockersOf(final DraftOutcome.Drafted outcome, final String text) {
        return evaluator.evaluateRewrite(outcome, text) instanceof RoundOutcome.Evaluated evaluated
                ? Optional.of(Blockers.of(evaluated.qa()))
                : Optional.empty();
    }

    private static MachineTarget machineOf(final RoundOutcome.Evaluated evaluated) {
        return new MachineTarget(evaluated.restoredTarget(), evaluated.maskedForm());
    }

    private static List<QaFinding> recordedEdits(final EditOutcome applied) {
        final List<QaFinding> findings = new ArrayList<>();
        applied.applied().stream()
                .map(edit -> new AppliedEdit(edit.criterion().wire(), edit.quote(), edit.replacement()))
                .map(AppliedEdit::toFinding)
                .forEach(findings::add);
        applied.notes().stream()
                .map(note -> new QaFinding(
                        note.criterion().wire(),
                        Severity.LOW,
                        "\"" + note.quote() + "\" could read \"" + note.replacement() + "\"",
                        SegmentOutcomes.REVIEWER))
                .forEach(findings::add);
        return findings;
    }

    private static QaFinding evidenceOf(final EditOutcome.FailedEdit refused) {
        final ReviewEdit edit = refused.edit();
        return new QaFinding(
                edit.criterion().wire(),
                Severity.MEDIUM,
                "\"" + edit.quote() + "\" — " + refused.reason().label() + "; the reviewer suggests \""
                        + edit.replacement() + "\"",
                SegmentOutcomes.REVIEWER);
    }

    /** A draft's text after the verified edits: what it stands on, how it evaluated and the masked text. */
    private record Edited(MachineTarget machine, QaResult qa, String text) {}
}
