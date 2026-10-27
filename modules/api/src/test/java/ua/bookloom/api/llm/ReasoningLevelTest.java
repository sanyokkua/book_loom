package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.settings.ReasoningSetting;

class ReasoningLevelTest {

    // Low and On let the model think, so a reply needs output-cap headroom; Off does not.
    @ParameterizedTest
    @CsvSource({"OFF,false", "LOW,true", "ON,true"})
    void isThinking_level_isTrueOnlyWhenTheModelReasons(final ReasoningLevel level, final boolean expected) {
        assertThat(level.isThinking()).isEqualTo(expected);
    }

    // The stored Generation setting maps to the level of the same name.
    @ParameterizedTest
    @CsvSource({"OFF,OFF", "LOW,LOW", "ON,ON"})
    void of_storedSetting_givesTheLevelOfTheSameName(final ReasoningSetting setting, final ReasoningLevel expected) {
        assertThat(ReasoningLevel.of(setting)).isEqualTo(expected);
    }
}
