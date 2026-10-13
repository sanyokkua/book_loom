package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The relevant-earlier-pairs block has an on/off switch the prompt eval flips to decide it (15i.T8): on until the
 * gate says otherwise, set by {@code BOOKLOOM_RELEVANT_PAIRS} or the {@code bookloom.relevant.pairs} property, the
 * eval passing {@code BOOKLOOM_EVAL_RELEVANT_PAIRS} through as that property.
 */
class RelevantPairsSwitchTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(RelevantPairsSwitch.PROPERTY_NAME);
    }

    @Test
    void default_afterTheGateFailed_isOff() {
        assertThat(RelevantPairsSwitch.DEFAULT).isFalse();
    }

    // The environment wins over the property; a value that is neither on nor off keeps the default.
    @ParameterizedTest
    @CsvSource(
            value = {
                "null,null,false",
                "off,null,false",
                "OFF,null,false",
                "on,null,true",
                "null,off,false",
                "null,on,true",
                "on,off,true",
                "off,on,false",
                "maybe,null,false",
                "null,maybe,false"
            },
            nullValues = "null")
    void resolve_envThenProperty_namesTheSwitch(final String env, final String property, final boolean expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(RelevantPairsSwitch.ENV_NAME, env);

        assertThat(RelevantPairsSwitch.resolve(environment, property)).isEqualTo(expected);
    }

    @Test
    void isOn_propertyOff_isFalse() {
        System.setProperty(RelevantPairsSwitch.PROPERTY_NAME, "off");

        assertThat(RelevantPairsSwitch.isOn()).isFalse();
    }

    @Test
    void isOn_propertyCleared_isTheDefault() {
        assertThat(RelevantPairsSwitch.isOn()).isEqualTo(RelevantPairsSwitch.DEFAULT);
    }
}
