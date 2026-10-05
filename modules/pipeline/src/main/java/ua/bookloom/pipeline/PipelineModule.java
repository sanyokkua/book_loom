package ua.bookloom.pipeline;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import java.time.Clock;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.export.ExportServiceImpl;
import ua.bookloom.pipeline.glossary.GlossaryServiceImpl;
import ua.bookloom.pipeline.lexicon.LexiconServiceImpl;
import ua.bookloom.pipeline.project.ProjectServiceImpl;
import ua.bookloom.pipeline.prompt.LanguageRules;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewDeskImpl;

/**
 * Guice bindings owned by {@code :pipeline}: the {@code TranslationEngine}, {@code ExportService},
 * {@code ProjectService}, {@code GlossaryService}, {@code LexiconService} and {@code ReviewDesk} ports bound to their implementations, the
 * language-rules map behind {@code LanguageSupport}, the
 * clock a run times itself with, and the prompt
 * templates, loaded and slot-checked once at injector creation so a broken template fails the start, not a run.
 *
 * <p>The quality loop, the reviewer and the self-heal calls ({@code heal}, {@code reviewer}) need no binding: each carries
 * an {@code @Inject} constructor, so Guice builds them for the engine, which hands the quality loop to every run. The
 * deterministic checks in {@code qa} are static functions.
 *
 * <p>{@code ReviewMode} is deliberately not bound here: the composition root chooses it once at launch, and a binding
 * here would clash with it. An injector built from this module alone must bind it too.
 */
public final class PipelineModule extends AbstractModule {

    /** Installed by the composition root in {@code :app}. */
    public PipelineModule() {
        // Guice modules are constructed, not injected.
    }

    @Override
    protected void configure() {
        bind(PromptTemplates.class).asEagerSingleton();
        bind(LanguageSupport.class).toInstance(LanguageRules.bundled());
        bind(TranslationEngine.class).to(TranslationEngineImpl.class);
        bind(ExportService.class).to(ExportServiceImpl.class);
        bind(ProjectService.class).to(ProjectServiceImpl.class);
        bind(GlossaryService.class).to(GlossaryServiceImpl.class);
        bind(LexiconService.class).to(LexiconServiceImpl.class);
        bind(ReviewDesk.class).to(ReviewDeskImpl.class);
        // No dictionary of words is bundled or planned, so a run's word check has no opinion.
        bind(WordValidator.class).toInstance(WordValidator.none());
    }

    @Provides
    Clock clock() {
        return Clock.systemUTC();
    }

    @Provides
    RecoveryTimer recoveryTimer() {
        return RecoveryTimer.REAL;
    }
}
