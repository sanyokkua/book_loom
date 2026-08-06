package ua.bookloom.document;

import com.google.inject.AbstractModule;

/**
 * Guice bindings owned by {@code :document} — the port-to-implementation wiring for document parsing, masking and
 * repackaging.
 *
 * <p>Scaffolding: the bindings arrive with the round-trip changes. It exists now so the module's
 * {@code opens … to com.google.guice} has a real target and change
 * {@code bootstrap-app-launch-and-empty-window} needs no {@code module-info} churn to install it.
 */
public final class DocumentModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public DocumentModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        // No bindings yet.
    }
}
