package ua.bookloom.pipeline.memory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.qa.CheckResult;

/**
 * The protected-span hard gate: each hidden span's token must come back exactly once, then the hidden texts are put
 * back and the wrapped gate checks the document's own markup.
 */
@Slf4j
final class ProtectedGate implements GateFunction {

    private final Map<String, ProtectedMask> masksBySegmentId;
    private final GateFunction inner;

    ProtectedGate(final Map<String, ProtectedMask> masksBySegmentId, final GateFunction inner) {
        this.masksBySegmentId = Map.copyOf(Objects.requireNonNull(masksBySegmentId, "masksBySegmentId"));
        this.inner = Objects.requireNonNull(inner, "inner");
    }

    @Override
    public GateResult restore(final Segment segment, final String maskedReply) {
        return restoreWith(segment, maskedReply, null);
    }

    @Override
    public GateResult restoreRepairing(final Segment segment, final String maskedReply, final PlaceholderRepair mode) {
        Objects.requireNonNull(mode, "mode");
        return restoreWith(segment, maskedReply, mode);
    }

    private GateResult restoreWith(
            final Segment segment, final String maskedReply, @Nullable final PlaceholderRepair mode) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedReply, "maskedReply");
        final ProtectedMask mask = masksBySegmentId.get(segment.id());
        if (mask == null || (mask.spans().isEmpty() && mask.folded().isEmpty())) {
            log.debug("Protected-span gate segment={} outcome=no-spans", segment.id());
            return passOn(segment, maskedReply, mode);
        }
        final List<String> replyTokens = Tokens.inOrder(maskedReply);
        for (final ProtectedSpan span : mask.spans()) {
            final int count = Collections.frequency(replyTokens, span.token());
            if (count != 1) {
                return failed(segment.id(), mask, span, count, replyTokens);
            }
        }
        log.debug(
                "Protected-span gate segment={} outcome=Restored spans={} folded={}",
                segment.id(),
                mask.spans().size(),
                mask.folded().size());
        final String restored = substitute(maskedReply, mask);
        return mask.folded().isEmpty() ? passOn(segment, restored, mode) : unfold(segment, restored, mask, mode);
    }

    private GateResult passOn(final Segment segment, final String text, @Nullable final PlaceholderRepair mode) {
        return mode == null ? inner.restore(segment, text) : inner.restoreRepairing(segment, text, mode);
    }

    // A drop cap folded out of the shown text comes back by position; a reply missing only those tokens needs no
    // finding, because putting them back is the plan, not a repair of the model's work.
    private GateResult unfold(
            final Segment segment,
            final String text,
            final ProtectedMask mask,
            @Nullable final PlaceholderRepair mode) {
        final boolean onlyFolded = DropCaps.onlyFoldedMissing(segment.masked(), text, mask.folded());
        final GateResult result =
                inner.restoreRepairing(segment, text, mode == null ? PlaceholderRepair.RESTORE_MISSING : mode);
        log.debug(
                "Drop caps put back segment={} onlyFoldedMissing={} restored={}",
                segment.id(),
                onlyFolded,
                result instanceof GateResult.Restored);
        return onlyFolded && result instanceof GateResult.Restored restored ? restored.withoutAutoRepair() : result;
    }

    private static GateResult failed(
            final String segmentId,
            final ProtectedMask mask,
            final ProtectedSpan span,
            final int count,
            final List<String> replyTokens) {
        final List<String> expected =
                mask.spans().stream().map(ProtectedSpan::token).toList();
        final List<String> observed =
                replyTokens.stream().filter(expected::contains).toList();
        log.warn(
                "Protected-span gate refused segment={} token={} count={} expected={} observed={}",
                segmentId,
                span.token(),
                count,
                expected,
                observed);
        final String note =
                "The protected token " + span.token() + " came back " + count + " times instead of exactly once.";
        final QaFinding finding = Objects.requireNonNull(
                CheckResult.hardGateFailed(span.check(), note).finding());
        final AppError error = AppError.of(
                ErrorCode.validation,
                "Protected text was not returned",
                "The translation dropped or repeated a token standing for a locked name or a kept foreign phrase.",
                SafeDetails.empty().withPlaceholderMultiset(expected, observed).render(),
                null);
        return new GateResult.GateFailed(finding, error);
    }

    private static String substitute(final String reply, final ProtectedMask mask) {
        final Map<String, String> restoredByToken = new HashMap<>();
        mask.spans().forEach(span -> restoredByToken.put(span.token(), span.restored()));
        final Matcher matcher = Tokens.matcher(reply);
        final StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            final String restored = restoredByToken.get(matcher.group());
            if (restored != null) {
                out.append(reply, cursor, matcher.start()).append(restored);
                cursor = matcher.end();
            }
        }
        return out.append(reply, cursor, reply.length()).toString();
    }
}
