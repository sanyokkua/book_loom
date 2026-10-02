package ua.bookloom.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.Paused;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.api.pipeline.RecoveryWaiting;
import ua.bookloom.pipeline.run.PauseDecider;

/**
 * Waits through a pause on a provider error the run recovers from by itself, so a run left alone overnight is in the
 * morning either finished or visibly waiting with its reason: it sleeps by {@link RecoverySchedule}, probes the
 * provider at each wake and resumes as soon as a probe passes. A failed probe only extends the wait; a person's Retry
 * now, Skip segment or Resume ends it at once and restarts the wake schedule, keeping when the outage began, and the call
 * it sends again spends no segment budget; Pause holds the run for the person; Stop ends it. The outage — when it began
 * — lasts until a model call answers again. An unloaded model
 * wakes at most {@link PauseDecider#UNLOADED_MODEL_WAKES} times in one outage, after which the person loads it.
 *
 * <p>Used from the job thread only, apart from {@link #probeWith(ProviderProbe)}.
 */
@Slf4j
final class UnattendedRecovery {

    private final JobControl control;
    private final Clock clock;
    private final RecoveryTimer timer;
    private final Consumer<JobEvent> emit;
    private volatile ProviderProbe probe = ProviderProbe.ASSUME_REACHABLE;
    private @Nullable Instant downSince;
    private int wakes;
    private int cappedWakes;
    private @Nullable ErrorCode lastProbe;
    private boolean retriedByPerson;

    UnattendedRecovery(
            final JobControl control, final Clock clock, final RecoveryTimer timer, final Consumer<JobEvent> emit) {
        this.control = Objects.requireNonNull(control, "control");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timer = Objects.requireNonNull(timer, "timer");
        this.emit = Objects.requireNonNull(emit, "emit");
    }

    void probeWith(final ProviderProbe probe) {
        this.probe = Objects.requireNonNull(probe, "probe");
        log.debug("Recovery probe set");
    }

    /**
     * Takes the fact that the wait just ended was ended by the person — Retry now, Skip segment or Resume — rather
     * than by a probe that found the provider back: the call it sends again is theirs, so it spends no segment budget.
     *
     * @return {@code true} once for each such ending, {@code false} otherwise
     */
    boolean takeRetriedByPerson() {
        final boolean taken = retriedByPerson;
        retriedByPerson = false;
        return taken;
    }

    /** A model call answered: the outage, if there was one, is over. */
    void callAnswered() {
        final Instant began = downSince;
        if (began != null) {
            log.info(
                    "Provider answers again after an outage of {} min and {} wakes",
                    Duration.between(began, clock.instant()).toMinutes(),
                    wakes);
            forget();
        }
    }

    /**
     * Waits through the pause just announced until the provider answers a probe, the person acts, or the outage has
     * lasted too long, after which it waits for the person alone.
     *
     * @param paused the non-null pause on an error, already announced
     * @return how the wait ended
     */
    PauseWait await(final Paused paused) {
        final AppError error = Objects.requireNonNull(paused.error(), "a recovering pause is on an error");
        final Instant now = clock.instant();
        if (downSince == null) {
            downSince = now;
            wakes = 0;
            cappedWakes = 0;
            log.info("Provider outage began code={} segmentId={}", error.code(), paused.segmentId());
        }
        control.beginRecovery();
        try {
            return waitThrough(error, paused.progress(), Objects.requireNonNull(downSince, "downSince"));
        } finally {
            control.endRecovery();
        }
    }

    private PauseWait waitThrough(final AppError error, final JobProgress progress, final Instant since) {
        lastProbe = null;
        final int cap = PauseDecider.recovery(error.code()).wakesPerOutage();
        while (true) {
            final Instant now = clock.instant();
            final Optional<Duration> delay = RecoverySchedule.delayBefore(wakes + 1, Duration.between(since, now));
            if (delay.isEmpty() || cappedWakes >= cap) {
                logGivingUp(error, since, cap);
                return holdFor(RecoveryWaiting.Status.GAVE_UP, error, progress, since, lastProbe);
            }
            announce(error, progress, since, now.plus(delay.get()));
            final Optional<PauseWait> ended = wake(delay.get(), error, progress, since, cap);
            if (ended.isPresent()) {
                return ended.get();
            }
        }
    }

