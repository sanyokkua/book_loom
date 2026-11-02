package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The language pair an eval run asks for names the mini dataset it reads and the files it writes. */
class EvalPairTest {

    @Test
    void fromEnvironment_unset_isEnglishToUkrainian() {
        assertThat(EvalPair.fromEnvironment(Map.of()).dataset()).isEqualTo("mini-en");
    }

    @ParameterizedTest
    @CsvSource({"en-uk,mini-en", "ru-uk,mini-ru", "RU-UK,mini-ru", "'',mini-en"})
    void fromEnvironment_pair_choosesItsDataset(final String asked, final String dataset) {
        assertThat(EvalPair.fromEnvironment(Map.of(EvalPair.ENV, asked)).dataset())
                .isEqualTo(dataset);
    }

    @Test
    void fromEnvironment_unknownPair_isRefused() {
        final var arg = Map.of(EvalPair.ENV, "fr-uk");
        assertThatThrownBy(() -> EvalPair.fromEnvironment(arg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fr-uk");
    }

    @Test
    void label_pairDialPolicy_namesTheRunSoOthersAreNotOverwritten() {
        assertThat(EvalPair.RU_UK.label("BALANCED", "TRANSLITERATE")).isEqualTo("ru-uk-balanced-TRANSLITERATE");
    }
}
