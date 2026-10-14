package ua.bookloom.pipeline.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Drafting a long paragraph as sentence groups is an experiment arm (15i.X3), off until a measurement says otherwise: set
 * by {@code BOOKLOOM_LONG_PARAGRAPHS} or the {@code bookloom.long.paragraphs} property, the eval passing
 * {@code BOOKLOOM_EVAL_LONG_PARAGRAPHS} through as that property.
 */
class LongParagraphsSwitchTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(LongParagraphs.PROPERTY_NAME);
    }

    @Test
    void default_beforeAnyMeasurement_isOff() {
        assertThat(LongParagraphs.DEFAULT).isFalse();
    }

    // The environment wins over the property; a value that is neither groups nor off keeps the default.
    @ParameterizedTest
    @CsvSource(
            value = {
                "null,null,false",
                "groups,null,true",
                "GROUPS,null,true",
                "off,null,false",
                "null,groups,true",
                "null,off,false",
                "off,groups,false",
                "groups,off,true",
                "maybe,null,false",
                "null,maybe,false"
            },
            nullValues = "null")
    void resolve_envThenProperty_namesTheSwitch(final String env, final String property, final boolean expected) {
        final Map<String, String> environment = env == null ? Map.of() : Map.of(LongParagraphs.ENV_NAME, env);

        assertThat(LongParagraphs.resolve(environment, property)).isEqualTo(expected);
    }

    @Test
    void isOn_propertyGroups_isTrue() {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");

        assertThat(LongParagraphs.isOn()).isTrue();
    }

    @Test
    void isOn_propertyCleared_isTheDefault() {
        assertThat(LongParagraphs.isOn()).isEqualTo(LongParagraphs.DEFAULT);
    }

    // More than 180 words is long; the count leaves the placeholder tokens out, whose letter is no word.
    @ParameterizedTest
    @CsvSource({"179,false", "180,false", "181,true"})
    void isLong_wordsAroundTheLine_isLongAboveOneHundredEighty(final int words, final boolean expected) {
        final String text = "word ".repeat(words - 1) + "⟦g0⟧last⟦g1⟧";

        assertThat(LongParagraphs.words(text)).isEqualTo(words);
        assertThat(LongParagraphs.isLong(text)).isEqualTo(expected);
    }
}
