package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.time.Clock;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.pipeline.export.ExportServiceImpl;
import ua.bookloom.pipeline.project.ProjectServiceImpl;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Guice bindings owned by {@code :pipeline}: the {@code TranslationEngine}, {@code ExportService} and
 * {@code ProjectService} ports bound to their implementations, the clock a run times itself with, and the prompt
 * templates, loaded and slot-checked once at injector creation so a broken template fails the start, not a run.
 *
 * <p>The quality loop, the judge and the self-heal calls ({@code heal}, {@code judge}) need no binding: each carries
 * an {@code @Inject} constructor, so Guice builds them for the engine, which hands the quality loop to every run. The
 * deterministic checks in {@code qa} are static functions.
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
        bind(ExportService.class).to(ExportServiceImpl.class);
        bind(ProjectService.class).to(ProjectServiceImpl.class);
    }

    @Provides
    Clock clock() {
        return Clock.systemUTC();
    }
}
