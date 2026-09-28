package ua.bookloom.pipeline.heal;

import java.util.Objects;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;

/**
 * Restores one candidate masked target into a segment's placeholder gate: {@link DocumentPort#unmask} checks the
 * placeholder multiset, pair order and nesting. A {@code validation} result means the placeholder gate failed; any
 * other failure ends the caller's step ({@code specs/quality-gates/spec.md} "Treat placeholder integrity, including
 * the order of paired placeholders, as a hard gate"). Task 9.3 wraps this to also restore protected spans.
 *
 * <p>{@code maskedTarget} is already restored into the segment's own leading/trailing whitespace — the caller (the
 * draft step, {@link SegmentHealer}) computes that once with {@link ua.bookloom.pipeline.WhitespaceRestoration} and
 * keeps the same value as the recorded masked form, so the gate never re-derives a second, possibly different, copy
 * of it.
 */
@FunctionalInterface
public interface GateFunction {

    /**
     * Restores {@code maskedTarget} into {@code segment}'s markup.
     *
     * @param segment the segment being restored
     * @param maskedTarget the candidate target, already restored into the segment's own whitespace, still carrying
     *     its {@code ⟦gN⟧} tokens
     * @return the restored text, or a failed result — {@code ErrorCode.validation} for a failed placeholder gate,
     *     any other code for a failure that ends the caller's step
     */
    Result<String> restore(Segment segment, String maskedTarget);

    /**
     * Builds the gate over {@code :document}'s real placeholder validation.
     *
     * @param documents the port every restore call is delegated to
     * @param format the segment's book format, needed by {@link DocumentPort#unmask}
     * @return a gate that calls {@code documents.unmask(format, segment, maskedTarget)} directly
     */
    static GateFunction of(final DocumentPort documents, final BookFormat format) {
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(format, "format");
        return (segment, maskedTarget) -> documents.unmask(format, segment, maskedTarget);
    }
}
