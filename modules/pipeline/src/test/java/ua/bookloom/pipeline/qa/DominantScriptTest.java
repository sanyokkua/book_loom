package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.util.lang.Script;

/** The dominant-script signal {@link ForeignMarking} uses. */
class DominantScriptTest {

    @ParameterizedTest
    @CsvSource({
        "'He opened the old door.',LATIN,LATIN",
        "'Він відчинив старі двері.',LATIN,CYRILLIC",
        "'Γνῶθι σεαυτόν',LATIN,GREEK"
    })
    void of_singleScriptText_returnsThatScript(final String text, final Script tieBreak, final Script expected) {
        assertThat(DominantScript.of(text, tieBreak)).isEqualTo(expected);
    }

    @Test
    void of_noLetters_returnsTieBreak() {
        assertThat(DominantScript.of("123, .!", Script.LATIN)).isEqualTo(Script.LATIN);
    }

    @Test
    void of_hanAndJapaneseTie_resolvesToTheTieBreak() {
        // Every Han character counts toward both HAN and JAPANESE (Script.letterScripts()), so a
        // purely-Han text always ties between them; the tie resolves to the source language's own script.
        assertThat(DominantScript.of("中文", Script.JAPANESE)).isEqualTo(Script.JAPANESE);
        assertThat(DominantScript.of("中文", Script.HAN)).isEqualTo(Script.HAN);
    }

    @Test
    void of_tieBreakNotAmongTiedScripts_returnsFirstByDeclarationOrder() {
        // "中文" ties HAN and JAPANESE; LATIN, the tie-break, has zero letters and does not tie, so the
        // first tied script in Script's declaration order (LATIN, CYRILLIC, GREEK, HAN, JAPANESE, ...) wins.
        assertThat(DominantScript.of("中文", Script.LATIN)).isEqualTo(Script.HAN);
    }
}
