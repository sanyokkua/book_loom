package ua.bookloom.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that stands still until a test moves it, safe to read from the job thread and the watchdog's at once. */
final class ScriptedClock extends Clock {

    static final Instant START = Instant.parse("2026-10-02T02:14:00Z");

    private volatile Instant now = START;

    void advance(final Duration by) {
        // Moved only from one thread at a time: the job thread's timer, or the test before the run.
        now = now.plus(by);
    }

    Duration sinceStart() {
        return Duration.between(START, now);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(final ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
