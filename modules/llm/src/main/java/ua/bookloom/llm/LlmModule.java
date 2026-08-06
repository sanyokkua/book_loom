package ua.bookloom.llm;

import com.google.inject.AbstractModule;

/**
 * Guice bindings owned by {@code :llm} — the {@code Provider} port, {@code ProviderFactory}, the inference gate and
 * retry policy.
 *
 * <p>Scaffolding: the bindings arrive with the provider changes.
 */
public final class LlmModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public LlmModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        // No bindings yet.
    }
}
