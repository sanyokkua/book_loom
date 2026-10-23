package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * Every screen state and screen-level dialog the conformance catalogue lists, in English and Ukrainian, at the
 * smallest window and at a full-HD one: no label or button is drawn shorter than its words, and a dialog card stays
 * inside the window. Ukrainian words are about a quarter longer, so they are the ones that overflow first.
 */
class ScreenFitTest extends ConformanceTestBase {

    private static final List<double[]> SIZES = List.of(
            new double[] {CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT},
            // 700 high: JavaFX's headless screen is a 1000 by 1000 buffer (see LayoutFitTest).
            new double[] {1920, 700});

    static Stream<Arguments> screensInEachLanguageAndSize() {
        return ConformanceCases.SCREENS.stream()
                .flatMap(screen -> Stream.of("en", "uk")
                        .flatMap(language -> SIZES.stream()
                                .map(size -> Arguments.of(
                                        Named.of(screen.name(), screen),
                                        language,
                                        Named.of((int) size[0] + " x " + (int) size[1], size)))));
    }

    // IF a Ukrainian label were cut or a dialog ran past the window, THEN the person could not read or press it.
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("screensInEachLanguageAndSize")
    void screen_inEachLanguageAndSize_cutsNothingAndKeepsDialogsInsideTheWindow(
            final Screen screen, final String language, final double[] size) throws TimeoutException {
        show(screen, Locale.forLanguageTag(language));
        resizeScene(size[0], size[1]);

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        final List<String> outside =
                ThemeTestSupport.onFx(() -> FitProbe.cardsOutside(scene.getRoot(), size[0], size[1]));

        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
        assertThat(outside).as("dialog cards past the window's edge").isEmpty();
    }
}
