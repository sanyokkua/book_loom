package ua.bookloom.ui.theme;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.Theme;

/**
 * Holds the theme mode and applies the matching value block to the attached scene by toggling the dark style class on
 * its root — never by writing an inline style.
 *
 * <p>Every method must be called on the JavaFX Application Thread. The provider is consulted only when a scene is
 * attached and when the mode is set to {@link ThemeMode#SYSTEM}, so an explicit choice is never overridden by a later
 * change in the operating system.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ThemeController {

    private final ColorSchemeProvider provider;
    private final ReadOnlyObjectWrapper<ThemeBlock> activeBlock = new ReadOnlyObjectWrapper<>(ThemeBlock.LIGHT);
    private final ReadOnlyObjectWrapper<ThemeMode> mode = new ReadOnlyObjectWrapper<>(ThemeMode.SYSTEM);
    private @Nullable Scene scene;

    /**
     * Adds the theme stylesheet to the scene if it is not already listed and applies the block for the current mode.
     *
     * @param target the scene to theme; attaching another scene later moves the controller to it
     */
    public void attach(final Scene target) {
        Objects.requireNonNull(target, "target");
        log.debug("attaching scene, mode {}", mode.get());
        final ObservableList<String> stylesheets = target.getStylesheets();
        final String stylesheet = Theme.stylesheet();
        if (!stylesheets.contains(stylesheet)) {
            stylesheets.add(stylesheet);
        }
        scene = target;
        apply(resolveBlock());
    }

    /**
     * Chooses what decides the block and applies it.
     *
     * @param newMode the mode to hold from now on; {@link ThemeMode#SYSTEM} re-reads the operating system
     */
    public void setMode(final ThemeMode newMode) {
        Objects.requireNonNull(newMode, "newMode");
        log.debug("theme mode {} -> {}", mode.get(), newMode);
        mode.set(newMode);
        apply(resolveBlock());
    }

    /** Flips between light and dark and holds the result as an explicit mode, ending any following of the system. */
    public void toggle() {
        final ThemeMode next = activeBlock.get() == ThemeBlock.DARK ? ThemeMode.LIGHT : ThemeMode.DARK;
        log.debug("toggling theme from {} to mode {}", activeBlock.get(), next);
        setMode(next);
    }

    /**
     * The mode currently held.
     *
     * @return the mode; {@link ThemeMode#SYSTEM} until a person chooses otherwise
     */
    public ThemeMode mode() {
        return mode.get();
    }

    /**
     * Observes the mode held, so a control that shows it follows a change made from another place, such as the
     * title-bar toggle.
     *
     * @return a read-only property that changes only through this controller
     */
    public ReadOnlyObjectProperty<ThemeMode> modeProperty() {
        return mode.getReadOnlyProperty();
    }

    /**
     * The block currently in force.
     *
     * @return the block last applied; light before any scene is attached
     */
    public ThemeBlock activeBlock() {
        return activeBlock.get();
    }

    /**
     * Observes the block in force, for a control that shows it.
     *
     * @return a read-only property that changes only through this controller
     */
    public ReadOnlyObjectProperty<ThemeBlock> activeBlockProperty() {
        return activeBlock.getReadOnlyProperty();
    }

    private ThemeBlock resolveBlock() {
        return switch (mode.get()) {
            case LIGHT -> ThemeBlock.LIGHT;
            case DARK -> ThemeBlock.DARK;
            case SYSTEM -> {
                final ThemeBlock reported = provider.current().orElse(ThemeBlock.LIGHT);
                log.debug("mode SYSTEM chose block {} from the operating system", reported);
                yield reported;
            }
        };
    }

    private void apply(final ThemeBlock block) {
        final Scene current = scene;
        if (current != null) {
            final List<String> styleClass = current.getRoot().getStyleClass();
            styleClass.remove(Theme.DARK_STYLE_CLASS);
            if (block == ThemeBlock.DARK) {
                styleClass.add(Theme.DARK_STYLE_CLASS);
            }
        }
        activeBlock.set(block);
        log.debug("applied theme block {}", block);
    }
}
