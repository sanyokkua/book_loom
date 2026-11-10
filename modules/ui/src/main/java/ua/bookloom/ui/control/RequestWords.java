package ua.bookloom.ui.control;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CallTimings;
import ua.bookloom.ui.state.StepEntry;

/** The one line a request of a stage reads as in the list: its number, where it stands, its time and its speed. */
@RequiredArgsConstructor
final class RequestWords {

    static final String NONE = "none";

    private final Messages messages;

    /**
     * Words one request.
     *
     * @param number the request's place in its stage, from one
     * @param request the request's step row
     * @param call the call it made as last seen, or {@code null} when none was seen
     * @param now the moment a waiting call's clock reads
     * @return the line
     */
    String line(final int number, final StepEntry request, final @Nullable CallSnapshot call, final Instant now) {
        return line(number, NONE, request, call, now);
    }

    /**
     * Words one request of a stage whose request count is known.
     *
     * @param number the request's place in its stage, from one
     * @param total how many requests the stage sent in all, or {@link #NONE} while the stage still runs
     * @param request the request's step row
     * @param call the call it made as last seen, or {@code null} when none was seen
     * @param now the moment a waiting call's clock reads
     * @return the line
     */
    String line(
            final int number,
            final String total,
            final StepEntry request,
            final @Nullable CallSnapshot call,
            final Instant now) {
        if (call == null) {
            return messages.get(endedWith(request.result()), number, NONE, NONE, total);
        }
        final String attempt = call.maxAttempts() > 1 ? call.attempt() + "/" + call.maxAttempts() : NONE;
        return switch (call.state()) {
            case WAITING ->
                messages.get(
                        MessageKey.PROGRESS_REQUEST_WAITING,
                        number,
                        clock(Duration.between(call.startedAt(), now)),
                        attempt,
                        total);
            case ANSWERED ->
                messages.get(
                        MessageKey.PROGRESS_REQUEST_ANSWERED,
                        number,
                        clock(call.elapsed()),
                        speed(call.usage()),
                        total);
            case FAILED ->
                messages.get(MessageKey.PROGRESS_REQUEST_FAILED, number, clock(call.elapsed()), attempt, total);
            case CANCELLED -> messages.get(MessageKey.PROGRESS_REQUEST_STOPPED, number, NONE, NONE, total);
        };
    }

    // A request seen but not yet matched to a call is being sent; one that ended before it was seen says how it ended.
    private static MessageKey endedWith(final @Nullable StepResult result) {
        if (result == null) {
            return MessageKey.PROGRESS_REQUEST_SENDING;
        }
        return switch (result) {
            case FAILED -> MessageKey.PROGRESS_REQUEST_LOST;
            case STOPPED -> MessageKey.PROGRESS_REQUEST_STOPPED;
            case DONE, NO_CALL -> MessageKey.PROGRESS_REQUEST_DONE;
        };
    }

    static String clock(final Duration took) {
        return DurationText.clock((int) Math.max(0, took.toSeconds()));
    }

    /**
     * The tokens written a second.
     *
     * @param usage the reply's usage, or {@code null}
     * @return one decimal, or {@link #NONE} when it cannot be told
     */
    static String speed(final @Nullable TokenUsage usage) {
        final @Nullable Double rate = CallTimings.tokensPerSecond(usage);
        return rate == null ? NONE : String.format(Locale.ROOT, "%.1f", rate);
    }
}
