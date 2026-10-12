package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Where a dash-led line is spoken and where it is narration, line by line and not fooled by a number range. */
class SpeechTest {

    // IF a position is in a dash-led line, THEN only the dashes of that line decide, and a range such as 1990–1995 is
    // no dash of speech.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "— Come here. — Ann sat.|Come|false",
                "— Come here. — Ann sat.|Ann|true",
                "— Come here.\\nAnn sat down.|Ann|true",
                "— Come in.\\n— Thanks. — Ann smiled.|Ann|true",
                "— Come in.\\n— Thanks. — Ann smiled.|Thanks|false",
                "— It was 1990–1995 — said Ann.|said|true",
                "— It was 1990–1995 — said Ann.|1995|false",
                "Ann sat — and waited.|waited|true"
            })
    void isNarration_dashLedText_followsTheLineHoldingThePosition(
            final String text, final String at, final boolean narration) {
        final String lines = text.replace("\\n", "\n");

        assertThat(Speech.isNarration(lines, lines.indexOf(at))).isEqualTo(narration);
    }
}
