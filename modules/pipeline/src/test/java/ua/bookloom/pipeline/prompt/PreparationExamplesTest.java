package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;

/**
 * The names-pass prompts show worked examples in the book's own language: the source language's file when it sets
 * them, else the generic file's neutral ones, and never the target's or the pair's.
 */
class PreparationExamplesTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();

    // The task as a call sends it, its own data slots holding a placeholder word.
    private static String task(final PromptName name, @Nullable final String source, final String target) {
        final Map<String, String> data = new HashMap<>();
        name.userSlots().required().stream()
                .filter(slot -> !"targetLanguage".equals(slot))
                .forEach(slot -> data.put(slot, "data"));
        return TEMPLATES.renderTask(
                name,
                new CallFrame(source, target, StyleSheet.from(BookBrief.defaults(target)), ForeignPassagePolicy.KEEP),
                data);
    }

    // IF a Russian book were shown English examples, THEN a small model would answer in the examples' language.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"TERM_READING", "LEXICON_READING", "PRESCAN", "REVIEW_TERMS"})
    void prompt_sourceLanguageWithOwnExamples_usesThem(final PromptName name) {
        final String task = task(name, "ru", "uk");

        assertThat(task)
                .containsAnyOf("Отилия", "сигнальный огонь", "Савва Корнеев")
                .doesNotContain("Ottoline", "Marrow Gate", "Garrick Ashdown", "Отілія", "Сава Корнійчук")
                .doesNotContain("{{");
    }

    // English sets no examples of its own, so it reads the generic file's; the target's (Ukrainian) are never used.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"TERM_READING", "LEXICON_READING", "PRESCAN", "REVIEW_TERMS"})
    void prompt_sourceLanguageWithoutOwnExamples_usesTheGenericOnes(final PromptName name) {
        final String task = task(name, "en", "uk");

        assertThat(task)
                .containsAnyOf("Ottoline", "Marrow Gate", "Garrick Ashdown")
                .doesNotContain("Отілія", "сигнальне вогнище", "Сава Корнійчук", "Отилия");
    }

    // Under Auto no source language is chosen, so the neutral examples are shown.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"TERM_READING", "LEXICON_READING", "PRESCAN", "REVIEW_TERMS"})
    void prompt_auto_usesTheGenericOnes(final PromptName name) {
        final String task = task(name, null, "uk");

        assertThat(task)
                .containsAnyOf("Ottoline", "Marrow Gate", "Garrick Ashdown")
                .doesNotContain("Отілія", "Отилия");
    }
}
