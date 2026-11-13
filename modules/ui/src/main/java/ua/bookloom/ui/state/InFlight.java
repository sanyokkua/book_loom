package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Lets one piece of background work per key run at a time: asking for the same key again while it runs joins the work
 * instead of starting it twice. FX thread only.
 */
@Slf4j
final class InFlight {

    private @Nullable String running;

    /**
     * Claims the key.
     *
     * @param key what the work is for
     * @return {@code true} if the work should start, {@code false} if it is already running for this key
     */
    boolean begin(final String key) {
        Objects.requireNonNull(key, "key");
        if (key.equals(running)) {
            log.debug("{} is already running: joining it", key);
            return false;
        }
        running = key;
        return true;
    }

    /**
     * Releases the key once its work has answered; a later claim for another key stays in force.
     *
     * @param key what the work was for
     */
    void end(final String key) {
        if (key.equals(running)) {
            running = null;
        }
    }
}
