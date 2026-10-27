package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * The person's "keep the computer awake" choice, read by whoever holds the computer awake. Starts on, like the stored
 * default; startup seeds the stored value from any thread, and only the FX thread changes it afterwards.
 */
@Slf4j
@Singleton
public final class KeepAwakeGate {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean on = true;

    /**
     * Sets the stored value at startup without telling anyone; any thread.
     *
     * @param stored the stored choice
     */
    public void seed(final boolean stored) {
        log.debug("keep-awake gate seeded on={}", stored);
        on = stored;
    }

    /**
     * Changes the choice and tells the listeners when it changed; FX thread only.
     *
     * @param value the new choice
     */
    public void set(final boolean value) {
        final boolean changed = on != value;
        on = value;
        log.debug("keep-awake gate set on={} changed={}", value, changed);
        if (changed) {
            listeners.forEach(Runnable::run);
        }
    }

    /** {@return whether the computer may be held awake} */
    public boolean isOn() {
        return on;
    }

    /**
     * Registers a callback run on the FX thread after each change made by {@link #set(boolean)}.
     *
     * @param listener the non-null callback
     */
    public void onChange(final Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
