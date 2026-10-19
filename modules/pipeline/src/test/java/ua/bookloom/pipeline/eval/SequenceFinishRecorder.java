package ua.bookloom.pipeline.eval;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Passes every call to the model under test and counts the reviewer replies the provider cut off at its output cap
 * ({@code finish=LENGTH}), which the run itself only treats as an unreadable reply and does not report.
 */
final class SequenceFinishRecorder implements ChatModel {

    private final ChatModel model;
    private final AtomicInteger reviewerTruncated = new AtomicInteger();

    SequenceFinishRecorder(final ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    int reviewerTruncated() {
        return reviewerTruncated.get();
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return seen(request, model.chat(request));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        return seen(request, model.chat(request, attempts));
    }

    private Result<ChatResponse> seen(final ChatRequest request, final Result<ChatResponse> result) {
        final ChatResponse response = result.data();
        if (response != null
                && request.callKind() == CallKind.REVIEW
                && response.finishReason() == FinishReason.LENGTH) {
            reviewerTruncated.incrementAndGet();
        }
        return result;
    }
}
