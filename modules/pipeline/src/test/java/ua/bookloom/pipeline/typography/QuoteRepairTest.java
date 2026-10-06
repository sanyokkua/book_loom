package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuoteRepairTest {

    private static final String SOURCE = "“Wait,” said the boy, “the lamp has gone out.”";

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
                // stray closer at the paragraph end is dropped when the source does not open with a quote
                "Це кінець.»|Це кінець."
            })
    void repair_knownShapes_returnsBalancedText(final String bad, final String expected) {
        final String source = expected.equals("Це кінець.") ? "It is the end." : SOURCE;

        final QuoteRepair.Repaired repaired = QuoteRepair.repair(source, bad, "en", "uk");

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
}
