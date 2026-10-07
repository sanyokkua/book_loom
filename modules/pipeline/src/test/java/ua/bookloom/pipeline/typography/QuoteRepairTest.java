package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuoteRepairTest {

    private static final String SOURCE = "“Wait,” said the boy, “the lamp has gone out.”";
    private static final String PLAIN_SOURCE = "It is the end.";
    private static final String DASH_SOURCE = "He said, \"I'll come — maybe tomorrow.\"";
    private static final String QUOTED_SOURCE = "“Go away.”";

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // stray closer, the paragraph opens with a quote in the source
                "Зачекай», — сказав хлопчик, — «лампа згасла».|«Зачекай», — сказав хлопчик, — «лампа згасла».",
                // unclosed opener before the dash clause
                "«Ходи зі мною, — прошепотіла вона, — «і не видавай ні звуку».|«Ходи зі мною», — прошепотіла вона, — «і не видавай ні звуку».",
                // unclosed opener at the paragraph end
                "«Брама зачинена», — сказав вартовий, — «а ключ загубився.|«Брама зачинена», — сказав вартовий, — «а ключ загубився».",
                // crossed with the low closer
                "«Ти це бачиш?“ — запитав він, — «Он там?»|«Ти це бачиш?» — запитав він, — «Он там?»",
                // crossed inside a nested quote
                "«Він назвав її „лампою мертвих», і ніхто не сперечався», — сказав провідник.|«Він назвав її „лампою мертвих“, і ніхто не сперечався», — сказав провідник.",
                // doubled closer
                "«Біжіть!»» — крикнув він. — «Негайно!»|«Біжіть!» — крикнув він. — «Негайно!»",
                // a closing full stop stays outside the mark
                "«Брама зачинена.|«Брама зачинена»."
            })
    void repair_knownShapes_returnsBalancedText(final String bad, final String expected) {
        final QuoteRepair.Repaired repaired = QuoteRepair.repair(SOURCE, bad, "en", "uk");

        assertThat(repaired.text()).isEqualTo(expected);
        assertThat(repaired.isChanged()).isTrue();
        assertThat(repaired.note()).startsWith("Quote marks repaired");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "«Зачекай», — сказав хлопчик.",
                "⟦g0⟧Ча⟦g1⟧си минали повільно. Зачекай», — сказав хлопчик.",
                "«Він» сказав: Зачекай», а потім пішов.",
                "«Один «два «три.",
                "Просто речення без лапок."
            })
    void repair_unsureOrBalanced_returnsInputUnchanged(final String target) {
        final QuoteRepair.Repaired repaired = QuoteRepair.repair(SOURCE, target, "en", "uk");

        assertThat(repaired.text()).isEqualTo(target);
        assertThat(repaired.isChanged()).isFalse();
        assertThat(repaired.note()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Не сьогодні», — сказала вона.", "«Ходи зі мною, — прошепотіла вона."})
    void repair_sourceItselfOpen_returnsInputUnchanged(final String target) {
        final QuoteRepair.Repaired repaired = QuoteRepair.repair("“It runs on, she said", target, "en", "uk");

        assertThat(repaired.text()).isEqualTo(target);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Зачекай», — сказав хлопчик, — «лампа згасла».",
                "«Ходи зі мною, — прошепотіла вона, — «і не видавай ні звуку».",
                "«Біжіть!»» — крикнув він. — «Негайно!»"
            })
    void repair_repairedText_repairsToItself(final String bad) {
        final String once = QuoteRepair.repair(SOURCE, bad, "en", "uk").text();

        final QuoteRepair.Repaired again = QuoteRepair.repair(SOURCE, once, "en", "uk");

        assertThat(again.text()).isEqualTo(once);
        assertThat(again.isChanged()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "xx"})
    void repair_languageWithoutOwnLineOrEnglishTarget_isLeftAlone(final String targetLanguage) {
        final String target = "Зачекай», — сказав хлопчик.";

        assertThat(QuoteRepair.repair(SOURCE, target, "en", targetLanguage).text())
                .isEqualTo(target);
    }

    @Test
    void repair_strayCloserAtEndWhenSourceDoesNotOpenWithQuote_dropsIt() {
        final QuoteRepair.Repaired repaired = QuoteRepair.repair(PLAIN_SOURCE, "Це кінець.»", "en", "uk");

        assertThat(repaired.text()).isEqualTo("Це кінець.");
    }

    @Test
    void repair_strayCloserAtEndWhenSourceOpensWithQuote_opensTheParagraph() {
        final QuoteRepair.Repaired repaired = QuoteRepair.repair(QUOTED_SOURCE, "Іди геть.»", "en", "uk");

        assertThat(repaired.text()).isEqualTo("«Іди геть.»");
    }

    @Test
    void repair_dashInsideTheSpeech_closesAtTheParagraphEnd() {
        final QuoteRepair.Repaired repaired =
                QuoteRepair.repair(DASH_SOURCE, "Він сказав: «Я прийду — можливо, завтра.", "en", "uk");

        assertThat(repaired.text()).isEqualTo("Він сказав: «Я прийду — можливо, завтра».");
    }

    @Test
    void repair_englishQuotesInsideLowQuotes_keepsThemAndClosesTheOuterMark() {
        final QuoteRepair.Repaired repaired =
                QuoteRepair.repair("“He said ‘yes’ and left.”", "«Він сказав “так” і пішов.", "en", "uk");

        assertThat(repaired.text()).isEqualTo("«Він сказав “так” і пішов».");
    }

    @ParameterizedTest
    @CsvSource({"uk,«,»", "ru,«,»", "be,«,»", "bg,«,»", "de,»,«", "cs,»,«", "sk,»,«", "sl,»,«", "hr,»,«"})
    void repair_englishPairInsideOuterMark_isNeverTurnedIntoACloser(
            final String language, final String open, final String close) {
        final String target = open + "Він сказав “так” і пішов.";

        final QuoteRepair.Repaired repaired = QuoteRepair.repair("“He said ‘yes’ and left.”", target, "en", language);

        assertThat(repaired.text()).isEqualTo(open + "Він сказав “так” і пішов" + close + ".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk", "de"})
    void repair_englishPairAloneIsBalancedEnough_returnsInputUnchanged(final String language) {
        final String target = "Він сказав “так” і пішов.";

        assertThat(QuoteRepair.repair("“He said yes.”", target, "en", language).text())
                .isEqualTo(target);
    }
}
