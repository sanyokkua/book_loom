package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A pair's file states its output ratio or not; only ru-uk states one (1.2); the other bundled pairs use the default 1.5. */
class PairOutputRatioTest {

    @ParameterizedTest
    @CsvSource({"en,uk", ",uk", "xx,uk"})
    void of_pairWithoutRatio_empty(@Nullable final String source, final String target) {
        assertThat(PairOutputRatio.of(LanguageRules.bundled(), source, target)).isEmpty();
    }

    @Test
    void of_ruUk_statesMeasuredRatio() {
        assertThat(PairOutputRatio.of(LanguageRules.bundled(), "ru", "uk")).hasValue(1.2);
    }
}
