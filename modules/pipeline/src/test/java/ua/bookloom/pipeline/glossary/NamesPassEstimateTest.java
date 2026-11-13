package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.pipeline.NamesPassReport;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;

/**
 * The estimate a person sees before the names pass counts every call the pass sends (15i review): the names reading,
 * the batched review, the recurring terms and the renderings, not the names reading alone. The book is invented.
 */
class NamesPassEstimateTest extends NamesPassTestBase {

    // IF the estimate counted the names reading only, THEN the pass would send more calls than the person was told.
    @Test
    void estimate_seededBook_coversEveryCallThePassSends() {
        seeded("uk", NamePolicy.TRANSLITERATE);

        final NamesEstimate estimate = Objects.requireNonNull(
                fixture.pass().estimate(PROJECT, Map.of()).data());
        final NamesPassReport report = succeeded(run(model()));

        assertThat(estimate.calls()).isGreaterThanOrEqualTo(report.calls());
        assertThat(estimate.minutesHigh()).isPositive();
    }

    // IF the estimate stayed at the built-in figures with seconds measured on the model, THEN a slow model would be
    // told a tenth of its real time.
    @Test
    void estimate_withStoredSeconds_sumsCallsTimesSecondsAndSaysMeasured() {
        final NamesEstimate estimate = NamesPassEstimate.time(
                QualityDial.BALANCED,
                Map.of(CallKind.DRAFT, 3, CallKind.TERM_READING, 2),
                Map.of(
                        CallKind.DRAFT, new MeasuredSpeed(4.0, MeasuredFrom.RUN),
                        CallKind.TERM_READING, new MeasuredSpeed(10.0, MeasuredFrom.RUN)));

        assertThat(estimate)
                .isEqualTo(new NamesEstimate(5, Map.of(CallKind.DRAFT, 3, CallKind.TERM_READING, 2), 1, 1, true));
    }

    @Test
    void estimate_storedSecondsOfTheSpecPlan_isTwoMinutesMeasured() {
        final NamesEstimate estimate = NamesPassEstimate.time(
                QualityDial.BALANCED,
                Map.of(CallKind.TERM_READING, 5, CallKind.NAME_KNOWLEDGE, 1, CallKind.SUGGEST_TARGETS, 1),
                Map.of(
                        CallKind.TERM_READING, new MeasuredSpeed(15.0, MeasuredFrom.RUN),
                        CallKind.NAME_KNOWLEDGE, new MeasuredSpeed(15.0, MeasuredFrom.RUN),
                        CallKind.SUGGEST_TARGETS, new MeasuredSpeed(30.0, MeasuredFrom.RUN)));

        assertThat(estimate.minutesLow()).isEqualTo(2);
        assertThat(estimate.minutesHigh()).isEqualTo(2);
        assertThat(estimate.measured()).isTrue();
    }

    // IF one kind of call had no stored seconds, THEN calling the sum measured would hide a guess.
    @Test
    void estimate_withoutStoredSeconds_saysEstimate() {
        final NamesEstimate estimate = NamesPassEstimate.time(
                QualityDial.BALANCED,
                Map.of(CallKind.TERM_READING, 2, CallKind.SUGGEST_TARGETS, 1),
                Map.of(CallKind.TERM_READING, new MeasuredSpeed(10.0, MeasuredFrom.RUN)));

        assertThat(estimate.measured()).isFalse();
    }

    @Test
    void estimate_nothingMeasured_showsTheBuiltInRange() {
        final NamesEstimate estimate =
                NamesPassEstimate.time(QualityDial.BALANCED, Map.of(CallKind.TERM_READING, 15), Map.of());

        assertThat(estimate).isEqualTo(new NamesEstimate(15, Map.of(CallKind.TERM_READING, 15), 1, 2, false));
    }

    // A model twice as slow per segment as the reference doubles the built-in per-call times.
    @Test
    void estimate_measuredSpeedPerSegmentOnly_scalesTheBuiltInTimes() {
        final NamesEstimate estimate = NamesPassEstimate.time(
                QualityDial.BALANCED,
                Map.of(CallKind.TERM_READING, 15),
                Map.of(CallKind.DRAFT, new MeasuredSpeed(6.88, MeasuredFrom.TEST)));

        assertThat(estimate).isEqualTo(new NamesEstimate(15, Map.of(CallKind.TERM_READING, 15), 2, 3, false));
    }
}
