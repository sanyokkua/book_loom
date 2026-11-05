package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.prompt.PromptVariants;
import ua.bookloom.pipeline.prompt.PromptVariants.LanguageRulesVariant;

/** The eval's variants variable: each named field is bound and the others keep their shipped value. */
class EvalVariantsTest {

    @Test
    void fromEnvironment_languageRules_bindsIt() {
        final PromptVariants variants =
                EvalVariants.fromEnvironment(Map.of(EvalVariants.ENV, "languageRules=GENERIC_ONLY"));

        assertThat(variants).isEqualTo(new PromptVariants(LanguageRulesVariant.GENERIC_ONLY));
    }

    @Test
    void fromEnvironment_unset_isTheDefaults() {
        assertThat(EvalVariants.fromEnvironment(Map.of())).isEqualTo(PromptVariants.DEFAULTS);
    }

    @Test
    void fromEnvironment_unknownField_isRefused() {
        final Map<String, String> environment = Map.of(EvalVariants.ENV, "rulesLayout=TODAY");

        assertThatThrownBy(() -> EvalVariants.fromEnvironment(environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
