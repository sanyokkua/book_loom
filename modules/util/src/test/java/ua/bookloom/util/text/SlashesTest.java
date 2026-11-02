package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Slash trimming keeps the inner slashes and stays linear on long runs. */
class SlashesTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"http://h/v1/// | http://h/v1", "/ | ''", "a/b | a/b", "'' | ''", "//a// | //a"})
    void trimTrailing_variousTexts_removesOnlyTheEnd(final String text, final String expected) {
        assertThat(Slashes.trimTrailing(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"///v1/chat | v1/chat", "/ | ''", "a/b | a/b", "'' | ''", "//a// | a//"})
    void trimLeading_variousTexts_removesOnlyTheStart(final String text, final String expected) {
        assertThat(Slashes.trimLeading(text)).isEqualTo(expected);
    }

    @Test
    void trimTrailing_longRunOfSlashesBeforeAnotherCharacter_isLinear() {
        final String text = "/".repeat(200_000) + "x";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(Slashes.trimTrailing(text)).isEqualTo(text));
    }
}
