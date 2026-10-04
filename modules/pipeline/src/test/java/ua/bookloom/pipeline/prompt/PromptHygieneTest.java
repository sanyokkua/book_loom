package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptHygieneTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "(none)", "None", "[none]", "n/a", "N/A.", "-", "—", "(empty)"})
    void isFiller_placeholderOrBlank_isTrue(final String line) {
        assertThat(PromptHygiene.isFiller(line)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hale → Гейл (character, male)", "(none — write no ⟦gN⟧ token)", "None of them came."})
    void isFiller_contentLine_isFalse(final String line) {
        assertThat(PromptHygiene.isFiller(line)).isFalse();
    }

    @Test
    void clean_fillerAndRepeats_keepsTheFirstOfEachContentLineInOrder() {
        assertThat(PromptHygiene.clean(List.of("b", "(none)", "a", "", "b", "a")))
                .containsExactly("b", "a");
    }

    @Test
    void cleanText_fillerText_isNull() {
        assertThat(PromptHygiene.cleanText("(none)")).isNull();
        assertThat(PromptHygiene.cleanText("The book so far.")).isEqualTo("The book so far.");
    }

    @Test
    void draftContext_fillerEverywhere_leavesNoSection() {
        final DraftContext context =
                new DraftContext(List.of("(none)", "Один.", "Один."), "(none)", List.of("-"), List.of(""), List.of());

        assertThat(context.precedingTargets()).containsExactly("Один.");
        assertThat(context.summary()).isNull();
        assertThat(context.glossaryLines()).isEmpty();
        assertThat(context.memoryLines()).isEmpty();
    }
}
