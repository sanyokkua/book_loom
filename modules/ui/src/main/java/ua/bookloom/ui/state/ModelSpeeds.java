package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells the screens that show a time estimate that a stored speed changed, so they read it again. A singleton: the
 * recorder bumps it and the estimates listen. FX thread only.
 */
@Slf4j
@Singleton
public final class ModelSpeeds {

    private final ReadOnlyLongWrapper revision = new ReadOnlyLongWrapper(0);

    /** {@return a count that grows each time a stored speed changed} */
    public ReadOnlyLongProperty revision() {
        return revision.getReadOnlyProperty();
    }

    /** Notes that a stored speed changed. */
    void bump() {
        revision.set(revision.get() + 1);
        log.debug("model speeds changed, revision {}", revision.get());
    }
}
