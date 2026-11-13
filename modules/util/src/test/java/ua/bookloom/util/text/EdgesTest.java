package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Edge stripping removes only the listed characters at the two ends and stays linear on long runs. */
class EdgesTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"**abc** | * | abc", "a**b | * | a**b", "***** | * | ''", "abc | * | abc", "'' | * | ''"})
    void strip_listedCharacters_areRemovedAtBothEndsOnly(
            final String text, final String characters, final String expected) {
        assertThat(Edges.strip(text, characters)).isEqualTo(expected);
    }

    @Test
    void strip_longRunOfListedCharactersThenAnotherOne_isLinear() {
        final String text = "*".repeat(200_000) + "x" + "*".repeat(200_000) + "y";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThat(Edges.strip(text, "*")).hasSize(200_002));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Mr. Smith | Smith", "Smith | Smith", "Smith. | ''", "'' | ''", "ab cd-ef | cd-ef"})
    void trailingRun_letterOrHyphenRun_isTheEndOfTheText(final String text, final String expected) {
        assertThat(Edges.trailingRun(text, c -> Character.isLetter(c) || c == '-'))
                .isEqualTo(expected);
    }

    @Test
    void trailingRun_longRunBehindAMark_isLinear() {
        final String text = ("a".repeat(100_000) + ".").repeat(3) + "b".repeat(200_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(Edges.trailingRun(text, Character::isLetter)).hasSize(200_000));
    }
}
