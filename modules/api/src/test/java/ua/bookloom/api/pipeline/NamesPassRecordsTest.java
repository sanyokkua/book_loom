package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;

/** What the records of the names pass accept (15i.N8): honest counts, a range that does not end before it starts. */
class NamesPassRecordsTest {

    @Test
    void estimate_validRange_keepsItsParts() {
        final NamesEstimate estimate = new NamesEstimate(5, 1, 2);

        assertThat(estimate)
                .extracting(NamesEstimate::calls, NamesEstimate::minutesLow, NamesEstimate::minutesHigh)
                .containsExactly(5, 1, 2);
    }

    @Test
    void estimate_noCalls_isAllowed() {
        assertThat(new NamesEstimate(0, 0, 0).calls()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "1, -1, 0", "1, 3, 2"})
    void estimate_negativeOrBackwardsRange_isRefused(final int calls, final int low, final int high) {
        assertThatThrownBy(() -> new NamesEstimate(calls, low, high)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void report_validParts_keepsThem() {
        final NamesPassReport report =
                new NamesPassReport(EntryChanges.none(), EntryChanges.none(), 7, Duration.ofSeconds(14));

        assertThat(report)
                .extracting(NamesPassReport::calls, NamesPassReport::elapsed)
                .containsExactly(7, Duration.ofSeconds(14));
    }

    @Test
    void report_negativeCalls_isRefused() {
        assertThatThrownBy(() -> new NamesPassReport(EntryChanges.none(), EntryChanges.none(), -1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void report_negativeElapsed_isRefused() {
        assertThatThrownBy(
                        () -> new NamesPassReport(EntryChanges.none(), EntryChanges.none(), 0, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void report_missingPart_isRefused() {
        final EntryChanges<GlossaryEntry> names = EntryChanges.none();
        final EntryChanges<LexiconEntry> terms = EntryChanges.none();

        assertThatNullPointerException().isThrownBy(() -> new NamesPassReport(null, terms, 0, Duration.ZERO));
        assertThatNullPointerException().isThrownBy(() -> new NamesPassReport(names, null, 0, Duration.ZERO));
        assertThatNullPointerException().isThrownBy(() -> new NamesPassReport(names, terms, 0, null));
    }
}
