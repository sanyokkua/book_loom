package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A word joins parts by apostrophes and hyphens, leaves a trailing possessive out, and long texts stay linear. */
class WordScanTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "--Ashdown's tower~2~9~11",
                "o'clock now~0~7~7",
                "mother-in-law's~0~13~15",
                "Ashdown’sx and~0~10~10",
                "rock'n'roll~0~11~11",
                "Smith's~0~5~7",
                "end-~0~3~3",
                "it's's~0~2~4",
                "ab-'cd~0~2~2"
            })
    void next_text_givesTheWordAndItsPossessiveEnd(
            final String text, final int from, final int wordEnd, final int end) {
        final WordScan scan = Objects.requireNonNull(WordScan.next(text, from));

        assertThat(scan.wordEnd()).isEqualTo(wordEnd);
        assertThat(scan.end()).isEqualTo(end);
    }

    @Test
    void next_noLetterLeft_givesNull() {
        assertThat(WordScan.next("... —", 0)).isNull();
    }

    @Test
    void next_longChainOfHyphenatedParts_isLinear() {
        final String text = "ab-".repeat(200_000) + "c";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(WordScan.next(text, 0)).isEqualTo(new WordScan(0, text.length(), text.length())));
    }
}
