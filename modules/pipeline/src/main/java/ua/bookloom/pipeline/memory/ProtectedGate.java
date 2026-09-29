package ua.bookloom.pipeline.memory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.SafeDetails;
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
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedReply, "maskedReply");
        final ProtectedMask mask = masksBySegmentId.get(segment.id());
        if (mask == null || mask.spans().isEmpty()) {
            log.debug("Protected-span gate segment={} outcome=no-spans", segment.id());
            return inner.restore(segment, maskedReply);
        }
        final List<String> replyTokens = Tokens.inOrder(maskedReply);
        for (final ProtectedSpan span : mask.spans()) {
            final int count = Collections.frequency(replyTokens, span.token());
            if (count != 1) {
                return failed(segment.id(), mask, span, count, replyTokens);
            }
        }
        log.debug(
                "Protected-span gate segment={} outcome=Restored spans={}",
                segment.id(),
                mask.spans().size());
        return inner.restore(segment, substitute(maskedReply, mask));
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
