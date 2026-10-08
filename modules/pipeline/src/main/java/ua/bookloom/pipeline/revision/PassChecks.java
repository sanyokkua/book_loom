package ua.bookloom.pipeline.revision;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.audit.FinalAudit;
import ua.bookloom.pipeline.heal.AcceptanceRule;
import ua.bookloom.pipeline.heal.QaEvaluation;
import ua.bookloom.pipeline.heal.RoundProgress;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.qa.CheckName;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * The rule every text the pass would store is held to against the text it replaces: it passes the checks with the
 * book's real glossary, is no worse by them, keeps what the old text had ({@link RevisionGuards}) and brings no audit
 * doubt the old text did not have. Both the retry of a doubted segment and the check against the neighbours use it,
 * so a pass never trades one defect for another.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PassChecks {

    static final String CHECKS = "checks";
    static final String WORSE = "worse";

    /** The checks whose failure says the old text is still in the source language. */
    private static final Set<CheckName> UNTRANSLATED =
            EnumSet.of(CheckName.ECHO, CheckName.SCRIPT, CheckName.LANGUAGE_IDENTITY);

    /**
     * Why a new text may not replace the old one.
     *
     * @param inputs what the pass read as it started
     * @param segment the opened book's segment
     * @param before the old text, masked
     * @param candidate the new text as the model wrote it, masked
     * @param after the new text restored through the gate, masked
     * @param mode how the guards compare the counts
     * @return the broken rule's name, or empty when the new text may replace the old one
     */
    static Optional<String> refusal(
            final PassInputs inputs,
            final Segment segment,
            final String before,
            final String candidate,
            final String after,
            final RevisionGuards.Mode mode) {
        final QaResult fresh = evaluate(inputs, segment, candidate, after);
        final QaResult old = evaluate(inputs, segment, before, before);
        final boolean untranslated = isUntranslated(old);
        final Optional<String> refusal = AcceptanceRule.accepts(fresh, 0)
                ? guards(segment, before, after, mode, untranslated)
                        .or(() -> RoundProgress.isWorse(fresh, old) ? Optional.of(WORSE) : Optional.empty())
                        .or(() -> newDoubt(inputs, segment, before, after))
                : Optional.of(CHECKS);
        log.debug(
                "Pass checks segmentId={} mode={} oldUntranslated={} hardGatesPass={} failedOutright={} findings={}"
                        + " refusal={}",
                segment.id(),
                mode,
                untranslated,
                fresh.hardGatesPass(),
                fresh.failedOutright(),
                fresh.findings().stream().map(QaFinding::kind).toList(),
                refusal.orElse("none"));
        return refusal;
    }

    /**
     * Whether a new text that may replace the old one is also better: it fails fewer checks, or the audit doubts it
     * by fewer checks.
     *
     * @param inputs what the pass read as it started
     * @param segment the opened book's segment
     * @param before the old text, masked
     * @param after the new text, masked
     * @return {@code true} if the new text is better, {@code false} if it is only as good
     */
    static boolean isBetter(final PassInputs inputs, final Segment segment, final String before, final String after) {
        final QaResult fresh = evaluate(inputs, segment, after, after);
        final QaResult old = evaluate(inputs, segment, before, before);
        final int oldDoubts =
                FinalAudit.checksOf(inputs.book(), segment, before).size();
        final int newDoubts = FinalAudit.checksOf(inputs.book(), segment, after).size();
        final boolean better =
                !AcceptanceRule.accepts(old, 0) || RoundProgress.isWorse(old, fresh) || newDoubts < oldDoubts;
        log.debug(
                "Pass better segmentId={} oldAccepted={} doubts={}->{} better={}",
                segment.id(),
                AcceptanceRule.accepts(old, 0),
                oldDoubts,
                newDoubts,
                better);
        return better;
    }

    // An old text still in the source language (an echo, the wrong script, a leftover paragraph) is no translation to
    // keep counts of: a translation has fewer words than the English it replaces and no Latin run in common with it.
    // The new text is then held to the source's quote marks, dashes and sentences instead, which a translation keeps.
    private static Optional<String> guards(
            final Segment segment,
            final String before,
            final String after,
            final RevisionGuards.Mode mode,
            final boolean untranslated) {
        return untranslated
                ? RevisionGuards.violationOfSource(segment.masked(), after)
                : RevisionGuards.violation(before, after, mode);
    }

    private static boolean isUntranslated(final QaResult old) {
        return Stream.concat(old.hardGates().stream(), old.soft().stream())
                .anyMatch(result -> !result.passed() && UNTRANSLATED.contains(result.check()));
    }

    private static Optional<String> newDoubt(
            final PassInputs inputs, final Segment segment, final String before, final String after) {
        final List<String> old = FinalAudit.checksOf(inputs.book(), segment, before);
        return FinalAudit.checksOf(inputs.book(), segment, after).stream()
                .filter(check -> !old.contains(check))
                .findFirst();
    }

    private static QaResult evaluate(
            final PassInputs inputs, final Segment segment, final String candidate, final String maskedForm) {
        return QaEvaluation.evaluate(
                List.of(),
                segment,
                segment.masked(),
                candidate,
                maskedForm,
                inputs.frame(),
                inputs.namePolicy(),
                inputs.glossary().stream().map(GlossaryEntry::term).toList(),
                ProtectedSpans.mask(segment, inputs.frame(), inputs.glossary()).presentLocked(),
                pairs(inputs.glossary()));
    }

    private static List<String> pairs(final List<GlossaryEntry> glossary) {
        return glossary.stream()
                .filter(entry -> entry.target() != null && !entry.target().isBlank())
                .map(entry -> entry.term() + " → " + entry.target())
                .toList();
    }
}
