package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.DraftStep;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * One model call of the draft step — a whole segment's or a piece's — with a thrown failure turned into the
 * {@code internal} error the run routes, so neither the draft step nor the piece drafter lets an exception escape.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DraftCalls {

    static Result<ChatResponse> call(
            final DraftStep step,
            final Segment segment,
            final ChatRequest request,
            final CallDescriptor descriptor,
            final ModelCalls through) {
        log.debug(
                "Calling chat model segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        try {
            final Result<ChatResponse> result = Objects.requireNonNull(
                    through.callAbout(step.callKind(), List.of(segment.id()), request, descriptor), "model result");
            log.debug("Chat model completed segmentId={} result={}", segment.id(), result.isOk() ? "success" : "error");
            return result;
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Translation failed",
                    "The model could not translate this segment.",
                    null,
                    cause);
            log.debug("Chat model completed segmentId={} result=thrown errorCode={}", segment.id(), error.code());
            log.error("Unexpected model failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }
}
