package ua.bookloom.pipeline.typography;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.checks.ReplyClosers;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.qa.CheckName;

/**
 * Puts the typography pass in front of a gate, so every candidate target — a draft, a repair round, a retry, a reused
 * memory entry — is normalised on its way in and no caller has to remember to. It sits outermost, ahead of the
 * protected-span gate, because only there are locked names and kept foreign runs still tokens the pass cannot read.
 * A change is recorded as a low {@code normalised} note on the restored result; the gate's own verdict is never
 * altered, and a person's edit does not pass through here (export normalises it instead).
 */
@Slf4j
public final class TypographyGate implements GateFunction {

    private static final String CLOSERS_NOTE = "Removed reply closers from the end of the text.";

    private final GateFunction inner;
    private final String targetLanguage;

    private TypographyGate(final GateFunction inner, final String targetLanguage) {
        this.inner = inner;
        this.targetLanguage = targetLanguage;
    }

    /**
     * Wraps a gate.
     *
     * @param inner the non-null gate every normalised candidate is passed to
     * @param targetLanguage the non-null BCP 47 tag of the language the candidates are written in
     * @return the wrapping gate
     */
    public static GateFunction around(final GateFunction inner, final String targetLanguage) {
        return new TypographyGate(
                Objects.requireNonNull(inner, "inner"), Objects.requireNonNull(targetLanguage, "targetLanguage"));
    }

    @Override
    public GateResult restore(final Segment segment, final String maskedReply) {
        final Cleaned cleaned = clean(segment, maskedReply);
        return noted(inner.restore(segment, cleaned.normalised().text()), cleaned, segment);
    }

    @Override
    public GateResult restoreRepairing(final Segment segment, final String maskedReply, final PlaceholderRepair mode) {
        final Cleaned cleaned = clean(segment, maskedReply);
        return noted(inner.restoreRepairing(segment, cleaned.normalised().text(), mode), cleaned, segment);
    }

    // The closers go first, before any check reads the text: a quote and a brace at the end are reply syntax, and the
    // residue check would otherwise block a draft that is right but for them.
    private Cleaned clean(final Segment segment, final String maskedReply) {
        final Optional<String> withoutClosers = ReplyClosers.strip(segment.masked(), maskedReply);
        withoutClosers.ifPresent(text -> log.debug(
                "Reply closers cut segment={} before={} after={}", segment.id(), maskedReply.length(), text.length()));
        final Normalisation normalised =
                TypographyNormalizer.normalise(segment.masked(), withoutClosers.orElse(maskedReply), targetLanguage);
        return new Cleaned(normalised, withoutClosers.isPresent());
    }

    private static GateResult noted(final GateResult result, final Cleaned cleaned, final Segment segment) {
        if (!(result instanceof GateResult.Restored restored)) {
            return result;
        }
        final Normalisation normalised = cleaned.normalised();
        final GateResult.Restored withCandidate = restored.withMaskedCandidate(normalised.text());
        if (!cleaned.closersCut() && !normalised.isChanged()) {
            return withCandidate;
        }
        final String note =
                (cleaned.closersCut() ? CLOSERS_NOTE + (normalised.isChanged() ? " " : "") : "") + normalised.note();
        log.trace("Typography note segment={} {}", segment.id(), note);
        return withCandidate.withNormalised(
                new QaFinding(CheckName.TYPOGRAPHY.findingKind(), Severity.LOW, note, CheckName.TYPOGRAPHY.raisedBy()));
    }

    private record Cleaned(Normalisation normalised, boolean closersCut) {}
}
