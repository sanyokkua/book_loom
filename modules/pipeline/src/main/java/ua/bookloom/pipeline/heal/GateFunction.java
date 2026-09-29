package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;

/**
 * Restores one candidate masked target through a segment's hard gates: {@link DocumentPort#unmask} checks the
 * placeholder multiset, pair order and nesting ({@code specs/quality-gates/spec.md} "Treat placeholder integrity,
 * including the order of paired placeholders, as a hard gate"). A protected-span gate wraps another gate and restores
 * hidden names and kept runs first, so a caller never learns which gates stand behind the function it holds.
 *
 * <p>{@code maskedReply} is already restored into the segment's own leading/trailing whitespace — the caller (the
 * draft step, {@link SegmentHealer}) computes that once with {@link ua.bookloom.pipeline.WhitespaceRestoration}, so
 * the gate never re-derives a second, possibly different, copy of it.
 */
@FunctionalInterface
public interface GateFunction {

    /**
     * Restores {@code maskedReply} into {@code segment}'s markup.
     *
     * @param segment the segment being restored
     * @param maskedReply the candidate target, already restored into the segment's own whitespace, still carrying
     *     its {@code ⟦gN⟧} tokens
     * @return {@link GateResult.Restored} when every gate passed, {@link GateResult.GateFailed} when one refused the
     *     candidate, {@link GateResult.StepError} when the gate itself failed
     */
    GateResult restore(Segment segment, String maskedReply);

    /**
     * Builds the gate over {@code :document}'s real placeholder validation.
     *
     * @param documents the port every restore call is delegated to
     * @param format the segment's book format, needed by {@link DocumentPort#unmask}
     * @return the document gate
     */
    static GateFunction of(final DocumentPort documents, final BookFormat format) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(format, "format");
        return new DocumentGate(documents, format);
    }
}
