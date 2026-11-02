package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** How a frontmatter line splits into a key and a value, and that no line makes the split slow. */
class FrontmatterScalarsTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"title: Dawn | title | text", "title :   Dawn   | title | text", "empty: | empty | empty"})
    void scan_keyValueLine_readsTheKeyAndClassifiesTheValue(final String line, final String key, final String kind) {
        final List<FrontmatterScalars.Scalar> found = FrontmatterScalars.scan(block(line));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().key()).isEqualTo(key);
        assertThat(found.getFirst().isText()).isEqualTo("text".equals(kind));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"title:Dawn", "no colon here", "# comment: x", "- item: x"})
    void scan_lineThatIsNoPair_isSkipped(final String line) {
        assertThat(FrontmatterScalars.scan(block(line))).isEmpty();
    }

    private static String block(final String line) {
        return "---\n" + line + "\n---\n";
    }

    @Test
    void scan_longLineOfBlanksAndColons_finishesPromptly() {
        final String line = "k" + " ".repeat(100_000) + ":" + " \t".repeat(50_000) + "x";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(FrontmatterScalars.scan(block(line))).hasSize(1));
    }
}
