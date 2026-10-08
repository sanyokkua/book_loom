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
 * The activity log's monospaced face: the first sturdy one the system has, since the logical "Monospaced" is the thin
 * Courier New on macOS and Windows, which reads as grey at the log's size.
 */
class MonoFontTest {

    // IF the order were ignored, THEN a Mac with DejaVu installed by some tool would get it instead of Menlo.
    @Test
    void pick_severalInstalled_takesTheFirstInPreferenceOrder() {
        assertThat(MonoFont.pick(List.of("Arial", "DejaVu Sans Mono", "Menlo"))).contains(MonoFont.MENLO);
        assertThat(MonoFont.pick(List.of("Consolas", "DejaVu Sans Mono"))).contains(MonoFont.CONSOLAS);
    }

    // IF a system had none of them, THEN the log keeps the logical face rather than a proportional one.
    @Test
    void pick_noneInstalled_isEmpty() {
        assertThat(MonoFont.pick(List.of("Arial", "Helvetica"))).isEmpty();
    }

    // IF a face had no rule, THEN choosing it would change nothing on screen.
    @ParameterizedTest
    @EnumSource(MonoFont.class)
    void stylesheet_eachFace_hasARuleForTheLogLines(final MonoFont face) {
        final String selector = "." + face.styleClass() + " .activity-log .list-cell .label";
        final List<String> bodies = ThemeTestSupport.rules(ThemeTestSupport.themeCss()).entrySet().stream()
                .filter(rule -> Arrays.stream(rule.getKey().split(",", -1))
                        .map(String::strip)
                        .anyMatch(selector::equals))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(bodies).anySatisfy(body -> assertThat(body).contains("\"" + face.family() + "\""));
    }
}
