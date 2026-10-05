package ua.bookloom.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.pipeline.Resumed;
import ua.bookloom.api.pipeline.SegmentDecided;

/**
 * Ends a model call the provider client failed to end: one outstanding half again as long as its own timeout, or a
 * running run that decided no segment for {@link #NO_DECISION_LIMIT}. It interrupts the call through the job's
 * control, as a pause does, so the client gives it up and the inference gate is released; the call's answer is then
 * read as a {@code timeout} and goes through the run's usual retry and failure budget.
 *
 * <p>Events arrive on the job thread and {@link #check()} runs on the watchdog's own ticker, so the state between them
 * is held in atomics.
 */
@Slf4j
final class StallWatchdog {

    /** How often the watchdog looks. */
    static final Duration TICK = Duration.ofSeconds(5);

    /** A call outstanding this many times its own timeout has outlived anything the client should allow. */
    static final double CEILING_FACTOR = 1.5;

    /** The ceiling of a call whose model announces no timeout of its own. */
    static final Duration UNTIMED_CEILING = Duration.ofMinutes(15);

    /** A running run that decides nothing for this long is stuck somewhere, whatever its calls report. */
    static final Duration NO_DECISION_LIMIT = Duration.ofMinutes(20);

    /** The attempt being waited on, and when the watchdog gives up on it. */
    private record Outstanding(CallKind kind, List<String> segmentIds, Instant since, Duration ceiling) {}

    /** What the watchdog last ended a call for, until the call's answer takes it. */
    record Stall(CallKind kind, Duration elapsed, Duration ceiling) {}

    private final JobControl control;
    private final Clock clock;
    private final AtomicReference<@Nullable Outstanding> outstanding = new AtomicReference<>();
    private final AtomicReference<Instant> lastProgress;
    private final AtomicReference<@Nullable Stall> fired = new AtomicReference<>();

    StallWatchdog(final JobControl control, final Clock clock) {
        this.control = Objects.requireNonNull(control, "control");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastProgress = new AtomicReference<>(clock.instant());
    }

    /**
     * Starts looking on {@code ticks}; the clock of decisions starts now.
     *
     * @return what stops the watchdog, called when the run ends
     */
    Runnable start(final RunTicks ticks) {
        lastProgress.set(clock.instant());
        log.debug("Stall watchdog started tick={} noDecisionLimit={}", TICK, NO_DECISION_LIMIT);
        final Runnable stop = Objects.requireNonNull(ticks, "ticks").start(TICK, this::checkSafely);
        return () -> {
            stop.run();
            log.debug("Stall watchdog stopped");
        };
    }

    /** Follows the run's own events: a call's attempts and ends, each decision and each resume. */
    void onEvent(final JobEvent event) {
        final Instant now = clock.instant();
        switch (event) {
            case ModelCallStarted started ->
                outstanding.set(new Outstanding(started.kind(), started.segmentIds(), now, ceilingOf(started)));
            case ModelCallFinished finished -> outstanding.set(null);
            case SegmentDecided decided -> lastProgress.set(now);
            case Resumed resumed -> lastProgress.set(now);
            default -> {
                // Nothing else says whether the run moves.
            }
        }
    }

    /**
     * Takes what the watchdog ended the call that just returned for.
     *
     * @return the stall if the watchdog interrupted that call, or {@code null} if it did not
     */
    @Nullable
    Stall takeStall() {
        return fired.getAndSet(null);
    }

    /** Forgets a stall left from an earlier call, as a new call starts. */
    void clearStall() {
        fired.set(null);
    }

    void check() {
        if (control.state() != JobState.RUNNING) {
            return;
        }
        final Instant now = clock.instant();
        final Outstanding call = outstanding.get();
        if (call != null && Duration.between(call.since(), now).compareTo(call.ceiling()) > 0) {
            outstanding.compareAndSet(call, null);
            fire(call.kind(), call.segmentIds(), Duration.between(call.since(), now), call.ceiling());
            return;
        }
        final Instant progressed = lastProgress.get();
        final Duration idle = Duration.between(progressed, now);
        if (idle.compareTo(NO_DECISION_LIMIT) > 0 && lastProgress.compareAndSet(progressed, now)) {
            log.warn("No segment decided for {} min while running; ending the call in flight", idle.toMinutes());
            if (call != null) {
                outstanding.compareAndSet(call, null);
                fire(call.kind(), call.segmentIds(), Duration.between(call.since(), now), NO_DECISION_LIMIT);
            }
        }
    }

    // The ticker stops calling a tick that throws, so nothing may escape it.
    private void checkSafely() {
        try {
            check();
        } catch (RuntimeException failure) {
            log.error("Stall watchdog check failed; it keeps watching", failure);
        }
    }

    // A batch call is about several segments and has no single one, so the line names every id it carries.
    private void fire(
            final CallKind kind, final List<String> segmentIds, final Duration elapsed, final Duration ceiling) {
        final Stall stall = new Stall(kind, elapsed, ceiling);
        fired.set(stall);
        if (control.interruptStalledCall()) {
            log.warn(
                    "Stall watchdog ended a {} call segmentIds={} after {} s (ceiling {} s); it is retried as a timeout",
                    kind,
                    segmentIds,
                    elapsed.toSeconds(),
                    ceiling.toSeconds());
        } else {
            fired.compareAndSet(stall, null);
            log.warn("Stall watchdog found no call in flight to end kind={} segmentIds={}", kind, segmentIds);
        }
    }

    private static Duration ceilingOf(final ModelCallStarted started) {
        final Duration timeout = started.timeout();
        return timeout == null ? UNTIMED_CEILING : Duration.ofMillis(Math.round(timeout.toMillis() * CEILING_FACTOR));
    }
}
