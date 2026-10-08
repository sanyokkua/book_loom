package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A model that writes « » and the dash as control codes is mapped back only when every code has one clear place. */
class ControlCharacterMapperTest {

    private static final String SOURCE = "\"Miles, where did she go?\" she asked.";

    // IF a pair of codes round a whole line were refused, THEN every directed fix of dialogue was wasted.
    @Test
    void map_codesAroundAWholeLine_becomeTheTargetQuotePair() {
        assertThat(ControlCharacterMapper.map(SOURCE, "\u001eМайлз, куди вона пішла?\u001d", "uk"))
                .contains("«Майлз, куди вона пішла?»");
    }

    @Test
    void map_quotePairAndDialogueDash_becomeQuotesAndEmDash() {
        assertThat(ControlCharacterMapper.map(SOURCE, "\u001eКуди вона пішла\u001d, \u001f спитала вона.", "uk"))
                .contains("«Куди вона пішла», — спитала вона.");
    }

    @ParameterizedTest
    @CsvSource({"uk,«,»", "de,„,“", "xx,«,»"})
    void map_targetLanguage_chooseTheQuotePair(final String language, final char open, final char close) {
        assertThat(ControlCharacterMapper.map(SOURCE, "\u001eHallo\u001d", language))
                .contains(open + "Hallo" + close);
    }

    @Test
    void map_unknownLanguageOrNone_fallsBackToGuillemets() {
        assertThat(ControlCharacterMapper.map(SOURCE, "\u0013Привіт\u001c", null))
                .contains("«Привіт»");
    }

    // IF an odd count were guessed, THEN a quote mark would be invented or dropped silently.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "\u001eКуди вона пішла, спитала вона.",
                "\u001eКуди\u001d вона \u001eпішла",
                "Куди\u001d вона\u001e пішла",
                "Куди вона\u001eпішла"
            })
    void map_unpairableCodes_areNotMapped(final String reply) {
        assertThat(ControlCharacterMapper.map(SOURCE, reply, "uk")).isEmpty();
    }

    // IF the source's own control characters were mixed with the glitch, THEN the two could not be told apart.
    @Test
    void map_sourceHoldingControlCharacters_isNotMapped() {
        assertThat(ControlCharacterMapper.map("Page\fone.", "\u001eСторінка\u001d\f", "uk"))
                .isEmpty();
    }
}
