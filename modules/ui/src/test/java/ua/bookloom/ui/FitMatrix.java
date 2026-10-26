package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Scene;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

/**
 * The matrix the fit and scroll tests share: what is shown, in English and Ukrainian, at a list of scene sizes, and
 * the one set of {@link FitProbe} assertions a shown scene must pass.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FitMatrix {

    /** The smallest window, either side of the compact-regular floor and two wide ones: run in the inner loop too. */
    static final List<double[]> FAST_SIZES = List.of(
            new double[] {ShellTestBase.CONTENT_AT_MINIMUM_WIDTH, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1040, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1700, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            // 700 high: JavaFX's headless screen is a 1000 by 1000 buffer (see LayoutFitTest).
            new double[] {1920, 700});

    /** The widths between: `slow`, so only the gate runs them. */
    static final List<double[]> SLOW_SIZES = List.of(
            new double[] {1000, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1100, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1250, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT},
            new double[] {1440, ShellTestBase.CONTENT_AT_MINIMUM_HEIGHT});

    private static final List<String> LANGUAGES = List.of("en", "uk");

    /** Every catalogue screen in each language at each size, as (screen, language, size). */
    static Stream<Arguments> screenCases(final List<double[]> sizes) {
        return cases(ConformanceCases.SCREENS.stream().map(screen -> Named.of(screen.name(), screen)), sizes);
    }

    /** Every subject in each language at each size, as (subject, language, size). */
    static <T> Stream<Arguments> cases(final Stream<Named<T>> subjects, final List<double[]> sizes) {
        return subjects.flatMap(subject -> LANGUAGES.stream().flatMap(language -> cases(subject, language, sizes)));
    }

    /** One subject in one language at each size, as (subject, language, size). */
    static <T> Stream<Arguments> cases(final Named<T> subject, final String language, final List<double[]> sizes) {
        return sizes.stream()
                .map(size -> Arguments.of(subject, language, Named.of((int) size[0] + " x " + (int) size[1], size)));
    }

    /**
     * No control runs past its viewport or card or is squeezed below its width, no part is drawn shorter than its
     * words, and no dialog card leaves the {@code size} window.
     */
    static void assertFits(final Scene scene, final double[] size) {
        final List<String> overflowing = ThemeTestSupport.onFx(() -> FitProbe.overflowing(scene.getRoot(), size[0]));
        final List<String> squeezed = ThemeTestSupport.onFx(() -> FitProbe.squeezed(scene.getRoot()));
        final List<String> cut = ThemeTestSupport.onFx(() -> FitProbe.cutParts(scene.getRoot()));
        final List<String> outside =
                ThemeTestSupport.onFx(() -> FitProbe.cardsOutside(scene.getRoot(), size[0], size[1]));

        assertThat(overflowing).as("controls past their viewport or card").isEmpty();
        assertThat(squeezed).as("controls drawn narrower than they ask").isEmpty();
        assertThat(cut).as("parts drawn shorter than their words").isEmpty();
        assertThat(outside).as("dialog cards past the window's edge").isEmpty();
    }
}
