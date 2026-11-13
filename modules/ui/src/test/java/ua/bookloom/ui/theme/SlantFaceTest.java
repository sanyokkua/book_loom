package ua.bookloom.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The face a slanted suggestion is set in: JavaFX draws macOS's system font upright even when asked for italic (the
 * italic and the regular glyphs are the same pixels), so a suggested target names a face with a real italic.
 */
class SlantFaceTest {

    // IF the order were ignored, THEN a Windows machine with Arial would lose its own Segoe UI italic.
    @Test
    void pick_severalInstalled_takesTheFirstInPreferenceOrder() {
        assertThat(SlantFace.pick(List.of("Arial", "Helvetica Neue", "Segoe UI")))
                .contains(SlantFace.SEGOE_UI);
        assertThat(SlantFace.pick(List.of("Arial", "Helvetica Neue"))).contains(SlantFace.HELVETICA_NEUE);
        assertThat(SlantFace.pick(List.of("Liberation Sans", "DejaVu Sans"))).contains(SlantFace.DEJAVU_SANS);
    }

    // IF a system had none of them, THEN the field keeps the system face and its likely chip.
    @Test
    void pick_noneInstalled_isEmpty() {
        assertThat(SlantFace.pick(List.of("Menlo", "Courier"))).isEmpty();
    }

    // IF a face had no rule, THEN choosing it would leave the suggestion upright.
    @ParameterizedTest
    @EnumSource(SlantFace.class)
    void stylesheet_eachFace_hasARuleForTheSuggestedTarget(final SlantFace face) {
        final String selector = "." + face.styleClass() + " .glossary-table .text-field:suggested";
        final List<String> bodies = ThemeTestSupport.rules(ThemeTestSupport.themeCss()).entrySet().stream()
                .filter(rule -> Arrays.stream(rule.getKey().split(",", -1))
                        .map(String::strip)
                        .anyMatch(selector::equals))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(bodies).anySatisfy(body -> assertThat(body).contains("\"" + face.family() + "\""));
    }
}
