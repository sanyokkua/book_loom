package ua.bookloom.ui;

import com.google.inject.ImplementedBy;

/**
 * Keeps the computer from sleeping while a translation is at work, so a run left overnight is not frozen by an idle
 * sleep. Both calls are idempotent and never fail the run: a computer that cannot be kept awake just sleeps as before.
 * The composition root binds the operating system's own mechanism; without it nothing is held.
 */
@ImplementedBy(KeepAwake.Off.class)
public interface KeepAwake {

    /** Starts holding the computer awake; a second start while held changes nothing. */
    void start();

    /** Lets the computer sleep again; a stop while not held changes nothing. */
    void stop();

    /** Holds nothing: the binding for a test injector and for a platform with no mechanism. */
    final class Off implements KeepAwake {

        @Override
        public void start() {
            // Nothing to hold.
        }

        @Override
        public void stop() {
            // Nothing held.
        }
    }
}
