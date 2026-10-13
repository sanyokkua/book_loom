package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The batch draft's one-line notes arm has an on/off switch the prompt eval flips (15i.X2): off until measured,
 * set by {@code BOOKLOOM_BATCH_NOTES} or the {@code bookloom.batch.notes} property.
 */
class BatchNotesSwitchTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(BatchNotesSwitch.PROPERTY_NAME);
    }

    @Test
    void default_untilMeasured_isOff() {
        assertThat(BatchNotesSwitch.DEFAULT).isFalse();
    }

    // The environment wins over the property; a value that is neither on nor off keeps the default.
    @ParameterizedTest
    @CsvSource(
            value = {
                "null,null,false",
                "on,null,true",
                "ON,null,true",
                "off,null,false",
                "null,on,true",
                "null,off,false",
                "off,on,false",
                "on,off,true",
                "maybe,null,false",
                "null,maybe,false"
            },
            nullValues = "null")
    void resolve_envThenProperty_namesTheSwitch(final String env, final String property, final boolean expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(BatchNotesSwitch.ENV_NAME, env);

        assertThat(BatchNotesSwitch.resolve(environment, property)).isEqualTo(expected);
    }

    @Test
    void isOn_propertyOn_isTrue() {
        System.setProperty(BatchNotesSwitch.PROPERTY_NAME, "on");

        assertThat(BatchNotesSwitch.isOn()).isTrue();
    }

    @Test
    void isOn_nothingSet_isOff() {
        assertThat(BatchNotesSwitch.isOn()).isFalse();
    }
}
