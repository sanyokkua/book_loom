package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The source-aware speech rewrites of the typography pass (plan B7), on invented sentences. */
class SpeechRepairsTest {

    private static String uk(final String source, final String target) {
        return TypographyNormalizer.normalise(source, target, "uk").text();
    }

    private static String lettersOf(final String text) {
        final char[] letters =
                text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]", "").toCharArray();
        Arrays.sort(letters);
        return new String(letters);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "comma inside before dash|He said.|«Ходімо,» — сказав він.|«Ходімо», — сказав він.",
                "comma inside before en dash|He said.|«Ходімо,» – сказав він.|«Ходімо», – сказав він.",
                "comma outside already|He said.|«Ходімо», — сказав він.|«Ходімо», — сказав він.",
                "comma inside no dash|He said.|«Ходімо,» і пішов.|«Ходімо,» і пішов.",
            })
    void normalise_commaInsideClosingQuote_movesOutsideBeforeADash(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "mixed outside becomes primary|\"Go\" he said.|Він сказав: „Ходімо“ і пішов.|Він сказав: «Ходімо» і пішов.",
                "nested stays secondary|\"Go\" he said.|«Він сказав „так“ і пішов».|«Він сказав „так“ і пішов».",
                "unclosed low quote untouched|He said.|Він сказав „так і пішов.|Він сказав „так і пішов.",
            })
    void normalise_mixedLowQuotes_becomePrimaryOnlyOutsideAPrimaryPair(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "stray closer at end|He said.|«Привіт», — сказав він.\"|«Привіт», — сказав він.",
                "no other quotes kept|It is 5\"|Це 5\"|Це 5\"",
                "mid-text kept|He said.|«Привіт» ще\" дещо|«Привіт» ще\" дещо",
            })
    void normalise_loneStraightQuote_removedAtEdgeOnlyWhenTheRestIsBalanced(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "continuation restored|\"We go on,\" he said.|Ми підемо далі», — сказав він.|«Ми підемо далі», — сказав він.",
                "curly source opener|“We go on,” he said.|Ми підемо далі», — сказав він.|«Ми підемо далі», — сказав він.",
                "source does not open with a quote|We go on, he said.|Ми підемо далі», — сказав він.|Ми підемо далі», — сказав він.",
                "already opens|\"We go on,\" he said.|«Ми підемо далі», — сказав він.|«Ми підемо далі», — сказав він.",
                "dash dialogue left|\"We go on,\" he said.|— Ми підемо далі», — сказав він.|— Ми підемо далі», — сказав він.",
                "two extra closers left|\"We go on,\" he said.|Ми» підемо» далі|Ми» підемо» далі",
            })
    void normalise_sourceOpensWithQuote_prependsOpeningQuoteOnlyForOneExtraCloser(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "one source run merged|He said \"Go home now\".|Він сказав: «Іди додому» «зараз».|Він сказав: «Іди додому зараз».",
                "two source runs kept|He said \"Go\" and \"stay\".|Він сказав: «Іди» «зостанься».|Він сказав: «Іди» «зостанься».",
                "narration between kept|\"Go,\" he said. \"Now.\"|«Іди», — сказав він. «Зараз».|«Іди», — сказав він. «Зараз».",
            })
    void normalise_splitSpeech_mergesOnlyWhenTheSourceHasOneRun(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "paragraph start|The door opened.|двері відчинилися.|Двері відчинилися.",
                "after opening quote|\"The door opened.\"|«двері відчинилися.»|«Двері відчинилися.»",
                "after closing quote and period|The door opened. It was dark.|«Двері відчинилися». було темно.|«Двері відчинилися». Було темно.",
                "source lower case kept|and the door opened.|і двері відчинилися.|і двері відчинилися.",
                "dash continuation kept|The door opened.|— і двері відчинилися.|— і двері відчинилися.",
                "token start kept|The door opened.|⟦g1⟧двері відчинилися.|⟦g1⟧двері відчинилися.",
            })
    void normalise_lowercaseSentenceStart_isCapitalisedWhenTheSourceStartsUpper(
            final String name, final String source, final String raw, final String expected) {
        assertThat(uk(source, raw)).isEqualTo(expected);
    }

    @Test
    void normalise_realShapes_areAllRepairedTogether() {
        final String source = "\"We go on and never stop,\" he said.";
        final String raw = "Ми підемо далі,» «і не зупинимось,» — сказав він.\"";

        final Normalisation result = TypographyNormalizer.normalise(source, raw, "uk");

        assertThat(result.text()).isEqualTo("«Ми підемо далі, і не зупинимось», — сказав він.");
        assertThat(result.note())
                .contains("1 comma moved outside the quote")
                .contains("1 opening quote restored")
                .contains("1 split speech merged")
                .contains("1 stray quote mark removed");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "«Ходімо,» — сказав він.",
                "Він сказав: „Ходімо“ і пішов.",
                "«Привіт», — сказав він.\"",
                "Ми підемо далі», — сказав він.",
                "Він сказав: «Іди додому» «зараз».",
                "двері відчинилися. «Так». було темно.",
            })
    void normalise_speechRewrites_areIdempotentAndKeepEveryLetter(final String raw) {
        final String source = "\"We go on,\" The door opened.";

        final String once = uk(source, raw);

        assertThat(uk(source, once)).isEqualTo(once);
        assertThat(lettersOf(once)).isEqualTo(lettersOf(raw));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "«Go,» — he said.",
                "He said „so“ and »left«.",
                "the door opened. «Yes». it was dark.",
                "He said «go» «now».",
            })
    void normalise_englishTarget_leavesSpeechAlone(final String raw) {
        assertThat(TypographyNormalizer.normalise("\"A,\" he said. \"B\"", raw, "en")
                        .text())
                .isEqualTo(raw);
    }

    @Test
    void normalise_withoutASource_appliesOnlyRulesThatNeedNone() {
        assertThat(TypographyNormalizer.normalise("«Ходімо,» — сказав він.\"", "uk")
                        .text())
                .isEqualTo("«Ходімо», — сказав він.");
    }
}
