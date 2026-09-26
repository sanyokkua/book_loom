package ua.bookloom.pipeline;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;

/**
 * Lets Pause and Stop reach a model call that is already waiting on the provider.
 *
 * <p>The job's control interrupts the calling thread only while it is inside {@link #chat(ChatRequest)}, and the
 * provider client turns that interrupt into {@link ErrorCode#cancelled}. Once a stop or a pause is requested no
 * further request is sent at all, so a repair call can never start behind a button press. The callback runs once per
 * request that really goes out, never for a refused one, so the job can tell listeners a request is outstanding.
 */
@Slf4j
final class CancellableChatModel implements ChatModel {

    private final ChatModel delegate;
    private final JobControl control;
    private final Runnable onCallEntered;

    CancellableChatModel(final ChatModel delegate, final JobControl control, final Runnable onCallEntered) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.control = Objects.requireNonNull(control, "control");
        this.onCallEntered = Objects.requireNonNull(onCallEntered, "onCallEntered");
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        if (!control.enterModelCall()) {
            log.debug("Refused model call because a stop or a pause is already requested state={}", control.state());
            return Result.err(AppError.of(
                    ErrorCode.cancelled,
                    "Model call cancelled",
                    "A stop or a pause was requested, so no request was sent to the provider."));
        }
        try {
            onCallEntered.run();
            return delegate.chat(request);
        } finally {
            control.exitModelCall();
        }
    }
}
