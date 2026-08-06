package ua.bookloom.persistence;

import com.google.inject.AbstractModule;

/**
 * Guice bindings owned by {@code :persistence} — repository ports bound to their JDBI DAOs, plus DB bootstrap and
 * WAL configuration.
 *
 * <p>Scaffolding: the bindings arrive with the storage changes.
 */
public final class PersistenceModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public PersistenceModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        // No bindings yet.
    }
}
