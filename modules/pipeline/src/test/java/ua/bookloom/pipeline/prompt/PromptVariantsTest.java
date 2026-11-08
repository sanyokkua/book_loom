package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.LanguageSupport;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.CallLedgerTestModule;
import ua.bookloom.pipeline.PipelineModule;
import ua.bookloom.pipeline.ReviewModeTestModule;
import ua.bookloom.pipeline.prompt.PromptVariants.LanguageRulesVariant;
import ua.bookloom.pipeline.prompt.PromptVariants.ReviewerVariant;

/**
 * The production injector binds the variants that reproduce today's prompts, and a test injector that binds another
 * value reaches the language rules every service is given.
 */
class PromptVariantsTest {

    @Test
    void defaults_productionInjector_areBundledRulesAndBuilderLayout() {
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

    // Each settled arm deletes its field (arm 7c's proofreadPrompt in task 13.9); task 14.7 deletes the last ones.
    @Test
    void components_duringArm7a_areTheLanguageRulesAndTheReviewer() {
        assertThat(PromptVariants.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("languageRules", "reviewer");
    }

    @Test
    void defaults_untilArm7aIsDecided_keepTodaysReviewer() {
        assertThat(PromptVariants.DEFAULTS.reviewer()).isEqualTo(ReviewerVariant.ON);
        assertThat(new PromptVariants().reviewer()).isEqualTo(ReviewerVariant.ON);
        assertThat(PromptVariants.DEFAULTS.isDefault()).isTrue();
    }

    @Test
    void isDefault_reviewerOff_isFalse() {
        assertThat(PromptVariants.DEFAULTS.withReviewer(ReviewerVariant.OFF).isDefault())
                .isFalse();
    }

    @Test
    void templates_boundVariants_carryTheReviewerChoice() {
        final PromptVariants off = PromptVariants.DEFAULTS.withReviewer(ReviewerVariant.OFF);

        assertThat(new PromptTemplates(off).variants()).isEqualTo(off);
        assertThat(new PromptTemplates().variants()).isEqualTo(PromptVariants.DEFAULTS);
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

        assertThat(injector.getInstance(LanguageSupport.class)).isSameAs(LanguageRules.genericRulesOnly());
    }
}
