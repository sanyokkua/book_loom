package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.ui.i18n.Messages;

/** What the quality choice says about time before Start: a measured figure only when a speed is stored. */
class StartEstimatesTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));

    // IF an estimate appeared with no stored speed, THEN a guess of hours would be believed.
    @Test
    void of_noStoredSpeed_givesTheRelativeCostsAndNoHours() {
        final StartEstimate estimate = StartEstimates.of(Optional::empty, 400, QualityDial.BALANCED);

        assertThat(estimate).isEqualTo(new StartEstimate.Relative());
        assertThat(estimate.lines(EN)).containsExactly("Balanced ≈ 1.5× Fast", "Max ≈ 3× Fast");
    }

    @Test
    void of_noStoredSpeed_readsInUkrainianToo() {
        assertThat(StartEstimates.of(Optional::empty, 400, QualityDial.MAX).lines(UK))
                .containsExactly("Збалансовано ≈ 1,5× від Швидко", "Найвища якість ≈ 3× від Швидко");
    }

    // IF the stored speed were not scaled by the quality, THEN Max would promise the time of Fast.
    @ParameterizedTest(name = "{0}: {1} s")
    @CsvSource({"FAST, 300", "BALANCED, 450", "MAX, 900"})
    void of_storedSpeedOfThreeSeconds_scalesTheBookByTheDial(final QualityDial dial, final long seconds) {
        final StartEstimate estimate = StartEstimates.of(() -> Optional.of(3.0), 100, dial);

        assertThat(estimate).isEqualTo(new StartEstimate.Measured(Duration.ofSeconds(seconds)));
    }

    // IF the measured line did not say where it comes from, THEN it would read as a promise.
    @Test
    void lines_measured_marksTheFigureAsAnEstimateFromTheModelTest() {
        final StartEstimate estimate = new StartEstimate.Measured(Duration.ofSeconds(450));

        assertThat(estimate.lines(EN))
                .isEqualTo(List.of("For this book on this computer: about 8 min (estimate from the model test)"));
    }

    @ParameterizedTest
    @CsvSource({"0.0, 100", "-1.0, 100", "3.0, 0"})
    void of_unusableSpeedOrEmptyBook_givesTheRelativeCosts(final double speed, final int segments) {
        assertThat(StartEstimates.of(() -> Optional.of(speed), segments, QualityDial.FAST))
                .isEqualTo(new StartEstimate.Relative());
    }
}
