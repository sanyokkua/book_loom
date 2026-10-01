package ua.bookloom.llm.http;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Receives one streamed reply line by line and hands it to the calling thread, which waits for each line no longer
 * than the idle gap and for the whole reply no longer than the call's limit. The JDK client's own request timeout
 * stops at the response headers, so without this a reply that stops sending halfway would hold the call forever.
 *
 * <p>The JDK client delivers on its own threads; the calling thread is the only reader, so an interrupt of the caller
 * (a pause or a stop) ends the wait at once.
 */
@Slf4j
final class StreamedLines implements Flow.Subscriber<String> {

    private final BlockingQueue<Signal> signals = new LinkedBlockingQueue<>();
    private final AtomicReference<Flow.@Nullable Subscription> subscription = new AtomicReference<>();

    @Override
    public void onSubscribe(Flow.Subscription accepted) {
        subscription.set(accepted);
        accepted.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(String line) {
        signals.add(new Signal.Line(line));
    }

    @Override
    public void onError(Throwable failure) {
        signals.add(new Signal.Failed(failure));
    }

    @Override
    public void onComplete() {
        signals.add(new Signal.Done());
    }

    /** Reports a failure of the exchange itself, which may come before any body exists to subscribe to. */
    void exchangeFailed(Throwable failure) {
        signals.add(new Signal.Failed(unwrapped(failure)));
    }

    /** Stops the delivery, which closes the connection so the provider stops generating. */
    void cancel() {
        final Flow.Subscription active = subscription.get();
        if (active != null) {
            active.cancel();
        }
    }

    /**
     * Waits for the whole reply.
     *
     * @param idle the longest gap allowed between two lines, and before the first
     * @param limit the longest the whole reply may take, counted from {@code startedNanos}
     * @param startedNanos when the request was sent, on {@code nanoTime}'s scale
     * @param nanoTime the clock the waits are measured with
     * @param maxLines the most lines read before the reply is cut as a runaway; {@link Long#MAX_VALUE} for no bound
     * @return the lines joined by newlines; the lines read so far when {@code maxLines} was reached; or the failure
     *     that ended the wait: an {@link HttpTimeoutException} for a stall or an overrun, an
     *     {@link InterruptedException} for an interrupt, else the exchange's own failure
     */
    Outcome collect(Duration idle, Duration limit, long startedNanos, LongSupplier nanoTime, long maxLines) {
        final StringBuilder body = new StringBuilder();
        long lines = 0;
        while (true) {
            final long remaining = limit.toNanos() - (nanoTime.getAsLong() - startedNanos);
            final long wait = Math.min(idle.toNanos(), remaining);
            final Signal signal = next(wait);
            switch (signal) {
                case Signal.Line line -> {
                    body.append(line.text()).append('\n');
                    lines++;
                    if (lines >= maxLines) {
                        log.warn("Streamed reply cut as a runaway lines={} maxLines={}", lines, maxLines);
                        return new Outcome.Cut(body.toString());
                    }
                }
                case Signal.Done done -> {
                    log.debug("Streamed reply complete lines={} length={}", lines, body.length());
                    return new Outcome.Collected(body.toString());
                }
                case Signal.Failed failed -> {
                    log.debug("Streamed reply ended early lines={} failureType={}", lines, typeOf(failed.failure()));
                    return new Outcome.Failed(failed.failure(), wait < idle.toNanos() ? limit : idle);
                }
            }
        }
    }

    private Signal next(long waitNanos) {
        if (waitNanos <= 0) {
            return new Signal.Failed(new HttpTimeoutException("the streamed reply ran past its time limit"));
        }
        try {
            final Signal signal = signals.poll(waitNanos, TimeUnit.NANOSECONDS);
            return signal == null
                    ? new Signal.Failed(new HttpTimeoutException("the streamed reply stopped sending"))
                    : signal;
        } catch (InterruptedException interrupted) {
            return new Signal.Failed(interrupted);
        }
    }

    private static Throwable unwrapped(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = Objects.requireNonNull(current.getCause());
        }
        return current;
    }

    private static String typeOf(Throwable failure) {
        return failure.getClass().getSimpleName();
    }

    /** How waiting for a streamed reply ended. */
    sealed interface Outcome {

        /**
         * Every line arrived.
         *
         * @param body the lines, each followed by a newline
         */
        record Collected(String body) implements Outcome {}

        /**
         * The reply ran past the most lines it may send and was cut there.
         *
         * @param body the lines read, each followed by a newline
         */
        record Cut(String body) implements Outcome {}

        /**
         * The wait ended without the whole reply.
         *
         * @param failure what ended it
         * @param timeout the bound a timeout broke: the idle gap, or the whole limit when that was nearer
         */
        record Failed(Throwable failure, Duration timeout) implements Outcome {}
    }

    private sealed interface Signal {

        record Line(String text) implements Signal {}

        record Done() implements Signal {}

        record Failed(Throwable failure) implements Signal {}
    }
}
