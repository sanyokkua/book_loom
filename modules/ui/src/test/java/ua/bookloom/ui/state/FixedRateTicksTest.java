package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The production cadence: a repeating tick on a daemon thread of its own that a stopper ends. */
class FixedRateTicksTest {

    private static final long WAIT_SECONDS = 10;
    private static final long PERIOD_FLOOR_MILLIS = 95;

    // IF the period drifted from 100 ms, THEN the window would publish far more or far less often than specified.
    @Test
    void periodMillis_isOneHundred() {
        assertThat(FixedRateTicks.PERIOD_MILLIS).isEqualTo(100);
    }

    // IF the ticker were a non-daemon thread, THEN a run left going would keep the JVM alive after the last window
    // closed; IF the first tick came sooner than the period, THEN the cadence would exceed its budget.
    @Test
    void start_tick_repeatsOnANamedDaemonThreadNoSoonerThanThePeriod() throws Exception {
        final CountDownLatch twoTicks = new CountDownLatch(2);
        final List<Long> tickTimes = new CopyOnWriteArrayList<>();
        final AtomicReference<Thread> tickThread = new AtomicReference<>();
        final long begun = System.nanoTime();

        final Runnable stop = new FixedRateTicks().start(() -> {
            tickThread.set(Thread.currentThread());
            tickTimes.add(System.nanoTime());
            twoTicks.countDown();
        });
        final boolean repeated = twoTicks.await(WAIT_SECONDS, TimeUnit.SECONDS);
        stop.run();

        final Thread thread = Objects.requireNonNull(tickThread.get());
        assertThat(repeated).isTrue();
        assertThat(thread.isDaemon()).isTrue();
        assertThat(thread.getName()).isEqualTo("bookloom-run-ticker");
        assertThat(TimeUnit.NANOSECONDS.toMillis(tickTimes.get(0) - begun)).isGreaterThanOrEqualTo(PERIOD_FLOOR_MILLIS);
    }

    // IF the stopper left the scheduler alive, THEN every finished run would leak a timer thread.
    @Test
    void start_stopper_endsTheCadenceThread() throws Exception {
        final CountDownLatch oneTick = new CountDownLatch(1);
        final AtomicReference<Thread> tickThread = new AtomicReference<>();
        final Runnable stop = new FixedRateTicks().start(() -> {
            tickThread.set(Thread.currentThread());
            oneTick.countDown();
        });
        assertThat(oneTick.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        final Thread thread = Objects.requireNonNull(tickThread.get());
        stop.run();
        thread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertThat(thread.isAlive()).isFalse();
    }
}
