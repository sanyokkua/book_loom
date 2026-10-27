package ua.bookloom.ui;

import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Screen;

/**
 * Every screen state and screen-level dialog the conformance catalogue lists, in English and Ukrainian, at the
 * smallest window and a full-HD one (the inner loop) and at the widths around each layout switch (the gate): no label
 * or button is drawn shorter than its words, no control runs past its viewport or card or is squeezed below its width,
 * and a dialog card stays inside the window. Ukrainian words are about a quarter longer, so they overflow first.
 */
class ScreenFitTest extends ConformanceTestBase {

    static Stream<Arguments> screensInEachLanguageAndSize() {
        return FitMatrix.screenCases(FitMatrix.FAST_SIZES);
    }

    static Stream<Arguments> screensInEachLanguageAtTheWidthsBetween() {
        return FitMatrix.screenCases(FitMatrix.SLOW_SIZES);
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
        FitMatrix.assertFits(scene, size);
    }
}
