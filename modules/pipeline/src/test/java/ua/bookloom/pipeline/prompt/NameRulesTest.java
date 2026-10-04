package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.NamePolicy;

/** The name rule a suggestion call states: per policy, with the target language's own spelling convention. */
class NameRulesTest {

    @ParameterizedTest
    @CsvSource({
        "uk,Ukrainian orthography's practical transcription",
        "uk-UA,Гарроу",
        "de,spell them in German by their sound; never translate what they mean. Spell the name the way an educated"
    })
    void rule_transliterate_namesTheTargetsOwnConventionOrTheNeutralOne(final String target, final String phrase) {
        assertThat(NameRules.bundled().rule(NamePolicy.TRANSLITERATE, target))
                .contains(phrase)
                .doesNotContain("{target}", "{convention}");
    }

    @Test
    void rule_translate_asksForTheNaturalEquivalentAndAddsTheTermsLine() {
        assertThat(NameRules.bundled().rule(NamePolicy.TRANSLATE, "uk"))
                .contains("natural Ukrainian equivalent", "\n- A name made of ordinary words");
    }

    @Test
    void rule_keepOriginal_asksOnlyAboutTermsWithNoSeparateTermsLine() {
        assertThat(NameRules.bundled().rule(NamePolicy.KEEP_ORIGINAL, "uk"))
                .isEqualTo("- Every listed row is a term, not a name: translate it into Ukrainian by meaning, as a"
                        + " dictionary would.");
    }
}
