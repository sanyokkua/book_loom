package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.LockedRendering;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.typography.QuoteRepair;

/**
 * Repairs a candidate whose only blocker is the quote-balance check, with no model call: the deterministic
 * {@link QuoteRepair} runs on the candidate, the result goes back through the gate and the checks, and it replaces the
 * candidate only when the quote blocker is gone. The change is recorded as a low {@code normalised} note.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuoteFixUp {

    private static final Set<String> ONLY_QUOTES = Set.of(CheckName.QUOTE_BALANCE.raisedBy());

    /**
     * A candidate after its quote marks were repaired.
     *
     * @param maskedCandidate the repaired candidate, protected-span tokens still in it
     * @param restored the gate's answer for it, carrying the merged typography note
     * @param qa the repaired candidate's evaluation
     */
    record Fixed(String maskedCandidate, GateResult.Restored restored, QaResult qa) {}

    /**
     * A drafted segment after its quote marks were repaired.
     *
     * @param outcome the draft with the repaired reply, restored forms and note
     * @param qa the repaired draft's evaluation
     */
    record FixedDraft(DraftOutcome.Drafted outcome, QaResult qa) {}

    static Optional<FixedDraft> fixDraft(
            final DraftOutcome.Drafted draft, final QaResult qa, final GateFunction gate, final LoopSettings settings) {
        if (draft.restoredTarget() == null) {
            return Optional.empty();
        }
        return fix(
                        draft.segment(),
                        draft.maskedSource(),
                        draft.lockedRenderings(),
                        draft.maskedReply(),
                        qa,
                        draft.normalised(),
                        gate,
                        settings)
                .map(fixed -> new FixedDraft(
                        new DraftOutcome.Drafted(
                                draft.segment(),
                                draft.maskedSource(),
                                draft.lockedRenderings(),
                                fixed.maskedCandidate(),
                                fixed.restored().maskedForm(),
                                fixed.restored().restored(),
                                null,
                                draft.pieceRedraft(),
                                fixed.restored().autoRepair(),
                                null,
                                fixed.restored().normalised()),
                        fixed.qa()));
    }

    static Optional<Fixed> fix(
            final Segment segment,
            final String maskedSource,
            final List<LockedRendering> lockedRenderings,
            final String candidate,
            final QaResult qa,
            @Nullable final QaFinding earlierNote,
            final GateFunction gate,
            final LoopSettings settings) {
        if (!Blockers.of(qa).equals(ONLY_QUOTES)) {
            return Optional.empty();
        }
        final QuoteRepair.Repaired repaired = QuoteRepair.repair(
                maskedSource,
                candidate,
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage());
        return repaired.isChanged()
                ? reevaluated(segment, maskedSource, lockedRenderings, earlierNote, repaired, gate, settings)
                : Optional.empty();
    }

    private static Optional<Fixed> reevaluated(
            final Segment segment,
            final String maskedSource,
            final List<LockedRendering> lockedRenderings,
            @Nullable final QaFinding earlierNote,
            final QuoteRepair.Repaired repaired,
            final GateFunction gate,
            final LoopSettings settings) {
        if (!(gate.restoreRepairing(segment, repaired.text(), PlaceholderRepair.RESTORE_MISSING)
                instanceof GateResult.Restored restored)) {
            log.debug("Quote fix-up segment={} discarded: the repaired text failed the gate", segment.id());
            return Optional.empty();
        }
        final GateResult.Restored noted = restored.withNormalised(merged(restored.normalised(), earlierNote, repaired));
        final String repairedCandidate = noted.candidateOr(repaired.text());
        final QaResult fixed = QaEvaluation.evaluate(
                DraftEvaluation.notices(segment.id(), noted.autoRepair(), noted.normalised()),
                segment,
                maskedSource,
                repairedCandidate,
                noted.maskedForm(),
                settings,
                lockedRenderings);
        log.debug("Quote fix-up segment={} marks={} blockers={}", segment.id(), repaired.marks(), Blockers.of(fixed));
        return Blockers.of(fixed).contains(CheckName.QUOTE_BALANCE.raisedBy())
                ? Optional.empty()
                : Optional.of(new Fixed(repairedCandidate, noted, fixed));
    }

    // The gate restored the repaired text and may have noted its own typography change; the earlier candidate's note
    // (typography already applied before the repair) is kept so the review sees every change.
    private static QaFinding merged(
            @Nullable final QaFinding gateNote,
            @Nullable final QaFinding earlierNote,
            final QuoteRepair.Repaired repaired) {
        final String notes = Stream.of(earlierNote, gateNote)
                .filter(Objects::nonNull)
                .map(QaFinding::note)
                .distinct()
                .reduce("", (all, note) -> all.isEmpty() ? note : all + " " + note);
        final String note = notes.isEmpty() ? repaired.note() : notes + " " + repaired.note();
        final CheckName typography = CheckName.TYPOGRAPHY;
        return new QaFinding(typography.findingKind(), Severity.LOW, note, typography.raisedBy());
    }
}
