package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Whether a named variable is set: only presence is ever answered, never the value. */
class SystemEnvironmentVariablesTest {

    private final SystemEnvironmentVariables variables =
            new SystemEnvironmentVariables(Map.of("LMS_KEY", "sk-secret", "EMPTY", "", "BLANK", "  ")::get);

    @ParameterizedTest
    @CsvSource({"LMS_KEY,true", "EMPTY,false", "BLANK,false", "MISSING,false"})
    void isSet_variable_isTrueOnlyForNonBlankValue(final String name, final boolean expected) {
        assertThat(variables.isSet(name)).isEqualTo(expected);
    }

    @Test
    void isSet_blankName_isFalse() {
        assertThat(variables.isSet(" ")).isFalse();
    }
}
