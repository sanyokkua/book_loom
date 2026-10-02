package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Which shown texts are kept as they are, and by which rule; anything with a word in it is translated. */
class VerbatimRuleTest {

    @ParameterizedTest(name = "[{0}] -> {2}")
    @CsvSource(
            delimiter = '|',
            value = {
                "2|false|SYMBOLS",
                "***|false|SYMBOLS",
                "§ 3 —|false|SYMBOLS",
                "1/2|false|SYMBOLS",
                "⟦g0⟧12⟦g1⟧|false|SYMBOLS",
                "XIV|false|ROMAN_NUMERAL",
                "IV.|false|ROMAN_NUMERAL",
                "— XLII —|false|ROMAN_NUMERAL",
                "I|false|ROMAN_NUMERAL",
                "A|false|SINGLE_CHARACTER",
                "я|false|SINGLE_CHARACTER",
                "⟦g0⟧|false|NO_TEXT",
                "⟦g2⟧|true|LOCKED_TERM",
                "⟦g2⟧!|true|LOCKED_TERM",
                "F = G × (m₁ × m₂) / r²|false|FORMULA",
                "g = 9.81 m/s²|false|FORMULA",
            })
    void match_untranslatableText_namesItsRule(final String shown, final boolean locked, final VerbatimRule rule) {
        assertThat(VerbatimRule.match(shown, locked)).isEqualTo(rule);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Hello", "Chapter 2", "MIX up", "xiv", "A.", "IV2", "Я!", "⟦g2⟧ said", "a = b is true", "x y z"})
    void match_textWithSomethingToTranslate_matchesNoRule(final String shown) {
        assertThat(VerbatimRule.match(shown, true)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {" 2​", "﻿***­"})
    void match_invisibleCharactersAroundSymbols_areIgnored(final String shown) {
        assertThat(VerbatimRule.match(shown, false)).isEqualTo(VerbatimRule.SYMBOLS);
    }
}
