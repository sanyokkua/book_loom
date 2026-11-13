package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.CurrentProject;

/**
 * What every workflow screen is built on.
 *
 * @param project the holder of the open book
 * @param messages the catalogue the built parts are worded from
 * @param navigator where Back, Continue and the route from the no-book state lead
 */
public record ScreenContext(CurrentProject project, Messages messages, Navigator navigator) {

    /** Rejects a missing part. */
    @Inject
    public ScreenContext {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(navigator, "navigator");
    }
}
