package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A sentence opens at the start or after a stop mark with its closers and the opening marks and blanks that follow. */
class SentenceOpeningTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "''~true",
                "'  “‘ ('~true",
                "'He left. '~true",
                "'He left.” «'~true",
                "'Really?!\") – '~false",
                "'She said \"'~false",
                "'He left'~false",
                "'He left, '~false",
                "'Wait… '~true",
                "'a.\"\"\" '~true"
            })
    void endsAtOpening_text_tellsWhetherASentenceOpens(final String text, final boolean expected) {
        assertThat(SentenceOpening.endsAtOpening(text)).isEqualTo(expected);
    }

    @Test
    void endsAtOpening_longRunOfQuotesAndBlanks_isLinear() {
        final String text = "x" + "\" ".repeat(200_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(SentenceOpening.endsAtOpening(text)).isFalse());
    }
}
