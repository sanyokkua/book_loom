package ua.bookloom.app;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import ua.bookloom.api.AppRestart;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.MemorySource;

/**
 * Bindings only the desktop window needs: the operating system's keep-awake, so a run left overnight is not frozen by
 * an idle sleep, the computer's memory for the model advice, and the restart that starts the window again. A test injector leaves it out and keeps nothing awake.
 */
public final class DesktopModule extends AbstractModule {

    /** Installed by the application over the shared graph, replacing its do-nothing restart. */
    public DesktopModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(KeepAwake.class).to(OsKeepAwake.class);
        bind(MemorySource.class).to(SystemMemory.class).in(Singleton.class);
        bind(AppRestart.class).to(DesktopAppRestart.class).in(Singleton.class);
    }
}
