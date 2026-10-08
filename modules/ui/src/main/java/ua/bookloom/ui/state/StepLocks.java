package ua.bookloom.ui.state;

import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Which workflow screens are closed and why. An interface, so a screen test that is not about the gating can open
 * every step without first putting a book and languages in place.
 */
public interface StepLocks {

    /**
     * The reason a step is closed, observable.
     *
     * @param step the screen asked about
     * @return a read-only property holding the catalogue key of the reason, or {@code null} while the step is open
     */
    ReadOnlyObjectProperty<@Nullable MessageKey> lock(ViewNames step);

    /**
     * The reason a step is closed right now.
     *
     * @param step the screen asked about
     * @return the key of the reason, or empty if the step can be opened
     */
    default Optional<MessageKey> lockReason(final ViewNames step) {
        return Optional.ofNullable(lock(step).get());
    }
}
