package ua.bookloom.pipeline;

import java.time.Clock;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttemptListener;
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
 *
 * <p>The run's {@link StallWatchdog} interrupts a call the same way; its cancelled answer is turned into a
 * {@link ErrorCode#timeout} here, so the run retries it and spends its failure budget instead of stopping.
 */
@Slf4j
final class CancellableChatModel implements ChatModel {

    private final ChatModel delegate;
    private final JobControl control;
    private final Runnable onCallEntered;
    private final StallWatchdog stalls;

    CancellableChatModel(final ChatModel delegate, final JobControl control, final Runnable onCallEntered) {
        this(delegate, control, onCallEntered, new StallWatchdog(control, Clock.systemUTC()));
    }

    CancellableChatModel(
            final ChatModel delegate,
            final JobControl control,
            final Runnable onCallEntered,
            final StallWatchdog stalls) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.control = Objects.requireNonNull(control, "control");
        this.onCallEntered = Objects.requireNonNull(onCallEntered, "onCallEntered");
        this.stalls = Objects.requireNonNull(stalls, "stalls");
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        return chat(request, CallAttemptListener.NONE);
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request, final CallAttemptListener attempts) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attempts, "attempts");
        if (!control.enterModelCall()) {
            log.debug("Refused model call because a stop or a pause is already requested state={}", control.state());
            return Result.err(AppError.of(
                    ErrorCode.cancelled,
                    "Model call cancelled",
                    "A stop or a pause was requested, so no request was sent to the provider."));
        }
        try {
            stalls.clearStall();
            onCallEntered.run();
            return stalledAsTimeout(delegate.chat(request, attempts));
        } finally {
            control.exitModelCall();
        }
    }

    private Result<ChatResponse> stalledAsTimeout(final Result<ChatResponse> result) {
        final StallWatchdog.Stall stall = stalls.takeStall();
        final AppError error = result.error();
        if (stall == null || error == null || error.code() != ErrorCode.cancelled) {
            return result;
        }
        log.debug("A call the stall watchdog ended answered cancelled; answering timeout kind={}", stall.kind());
        return Result.err(AppError.of(
                ErrorCode.timeout,
                "Model call stalled",
                "The model call ran far past its time limit and was stopped; the run tries it again.",
                "timeout=" + stall.ceiling().toSeconds() + "s",
                null));
    }
}
