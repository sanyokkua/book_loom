package ua.bookloom.app;

import com.google.inject.AbstractModule;
import ua.bookloom.ui.KeepAwake;

/**
 * Bindings only the desktop window needs: the operating system's keep-awake, so a run left overnight is not frozen by
 * an idle sleep. A test injector leaves it out and keeps nothing awake.
 */
public final class DesktopModule extends AbstractModule {

    /** Installed by the application next to the shared graph. */
    public DesktopModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(KeepAwake.class).to(OsKeepAwake.class);
    }
}
