package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * Every button, toggle, field, combo and column header a person can operate explains itself on hover, on every screen
 * and dialog state the conformance cases reach. The walk is over the real scene, so a control added later without a
 * tip fails here by name.
 */
class TooltipCoverageTest extends ConformanceTestBase {

    static Stream<Arguments> screens() {
        return ConformanceCases.SCREENS.stream().map(screen -> Arguments.of(Named.of(screen.name(), screen)));
    }

    // IF a control were built without a hover explanation, THEN a person could not learn what it does without pressing
    // it.
    @ParameterizedTest(name = "{0}")
    @MethodSource("screens")
    void screen_everyOperableControl_carriesAHoverExplanation(final Screen screen) throws TimeoutException {
        show(screen);

        final List<String> missing = ThemeTestSupport.onFx(() -> TooltipProbe.untipped(scene.getRoot()));

        assertThat(missing)
                .as("controls without a tooltip on %s", screen.name())
                .isEmpty();
    }
}