    private void logGivingUp(final AppError error, final Instant since, final int cap) {
        if (cappedWakes >= cap) {
            log.warn(
                    "No model loaded after {} wakes code={}; waiting for the person to load it and resume",
                    cappedWakes,
                    error.code());
            return;
        }
        log.warn("Provider down since {} for over {}; waiting for the person", since, RecoverySchedule.MAX_OUTAGE);
    }

    // One wait and its probe: empty to wait again, or how the pause ended.
    // A capped kind counts its wakes apart, so an outage that changes kind is capped only for what the cap is about.
    private Optional<PauseWait> wake(
            final Duration delay,
            final AppError error,
            final JobProgress progress,
            final Instant since,
            final int cap) {
        final Optional<PauseWait> settled =
                settle(control.awaitWake(timer.nanosToWait(delay)), error, progress, since, lastProbe);
        if (settled.isPresent()) {
            return settled;
        }
        wakes++;
        if (cap != PauseDecider.UNCAPPED_WAKES) {
            cappedWakes++;
        }
        lastProbe = probeOnce();
        return lastProbe == null ? resumeAfterProbe(error, progress, since) : Optional.empty();
    }

    private void announce(final AppError error, final JobProgress progress, final Instant since, final Instant at) {
        emit.accept(
                new RecoveryWaiting(RecoveryWaiting.Status.WAITING, error, wakes + 1, since, at, lastProbe, progress));
        log.info(
                "Waiting for the provider: wake {} at {} code={} lastProbe={}", wakes + 1, at, error.code(), lastProbe);
    }

    // A person may act between a good probe and the resume; the wait of zero reads what they did.
    private Optional<PauseWait> resumeAfterProbe(
            final AppError error, final JobProgress progress, final Instant since) {
        if (control.resumeAutomatically()) {
            log.info(
                    "Resumed by itself after {} min and {} wakes code={}",
                    Duration.between(since, clock.instant()).toMinutes(),
                    wakes,
                    error.code());
            return Optional.of(PauseWait.RESUMED);
        }
        return settle(control.awaitWake(0), error, progress, since, null);
    }

    private Optional<PauseWait> settle(
            final RecoveryWake wake,
            final AppError error,
            final JobProgress progress,
            final Instant since,
            @Nullable final ErrorCode probeFailure) {
        return switch (wake) {
            case DUE -> Optional.empty();
            case CANCELLED -> {
                log.info("Recovery ended: the run was stopped while waiting for the provider");
                yield Optional.of(PauseWait.CANCELLED);
            }
            case RESUMED -> {
                log.info(
                        "Recovery ended: the person asked to retry now; the wait schedule starts again, the outage"
                                + " clock does not (down since {})",
                        since);
                wakes = 0;
                cappedWakes = 0;
                retriedByPerson = true;
                yield Optional.of(PauseWait.RESUMED);
            }
            case HELD -> {
                log.info("Recovery held: the person paused the run while it waited for the provider");
                yield Optional.of(holdFor(RecoveryWaiting.Status.HELD, error, progress, since, probeFailure));
            }
        };
    }

    private PauseWait holdFor(
            final RecoveryWaiting.Status status,
            final AppError error,
            final JobProgress progress,
            final Instant since,
            @Nullable final ErrorCode probeFailure) {
        emit.accept(new RecoveryWaiting(status, error, wakes, since, null, probeFailure, progress));
        control.endRecovery();
        final PauseWait wait = control.awaitPause();
        if (wait == PauseWait.RESUMED) {
            forget();
        }
        return wait;
    }

    // A probe that throws is a failed probe, never a reason to leave the wait.
    private @Nullable ErrorCode probeOnce() {
        try {
            final Result<Duration> probed = Objects.requireNonNull(probe.probe(), "probe result");
            if (probed.isOk()) {
                log.info("Provider probe at wake {} passed in {} ms", wakes, millisOf(probed));
                return null;
            }
            final AppError failure = Objects.requireNonNull(probed.error(), "probe error");
            log.info("Provider probe at wake {} failed code={}", wakes, failure.code());
            return failure.code();
        } catch (RuntimeException thrown) {
            log.warn("Provider probe at wake {} threw; counted as a failed probe", wakes, thrown);
            return ErrorCode.internal;
        }
    }

    private static long millisOf(final Result<Duration> probed) {
        return Objects.requireNonNull(probed.data(), "probe time").toMillis();
    }

    private void forget() {
        downSince = null;
        wakes = 0;
        cappedWakes = 0;
    }
}
