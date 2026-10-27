package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Whether model work is visible in the window now. The work layer writes it and the dialog host reads it, so a dialog
 * nobody asked for waits for the window to be free without the two layers holding each other.
 *
 * <p>FX Application Thread only, like every property the scene reads.
 */
@Singleton
public final class WorkVisibility {

    private final BooleanProperty visible = new SimpleBooleanProperty(false);

    /** Creates the holder with no work visible. */
    @Inject
    public WorkVisibility() {
        // nothing to prepare: the property starts false
    }

    /**
     * The observable flag.
     *
     * @return {@code true} while a progress dialog of model work is showing
     */
    public BooleanProperty visibleProperty() {
        return visible;
    }

    /**
     * Whether model work is visible now.
     *
     * @return {@code true} while a progress dialog of model work is showing
     */
    public boolean isVisible() {
        return visible.get();
    }
}
