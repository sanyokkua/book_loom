package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReasoningLevelTest {

    // Low and On let the model think, so a reply needs output-cap headroom; Off does not.
    @ParameterizedTest
    @CsvSource({"OFF,false", "LOW,true", "ON,true"})
    void isThinking_level_isTrueOnlyWhenTheModelReasons(final ReasoningLevel level, final boolean expected) {
        assertThat(level.isThinking()).isEqualTo(expected);
    }
}
