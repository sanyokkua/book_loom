package ua.bookloom.document;

import com.google.inject.AbstractModule;
import ua.bookloom.api.document.DocumentPort;

/**
 * Guice bindings owned by {@code :document} — the port-to-implementation wiring for document parsing, masking and
 * repackaging.
 */
public final class DocumentModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public DocumentModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(DocumentPort.class).to(DocumentService.class);
    }
}
