package ua.bookloom.ui.state;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A {@link Clock} the test moves by hand, so a ten-second wait is a line of code and not ten seconds of sleeping. */
public final class MutableClock extends Clock {

    // Moved on the test thread and read on the FX thread.
    private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

    /** Moves the clock on by {@code by}. */
    public void advance(final Duration by) {
        now = now.plus(by);
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
