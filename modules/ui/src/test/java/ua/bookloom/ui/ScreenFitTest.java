package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * Every screen state and screen-level dialog the conformance catalogue lists, in English and Ukrainian, at the
 * smallest window, at the widths around each layout switch and at a full-HD one: no label or button is drawn shorter
 * than its words, no control runs past its viewport or card or is squeezed below its width, and a dialog card stays
 * inside the window. Ukrainian words are about a quarter longer, so they are the ones that overflow first.
 */
class ScreenFitTest extends ConformanceTestBase {

    // The smallest window, either side of the compact-regular floor and two wide ones: run in the inner loop too.
    private static final List<double[]> SIZES = List.of(
            new double[] {CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1040, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1700, CONTENT_AT_MINIMUM_HEIGHT},
            // 700 high: JavaFX's headless screen is a 1000 by 1000 buffer (see LayoutFitTest).
            new double[] {1920, 700});
    // The widths between: `slow`, so only the gate runs them.
    private static final List<double[]> BETWEEN_SIZES = List.of(
            new double[] {1000, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1100, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1250, CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1440, CONTENT_AT_MINIMUM_HEIGHT});

    static Stream<Arguments> screensInEachLanguageAndSize() {
        return cases(SIZES);
    }

    static Stream<Arguments> screensInEachLanguageAtTheWidthsBetween() {
        return cases(BETWEEN_SIZES);
    }

    private static Stream<Arguments> cases(final List<double[]> sizes) {
        return ConformanceCases.SCREENS.stream()
                .flatMap(screen -> Stream.of("en", "uk")
                        .flatMap(language -> sizes.stream()
                                .map(size -> Arguments.of(
                                        Named.of(screen.name(), screen),
                                        language,
                                        Named.of((int) size[0] + " x " + (int) size[1], size)))));
    }

    // IF a Ukrainian label were cut or a dialog ran past the window, THEN the person could not read or press it.
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("screensInEachLanguageAndSize")
    void screen_inEachLanguageAndSize_cutsClipsAndSqueezesNothingAndKeepsDialogsInsideTheWindow(
            final Screen screen, final String language, final double[] size) throws TimeoutException {
        assertFits(screen, language, size);
    }

    // IF a layout switched between the widths the inner loop checks, THEN a control could be cut there.
    @Tag("slow")
    @ParameterizedTest(name = "{0} in {1} at {2}")
    @MethodSource("screensInEachLanguageAtTheWidthsBetween")
    void screen_atTheWidthsBetween_cutsClipsAndSqueezesNothingAndKeepsDialogsInsideTheWindow(
            final Screen screen, final String language, final double[] size) throws TimeoutException {
        assertFits(screen, language, size);
    }

    private void assertFits(final Screen screen, final String language, final double[] size) throws TimeoutException {
        show(screen, Locale.forLanguageTag(language));
        resizeScene(size[0], size[1]);

        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        final List<String> outside =
                ThemeTestSupport.onFx(() -> FitProbe.cardsOutside(scene.getRoot(), size[0], size[1]));
        final List<String> overflowing = ThemeTestSupport.onFx(() -> FitProbe.overflowing(scene.getRoot(), size[0]));
        final List<String> squeezed = ThemeTestSupport.onFx(() -> FitProbe.squeezed(scene.getRoot()));

        assertThat(overflowing).as("controls past their viewport or card").isEmpty();
        assertThat(squeezed).as("controls drawn narrower than they ask").isEmpty();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
        assertThat(outside).as("dialog cards past the window's edge").isEmpty();
    }
}
