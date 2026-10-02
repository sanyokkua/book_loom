package ua.bookloom.pipeline.heal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.qa.CheckName;

/** The innermost gate: {@link DocumentPort#unmask}'s answer read as a {@link GateResult}. */
@Slf4j
@RequiredArgsConstructor
final class DocumentGate implements GateFunction {

    private final DocumentPort documents;
    private final BookFormat format;

    @Override
    public GateResult restore(final Segment segment, final String maskedReply) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedReply, "maskedReply");
        final Result<String> unmasked = documents.unmask(format, segment, maskedReply);
        final GateResult result = unmasked.isOk()
                ? new GateResult.Restored(maskedReply, Objects.requireNonNull(unmasked.data()))
                : failed(Objects.requireNonNull(unmasked.error()), maskedReply);
        logOutcome(segment.id(), result);
        return result;
    }

    @Override
    public GateResult restoreRepairing(final Segment segment, final String maskedReply, final PlaceholderRepair mode) {
        Objects.requireNonNull(mode, "mode");
        final GateResult first = restore(segment, maskedReply);
        if (!(first instanceof GateResult.GateFailed)) {
            return first;
        }
        final Result<String> repaired = documents.repairPlaceholders(segment, maskedReply, mode);
        if (repaired.isErr()) {
            log.debug("Placeholder repair segment={} mode={} outcome=none", segment.id(), mode);
            return first;
        }
        final String text = Objects.requireNonNull(repaired.data());
        log.info(
                "Placeholders restored without a model: put back {}, removed {} segment={} mode={}",
                surplus(text, maskedReply),
                surplus(maskedReply, text),
                segment.id(),
                mode);
        return switch (restore(segment, text)) {
            case GateResult.Restored restored ->
                new GateResult.Restored(restored.maskedForm(), restored.restored(), autoRepairFinding(mode));
            case GateResult.GateFailed _ -> first;
            case GateResult.StepError stepError -> stepError;
        };
    }

    /** How many tokens {@code text} holds beyond those {@code other} holds, repeats counted. */
    private static int surplus(final String text, final String other) {
        final Map<String, Integer> counts = new HashMap<>();
        Tokens.inOrder(text).forEach(token -> counts.merge(token, 1, Integer::sum));
        Tokens.inOrder(other).forEach(token -> counts.merge(token, -1, Integer::sum));
        return counts.values().stream().mapToInt(count -> Math.max(0, count)).sum();
    }

    private static QaFinding autoRepairFinding(final PlaceholderRepair mode) {
        final String note =
                switch (mode) {
                    case RESTORE_MISSING ->
                        "Markup auto-restored: a formatting placeholder the model dropped or repeated was"
                                + " put back without a model. Check that the formatting covers the right words.";
                    case REWRAP_ALL ->
                        "Markup auto-restored: every formatting placeholder was placed again by position"
                                + " without a model. Check that the formatting covers the right words.";
                };
        return new QaFinding(CheckName.PLACEHOLDER.findingKind(), Severity.LOW, note, CheckName.PLACEHOLDER.raisedBy());
    }

    private static GateResult failed(final AppError error, final String candidate) {
        if (error.code() != ErrorCode.validation) {
            return new GateResult.StepError(error);
        }
        final QaFinding finding = new QaFinding(
                CheckName.PLACEHOLDER.findingKind(), Severity.HIGH, error.message(), CheckName.PLACEHOLDER.raisedBy());
        return new GateResult.GateFailed(finding, error, candidate);
    }

    private static void logOutcome(final String segmentId, final GateResult result) {
        switch (result) {
            case GateResult.Restored restored -> log.debug("Document gate segment={} outcome=Restored", segmentId);
            case GateResult.GateFailed failed ->
                log.debug(
                        "Document gate segment={} outcome=GateFailed raisedBy={}",
                        segmentId,
                        failed.finding().raisedBy());
            case GateResult.StepError stepError ->
                log.debug(
                        "Document gate segment={} outcome=StepError code={}",
                        segmentId,
                        stepError.error().code());
        }
    }
}
