package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.ui.i18n.Messages;

/** The one rule of the estimate before Start: measured per kind, a range from the draft speed, or nothing. */
class StartEstimatesTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));

    private static Map<CallKind, MeasuredSpeed> draft(final double seconds, final MeasuredFrom from) {
        return Map.of(CallKind.DRAFT, new MeasuredSpeed(seconds, from));
    }

    // IF a time showed with nothing measured, THEN a guess of hours would be believed.
    @Test
    void of_nothingStored_givesNoLine() {
        final StartEstimate estimate = StartEstimates.of(Map.of(), 400, 50, QualityDial.BALANCED, 1, 0);

        assertThat(estimate).isEqualTo(new StartEstimate.None());
        assertThat(estimate.lines(EN)).isEmpty();
        assertThat(estimate.tip()).isEmpty();
    }

    // Tokens per second or a reviewer figure alone are no draft speed, so they give no time.
    @Test
    void of_onlyAReviewFigure_givesNoLine() {
        final StartEstimate estimate = StartEstimates.of(
                Map.of(CallKind.REVIEW, new MeasuredSpeed(6.0, MeasuredFrom.RUN)), 400, 50, QualityDial.BALANCED, 1, 0);

        assertThat(estimate).isEqualTo(new StartEstimate.None());
    }

    // The spec's case: 300 drafts of 3 s and 20 proofread calls of 6 s at Balanced, no other kind, read 17 min.
    @Test
    void of_everyPlannedKindMeasuredByARun_isMeasuredPerKind() {
        final StartEstimate estimate = StartEstimates.of(
                Map.of(
                        CallKind.DRAFT, new MeasuredSpeed(3.0, MeasuredFrom.RUN),
                        CallKind.PROOFREAD, new MeasuredSpeed(6.0, MeasuredFrom.RUN)),
                300,
                20,
                QualityDial.BALANCED,
                0,
                0);

        assertThat(estimate).isEqualTo(new StartEstimate.Measured(Duration.ofMinutes(17)));
    }

    // IF the summaries the run writes were left out, THEN the estimate would be short by every chapter's call.
    @Test
    void of_summariesPlannedAndMeasured_areAddedToTheMeasuredTime() {
        final StartEstimate estimate = StartEstimates.of(
                Map.of(
                        CallKind.DRAFT, new MeasuredSpeed(3.0, MeasuredFrom.RUN),
                        CallKind.CHAPTER_SUMMARY, new MeasuredSpeed(30.0, MeasuredFrom.RUN)),
                300,
                20,
                QualityDial.FAST,
                0,
                4);

        assertThat(estimate).isEqualTo(new StartEstimate.Measured(Duration.ofMinutes(17)));
    }

    // With no summary measured the plan cannot be priced kind by kind, so the draft speed gives the range.
    @Test
    void of_summariesPlannedButNeverMeasured_isTheRangeFromTheDraftSpeed() {
        final StartEstimate estimate = StartEstimates.of(draft(3.0, MeasuredFrom.RUN), 300, 20, QualityDial.FAST, 0, 4);

        assertThat(estimate).isInstanceOf(StartEstimate.Ranged.class);
    }

    // IF a draft speed from the model test counted as measured, THEN a single test call would read as a whole run.
    @Test
    void of_draftFromTheTestAtFast_isARangeFromTheModelTest() {
        final StartEstimate estimate =
                StartEstimates.of(draft(3.0, MeasuredFrom.TEST), 3000, 375, QualityDial.FAST, 0, 0);

        assertThat(estimate)
                .isEqualTo(new StartEstimate.Ranged(Duration.ofHours(2), Duration.ofHours(3), MeasuredFrom.TEST));
    }

    // IF Balanced kept Fast's draft time, THEN the reviewer's calls would be promised for free.
    @ParameterizedTest(name = "{0}: {1}–{2} min")
    @CsvSource({"FAST, 1, 10", "BALANCED, 5, 10", "MAX, 10, 20"})
    void of_draftOnly_scalesByTheDialAndRoundsToFiveMinutes(final QualityDial dial, final long low, final long high) {
        final StartEstimate estimate = StartEstimates.of(draft(3.0, MeasuredFrom.TEST), 100, 13, dial, 1, 0);

        assertThat(estimate)
                .isEqualTo(
                        new StartEstimate.Ranged(Duration.ofMinutes(low), Duration.ofMinutes(high), MeasuredFrom.TEST));
    }

    @ParameterizedTest
    @CsvSource({"3.0, 0"})
    void of_emptyBook_givesNoLine(final double speed, final int segments) {
        assertThat(StartEstimates.of(draft(speed, MeasuredFrom.RUN), segments, 0, QualityDial.FAST, 0, 0))
                .isEqualTo(new StartEstimate.None());
    }

    // The spec's measured case: 8,460 seconds read in whole minutes.
    @Test
    void lines_measured_wholeMinutesMeasuredOnThisModel() {
        final StartEstimate estimate = new StartEstimate.Measured(Duration.ofMinutes(141));

        assertThat(estimate.lines(EN))
                .containsExactly("For this book on this computer: about 2 h 21 min, measured on this model");
        assertThat(estimate.lines(UK))
                .containsExactly("Для цієї книжки на цьому комп’ютері: близько 2 год 21 хв, виміряно на цій моделі");
    }

    @Test
    void lines_rangeFromAnEarlierTranslation_saysWhere() {
        final StartEstimate estimate =
                new StartEstimate.Ranged(Duration.ofMinutes(5), Duration.ofMinutes(15), MeasuredFrom.RUN);

        assertThat(estimate.lines(EN))
                .containsExactly(
                        "For this book on this computer: about 5–15 min, estimate from an earlier translation");
        assertThat(estimate.lines(UK))
                .containsExactly(
                        "Для цієї книжки на цьому комп’ютері: близько 5–15 хв, оцінка за попереднім перекладом");
    }

    @Test
    void lines_rangeOfHours_fromTheModelTest() {
        final StartEstimate estimate =
                new StartEstimate.Ranged(Duration.ofHours(2), Duration.ofHours(3), MeasuredFrom.TEST);

        assertThat(estimate.lines(UK))
                .containsExactly("Для цієї книжки на цьому комп’ютері: близько 2–3 год, оцінка за перевіркою моделі");
    }
}
