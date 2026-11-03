package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.state.QuantChipModel;

/** The quant chip says the token and the tier in words, and the role colour class follows the tier. */
class QuantChipTest extends FxTestBase {

    // IF a tier drew the wrong role, THEN a risky quant would look as safe as a good one.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "EXCELLENT, Q8_0 · excellent, chip-ok",
        "GOOD, UD-Q4_K_XL · good, chip-ok",
        "ACCEPTABLE, Q4_K_M · acceptable, chip-neutral",
        "POOR, Q3_K_M · poor, chip-warn",
        "UNUSABLE, Q2_K_L · unusable, chip-err",
        "REMOVED, Q4_0_4_4 · removed, chip-err",
        "UNMEASURED, nvfp4 · not measured, chip-neutral"
    })
    void of_eachTier_wordsAndRole(final QuantTier tier, final String text, final String role) {
        final String token = text.substring(0, text.indexOf(' '));

        final Label chip = onFx(() -> QuantChip.of(new QuantChipModel(token, tier, true), EnglishMessages.create()));

        assertThat(chip.getText()).isEqualTo(text);
        assertThat(chip.getStyleClass()).containsExactly("chip", role);
        assertThat(chip.getId()).isEqualTo("quant-chip");
    }

    // IF a name without a size showed an invented tier, THEN the chip would claim a judgement nobody made.
    @Test
    void of_tierUnknown_showsTheTokenAloneInTheNeutralRole() {
        final Label chip = onFx(() -> QuantChip.of(new QuantChipModel("nvfp4", null, false), EnglishMessages.create()));

        assertThat(chip.getText()).isEqualTo("nvfp4");
        assertThat(chip.getStyleClass()).containsExactly("chip", "chip-neutral");
    }

    // IF the chip could shrink below its words, THEN a narrow row would show "Q4_K_…" and defeat the chip.
    @Test
    void of_anyChip_neverShrinksBelowItsWordsAndDoesNotWrap() {
        final Label chip = onFx(
                () -> QuantChip.of(new QuantChipModel("Q4_K_M", QuantTier.ACCEPTABLE, true), EnglishMessages.create()));

        assertThat(chip.getMinWidth()).isEqualTo(Region.USE_PREF_SIZE);
        assertThat(chip.isWrapText()).isFalse();
    }
}
