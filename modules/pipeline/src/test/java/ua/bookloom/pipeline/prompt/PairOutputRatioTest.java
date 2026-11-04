package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A pair's file states its output ratio or not; the bundled pairs state none, so the default 1.5 applies. */
class PairOutputRatioTest {

    @ParameterizedTest
    @CsvSource({"ru,uk", "en,uk", ",uk", "xx,uk"})
    void of_pairWithoutRatio_empty(@Nullable final String source, final String target) {
        assertThat(PairOutputRatio.of(LanguageRules.bundled(), source, target)).isEmpty();
    }
}
