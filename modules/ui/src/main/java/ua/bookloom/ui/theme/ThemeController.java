package ua.bookloom.ui.theme;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.Theme;

/**
 * Holds the theme mode and applies the matching value block to the attached scene by toggling the dark style class on
 * its root — never by writing an inline style.
 *
 * <p>Every method but {@link #seed} must be called on the JavaFX Application Thread; the mode is written to the store
 * in the background whenever a person chooses one. The provider is consulted only when a scene is
 * attached and when the mode is set to {@link ThemeMode#SYSTEM}, so an explicit choice is never overridden by a later
 * change in the operating system.
 */
@Slf4j
@Singleton
public final class ThemeController {

    private final ColorSchemeProvider provider;
    private final SettingsRepository settings;
    private final Executor background;
    private final Executor fx;
    private final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(false);
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);
    private volatile @Nullable ThemeMode seeded;
    private volatile boolean chosen;
    private volatile boolean attached;
    private final ReadOnlyObjectWrapper<ThemeBlock> activeBlock = new ReadOnlyObjectWrapper<>(ThemeBlock.LIGHT);
    private final ReadOnlyObjectWrapper<ThemeMode> mode = new ReadOnlyObjectWrapper<>(ThemeMode.SYSTEM);
    private @Nullable Scene scene;

    /**
     * Creates the controller over the real store and the pool its writes go to.
     *
     * @param provider the operating system's colour scheme
     * @param settings where the chosen mode is kept
     * @param background where the store is written, never the FX thread
     */
    @Inject
    public ThemeController(
            final ColorSchemeProvider provider,
            final SettingsRepository settings,
            final @BackgroundExecutor ExecutorService background) {
        this(provider, settings, background, Platform::runLater);
    }

    ThemeController(final ColorSchemeProvider provider, final SettingsRepository settings, final Executor background) {
        this(provider, settings, background, Runnable::run);
    }

    private ThemeController(
            final ColorSchemeProvider provider,
            final SettingsRepository settings,
            final Executor background,
            final Executor fx) {
        this.fx = Objects.requireNonNull(fx, "fx");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.background = Objects.requireNonNull(background, "background");
    }

    /**
     * Records the stored mode before the scene exists; any thread. It is applied when the scene is attached, without
     * a write, and ignored once a scene is attached or a person has chosen. It must therefore run before
     * {@code start()} builds and attaches the scene, which is why startup reads the store in {@code init()}.
     *
     * @param stored the mode the store held
     */
    public void seed(final ThemeMode stored) {
        Objects.requireNonNull(stored, "stored");
        if (attached || chosen) {
            log.debug("theme seed {} ignored: attached={} chosen={}", stored, attached, chosen);
            return;
        }
        seeded = stored;
        log.debug("theme seeded {}", stored);
    }

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
        final ThemeMode seed = seeded;
        if (!attached && !chosen && seed != null) {
            mode.set(seed);
        }
        attached = true;
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
        chosen = true;
        mode.set(newMode);
        apply(resolveBlock());
        store(newMode);
    }

    // Each write waits for the one before it, so a quick second choice is never overwritten by the first.
    private void store(final ThemeMode newMode) {
        lastWrite = lastWrite.handle((done, failure) -> (Void) null).thenRunAsync(() -> write(newMode), background);
    }

    private void write(final ThemeMode newMode) {
        boolean failed;
        try {
            failed = settings.put(AppSetting.THEME, ThemeModes.toSetting(newMode))
                    .isErr();
        } catch (RuntimeException e) {
            log.error("storing the theme failed", e);
            failed = true;
        }
        if (failed) {
            log.warn("theme {} could not be stored; it holds until the app closes", newMode);
        }
        final boolean shown = failed;
        fx.execute(() -> saveFailed.set(shown));
    }

    /**
     * Whether the last choice could not be stored.
     *
     * @return a read-only property; the mode still holds until the app closes
     */
    public ReadOnlyBooleanProperty saveFailedProperty() {
        return saveFailed.getReadOnlyProperty();
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
            mark(current, block);
        }
        activeBlock.set(block);
        log.debug("applied theme block {}", block);
    }

    private static void mark(final Scene target, final ThemeBlock block) {
        final List<String> styleClass = target.getRoot().getStyleClass();
        styleClass.remove(Theme.DARK_STYLE_CLASS);
        if (block == ThemeBlock.DARK) {
            styleClass.add(Theme.DARK_STYLE_CLASS);
        }
    }
}
