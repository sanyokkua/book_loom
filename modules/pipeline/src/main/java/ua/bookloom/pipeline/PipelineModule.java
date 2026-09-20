package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import ua.bookloom.api.pipeline.TranslationEngine;

/**
 * Guice bindings owned by {@code :pipeline} — the {@code TranslationEngine} port bound to its implementation, plus
 * the chunker, context assembler, QA checks and judge.
 *
 * <p>Scaffolding: the bindings arrive with the engine changes.
 */
public final class PipelineModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public PipelineModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(TranslationEngine.class).to(TranslationEngineImpl.class);
    }
}
