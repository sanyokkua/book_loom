package ua.bookloom.pipeline.heal;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
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
                : failed(Objects.requireNonNull(unmasked.error()));
        logOutcome(segment.id(), result);
        return result;
    }

    private static GateResult failed(final AppError error) {
        if (error.code() != ErrorCode.validation) {
            return new GateResult.StepError(error);
        }
        final QaFinding finding = new QaFinding(
                CheckName.PLACEHOLDER.findingKind(), Severity.HIGH, error.message(), CheckName.PLACEHOLDER.raisedBy());
        return new GateResult.GateFailed(finding, error);
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
