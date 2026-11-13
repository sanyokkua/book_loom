package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A paradigm row reads as ending, gender, vocative flag and endings, and a long word stays linear. */
class ParadigmRowTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "o|m: а, у, ом~o~m~false~а, у, ом",
                "a|f>voc:   о~a~f~true~о",
                "C|any:x~C~any~false~x",
                "a|b|m: x~a|b~m~false~x"
            })
    void parse_wellFormedRow_givesItsParts(
            final String line, final String ending, final String gender, final boolean vocative, final String endings) {
        final var row = ParadigmRow.parse(line).orElseThrow();

        assertThat(row).isEqualTo(new ParadigmRow(ending, gender, vocative, endings));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {"o|x: а", "|m: а", "o|m:", "o m: а", "o|m", "plain"})
    void parse_otherShapes_giveNothing(final String line) {
        assertThat(ParadigmRow.parse(line)).isEmpty();
    }

    @Test
    void parse_lineBreakInTheEndings_givesNothing() {
        assertThat(ParadigmRow.parse("o|m: а\nб")).isEmpty();
    }

    @Test
    void parse_longWordOfBars_isLinear() {
        final String line = "|".repeat(200_000) + "x";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> assertThat(ParadigmRow.parse(line)).isEmpty());
    }
}
