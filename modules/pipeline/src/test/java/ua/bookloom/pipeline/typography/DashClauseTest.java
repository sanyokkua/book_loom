package ua.bookloom.pipeline.typography;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A dash clause is an optional comma, blanks, a dash and a blank; the scan stays linear on long blank runs. */
class DashClauseTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'I come — maybe' | 0 | 6",
                "'I come, — maybe' | 0 | 6",
                "'I come—maybe' | 0 | -1",
                "'I come —maybe' | 0 | -1",
                "'a — b — c' | 3 | 5",
                "'— start' | 0 | -1",
                "'' | 0 | -1"
            })
    void find_text_givesTheClauseStart(final String text, final int from, final int expected) {
        assertThat(DashClause.find(text, from)).isEqualTo(expected);
    }

    @Test
    void find_nonBreakingBlanksAndEnDash_areClauses() {
        assertThat(DashClause.find("x – y", 0)).isEqualTo(1);
    }

    @Test
    void find_longBlankRunWithoutDash_isLinear() {
        final String text = "a" + " ".repeat(300_000) + "b";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(DashClause.find(text, 0)).isEqualTo(-1));
    }
}
