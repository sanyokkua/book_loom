package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@link Borderline#isBorderline}: the polish window below τ, D-3's outright-failure exception included. The
 * {@code true,false,0.72,0.75} row also stands for "0.72 with an echo failed below the floor" — a below-floor echo
 * failure never sets {@code failedOutright} (D-3), so it is the identical input.
 */
class BorderlineTest {

    @ParameterizedTest
    @CsvSource({
        "true,false,0.72,0.75,true",
        "true,false,0.70,0.75,true",
        "true,false,0.68,0.75,false",
        "true,false,0.75,0.75,false",
        "false,false,0.72,0.75,false",
        "true,true,0.72,0.75,false"
    })
    void isBorderline_everyCombination_matchesTheWindow(
            final boolean hardGatesPass,
            final boolean failedOutright,
            final double confidence,
            final double tau,
            final boolean expected) {
        assertThat(Borderline.isBorderline(hardGatesPass, failedOutright, confidence, tau))
                .isEqualTo(expected);
    }
}
