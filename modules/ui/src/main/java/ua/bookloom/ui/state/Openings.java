package ua.bookloom.ui.state;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Numbers the openings of a screen, so that an answer asked for by an earlier opening is dropped instead of shown over
 * a later one. FX thread only.
 */
@Slf4j
final class Openings {

    private long generation;

    /** Starts a new opening; every answer asked for before it is now stale. */
    long begin() {
        return ++generation;
    }

    /** {@return a wrapper that runs what it is given only if no later opening began since it was made} */
    Consumer<Runnable> whileCurrent() {
        final long ticket = generation;
        return publish -> ifCurrent(ticket, publish);
    }

    void ifCurrent(final long ticket, final Runnable publish) {
        if (ticket == generation) {
            publish.run();
        } else {
            log.debug("an answer of opening {} is dropped: opening {} superseded it", ticket, generation);
        }
    }
}
