package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jspecify.annotations.Nullable;

/**
 * The results of a pass that ended in the background, from the moment a message offers them to the moment the person
 * opens them. The holder outlives the Names &amp; style screen, so a request made while another screen shows is still
 * there when this one is built. FX thread only.
 */
public final class ResultsReview {

    private final ObjectProperty<@Nullable ChangeResults> requested = new SimpleObjectProperty<>();
    private @Nullable ChangeResults announced;

    /**
     * The results the person asked to open.
     *
     * @return a property holding them until the screen takes them, or {@code null}
     */
    public ObjectProperty<@Nullable ChangeResults> requested() {
        return requested;
    }

    /**
     * Notes that the person was told of these results, so the message is raised once.
     *
     * @param results the results of a pass that ended in the background
     * @return {@code true} the first time, {@code false} when they were announced before
     */
    public boolean announce(final ChangeResults results) {
        Objects.requireNonNull(results, "results");
        if (announced == results) {
            return false;
        }
        announced = results;
        return true;
    }

    /**
     * Hands over the results the person asked to open.
     *
     * @return the results, which are no longer requested, or {@code null} when none were
     */
    public @Nullable ChangeResults take() {
        final ChangeResults asked = requested.get();
        requested.set(null);
        return asked;
    }
}
