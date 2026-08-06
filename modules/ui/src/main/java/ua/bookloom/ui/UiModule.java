package ua.bookloom.ui;

import com.google.inject.AbstractModule;

/**
 * Guice bindings owned by {@code :ui} — the navigation host, the singleton observable state mirror, and the FXML
 * controller factory.
 *
 * <p>Scaffolding: the bindings arrive with the app-shell changes.
 */
public final class UiModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public UiModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        // No bindings yet.
    }
}
