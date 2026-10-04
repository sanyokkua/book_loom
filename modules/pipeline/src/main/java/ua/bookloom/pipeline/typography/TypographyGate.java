package ua.bookloom.pipeline.typography;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
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
        final Normalisation normalised = TypographyNormalizer.normalise(maskedReply, targetLanguage);
        return noted(inner.restore(segment, normalised.text()), normalised, segment);
    }

    @Override
    public GateResult restoreRepairing(final Segment segment, final String maskedReply, final PlaceholderRepair mode) {
        final Normalisation normalised = TypographyNormalizer.normalise(maskedReply, targetLanguage);
        return noted(inner.restoreRepairing(segment, normalised.text(), mode), normalised, segment);
    }

    private static GateResult noted(final GateResult result, final Normalisation normalised, final Segment segment) {
        if (normalised.isChanged() && result instanceof GateResult.Restored restored) {
            log.trace("Typography note segment={} {}", segment.id(), normalised.note());
            return restored.withNormalised(new QaFinding(
                    CheckName.TYPOGRAPHY.findingKind(),
                    Severity.LOW,
                    normalised.note(),
                    CheckName.TYPOGRAPHY.raisedBy()));
        }
        return result;
    }
}
