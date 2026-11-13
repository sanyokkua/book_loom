package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A glossary line of a prompt splits into term, rendering and note, and a long line is read promptly. */
class PromptTermRowTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "Hall → Зала~Hall~Зала~''",
                "Hall → Зала (place)~Hall~Зала~place",
                "Hall → Зала (a) (b)~Hall~Зала~a) (b",
                "Hall → Зала ()~Hall~Зала~''",
                "Hall → Зала (open~Hall~Зала (open~''",
                "Hall → Хол → Зала~Hall~Хол → Зала~''",
                "Hall →~Hall →~''~''",
                "Hall → ~Hall →~''~''",
                "plain line~plain line~''~''"
            })
    void termRow_line_splitsIntoThreeCells(
            final String line, final String term, final String rendering, final String note) {
        assertThat(PromptParts.termRow(line.strip())).isEqualTo(List.of(term.strip(), rendering, note));
    }

    @Test
    void termRow_longLineWithManyArrowsAndBrackets_isPrompt() {
        final String line = "a" + " → (".repeat(100_000) + ")";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(PromptParts.termRow(line)).hasSize(3));
    }
}
