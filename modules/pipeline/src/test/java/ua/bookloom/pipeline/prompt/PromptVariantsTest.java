package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CallLedgerTestModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.prompt.PromptVariants.LanguageRulesVariant;

/**
 * The production injector binds the variants that reproduce today's prompts, and a test injector that binds another
 * value reaches the language rules every service is given.
 */
class PromptVariantsTest {

    @Test
    void defaults_productionInjector_areBundledRules() {
        final Injector injector = Guice.createInjector(
                new PipelineModule(),
                new ReviewModeTestModule(),
                new CallLedgerTestModule(),
                new DocumentModule(),
                new InMemoryPersistenceModule());

        final PromptVariants variants = injector.getInstance(PromptVariants.class);

        assertThat(variants.languageRules()).isEqualTo(LanguageRulesVariant.BUNDLED);
        assertThat(injector.getInstance(LanguageSupport.class)).isSameAs(LanguageRules.bundled());
    }

    @Test
    void languageSupport_testInjectorBindsGenericOnly_isTheGenericOnlyMap() {
        final Injector injector = Guice.createInjector(Modules.override(
                        new PipelineModule(),
                        new ReviewModeTestModule(),
                        new CallLedgerTestModule(),
                        new DocumentModule(),
                        new InMemoryPersistenceModule())
                .with(binder -> binder.bind(PromptVariants.class)
                        .toInstance(new PromptVariants(LanguageRulesVariant.GENERIC_ONLY))));

        assertThat(injector.getInstance(LanguageSupport.class)).isSameAs(LanguageRules.genericOnly());
    }
}
