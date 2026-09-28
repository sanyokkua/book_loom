package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Guice bindings owned by {@code :pipeline}: the {@code TranslationEngine} port bound to its implementation and the
 * prompt templates, loaded and slot-checked once at injector creation so a broken template fails the start, not a run.
 *
 * <p>The judge, the self-heal calls ({@code judge}, {@code heal}) and the stored-project service carry
 * {@code @Inject} constructors but no binding yet: the run that uses them, and the {@code ProjectService} binding,
 * arrive with the job's move onto a stored project. The deterministic checks in {@code qa} are static functions.
 */
public final class PipelineModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public PipelineModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(PromptTemplates.class).asEagerSingleton();
        bind(TranslationEngine.class).to(TranslationEngineImpl.class);
    }
}
