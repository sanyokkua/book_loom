package ua.bookloom.ui.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/** Animation pulses driven by hand for the scroll tests: {@link #tick} calls whatever ticker is running. */
final class ManualClock implements PulseClock {

    private final List<LongConsumer> running = new ArrayList<>();

    @Override
    public Ticker create(final LongConsumer onPulse) {
        return new Ticker() {
            @Override
            public void start() {
                running.add(onPulse);
            }

            @Override
            public void stop() {
                running.remove(onPulse);
            }
        };
    }

    void tick(final long now) {
        List.copyOf(running).forEach(pulse -> pulse.accept(now));
    }
}
