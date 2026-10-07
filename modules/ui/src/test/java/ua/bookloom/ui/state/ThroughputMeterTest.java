package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.ModelCallFinished;

/** The pace the title bar and the dashboard show: the recent drafts, and the run's average over every call. */
class ThroughputMeterTest {

    private static ModelCallFinished call(final CallKind kind, final int tokens, final int seconds) {
        return new ModelCallFinished(
                "s1",
                kind,
                Duration.ofSeconds(seconds),
                new TokenUsage(100, tokens, Duration.ofSeconds(seconds), null, null),
                10,
                false,
                List.of("s1"),
                1,
                null);
    }

    // IF a review's short answer counted toward the draft pace, THEN a slow model would look fast.
    @Test
    void tokensPerSecond_reviewCall_isNotCounted() {
        final ThroughputMeter meter = new ThroughputMeter();
        meter.finished(call(CallKind.DRAFT, 200, 10));
        meter.finished(call(CallKind.REVIEW, 400, 10));

        assertThat(meter.tokensPerSecond()).isCloseTo(20.0, within(0.001));
    }

    // IF the average left out the reviews, THEN it would not be the speed the model really wrote at over the run.
    @Test
    void averageTokensPerSecond_everyKindOfCall_isCountedOverTheWholeRun() {
        final ThroughputMeter meter = new ThroughputMeter();
        meter.finished(call(CallKind.DRAFT, 200, 10));
        meter.finished(call(CallKind.REVIEW, 100, 10));

        assertThat(meter.averageTokensPerSecond()).isCloseTo(15.0, within(0.001));
        assertThat(meter.snapshot(null, Duration.ofSeconds(30)).averageTokensPerSecond())
                .isCloseTo(15.0, within(0.001));
    }

    @Test
    void averageTokensPerSecond_noCallYet_isUnknown() {
        assertThat(new ThroughputMeter().averageTokensPerSecond()).isNull();
    }
}
