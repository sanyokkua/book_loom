package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.Messages;

/** The timing figures of a call as the window words them: a figure the engine did not report is a dash. */
class CallTimingsTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);

    private static TokenUsage usage(final @Nullable Long firstTokenMs, final @Nullable Integer cached) {
        return new TokenUsage(
                600,
                50,
                Duration.ofSeconds(2),
                firstTokenMs == null ? null : Duration.ofMillis(firstTokenMs),
                cached,
                null);
    }

    private static CallSnapshot call(final TokenUsage usage) {
        return CallSnapshot.waiting(1, CallKind.DRAFT, "draft", null, List.of(), List.of(), Instant.EPOCH, 1, 1, null)
                .answered("{}", usage, Duration.ofSeconds(3));
    }

    // IF a missing first-token time read 0 ms, THEN a server that reports none would look instant.
    @Test
    void firstToken_notReported_isTheDash() {
        assertThat(CallTimings.firstToken(EN, usage(null, null))).isEqualTo("–");
        assertThat(CallTimings.firstToken(EN, null)).isEqualTo("–");
    }

    @Test
    void firstToken_reported_isTheTime() {
        assertThat(CallTimings.firstToken(EN, usage(210L, null))).isEqualTo("210 ms");
    }

    @Test
    void speed_notReported_isTheDash() {
        assertThat(CallTimings.speed(EN, new TokenUsage(10, 20, null))).isEqualTo("–");
        assertThat(CallTimings.speed(EN, usage(null, null))).isEqualTo("25.0 tok/s");
    }

    @ParameterizedTest
    @CsvSource({"600,200,33", "600,0,0", "3,2,67", "600,600,100"})
    void cachedShare_reported_isTheRoundedPercent(final int prompt, final int cached, final int expected) {
        assertThat(CallTimings.cachedShare(prompt, cached)).isEqualTo(expected);
    }

    @Test
    void cachedShare_nothingToDivideOrNotReported_hasNoShare() {
        assertThat(CallTimings.cachedShare(0, 0)).isNull();
        assertThat(CallTimings.cachedShare(null, 5)).isNull();
        assertThat(CallTimings.cachedShare(600, null)).isNull();
    }

    @Test
    void cached_notReported_isTheDash() {
        assertThat(CallTimings.cached(EN, usage(100L, null))).isEqualTo("–");
        assertThat(CallTimings.cached(EN, usage(100L, 200))).isEqualTo("33 %");
    }

    // IF a missing figure counted as 0, THEN the average of 200, 400 and nothing would read 200 ms.
    @Test
    void meanFirstToken_oneCallWithoutTheFigure_isLeftOut() {
        final var calls = List.of(call(usage(200L, null)), call(usage(400L, null)), call(usage(null, null)));

        assertThat(CallTimings.meanFirstToken(calls)).contains(Duration.ofMillis(300));
    }

    @Test
    void meanFirstToken_noCallReported_isEmpty() {
        assertThat(CallTimings.meanFirstToken(List.of(call(usage(null, null))))).isEmpty();
    }
}
