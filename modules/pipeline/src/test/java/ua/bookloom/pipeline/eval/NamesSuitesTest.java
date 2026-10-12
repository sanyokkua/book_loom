package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** The names suite's selection of books and its report (15i.E1), offline over the scripted model. */
class NamesSuitesTest {

    @TempDir
    private Path dir;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"all", " all "})
    void books_unsetOrAll_isEveryCommittedBook(final String spec) {
        assertThat(NamesSuites.books(spec)).extracting(NamesBook::id).containsExactlyElementsOf(NamesBook.IDS);
    }

    @Test
    void books_oneId_isThatBook() {
        assertThat(NamesSuites.books("mayday")).extracting(NamesBook::id).containsExactly("mayday");
    }

    @Test
    void books_unknownId_isRejectedWithTheChoices() {
        assertThatThrownBy(() -> NamesSuites.books("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOKLOOM_EVAL_BOOK")
                .hasMessageContaining("mayday");
    }

    @Test
    void run_oneCommittedBookAndAGoldFile_reportsBothRowsAndTheReportNamesThem() throws IOException {
        final NamesBook book = NamesBook.byId("stray-he");
        final Path corpus = Files.createDirectories(dir.resolve("corpus"));
        final Path file = book.write(corpus);
        Files.writeString(
                corpus.resolve("own.gold.json"),
                "{\"book\":\"" + file.getFileName() + "\",\"sourceLanguage\":\"en\",\"targetLanguage\":\"uk\","
                        + "\"brief\":{\"register\":\"NEUTRAL\",\"narrator\":\"THIRD\",\"narratorGender\":\"UNKNOWN\"},"
                        + "\"characters\":[{\"term\":\"Perpetua\",\"type\":\"CHARACTER\",\"gender\":\"FEMALE\"}]}");

        final NamesReport report = NamesSuites.run(
                "fake",
                new NamesFakeModel(book),
                dir.resolve("work"),
                Map.of(NamesSuites.BOOK_ENV, "stray-he", NamesSuites.CORPUS_ENV, corpus.toString()));

        assertThat(report.rows()).extracting(NamesRow::book).containsExactly("stray-he", "own");
        assertThat(report.rows()).allSatisfy(row -> assertThat(row.error()).isNull());
        assertThat(report.rows().get(1).score().eligible()).isPositive();
        assertThat(report.json())
                .startsWith("{\"suite\":\"names\",\"model\":\"fake\",\"books\":[{\"book\":\"stray-he\"");
        assertThat(report.table()).contains("stray-he").contains("own");
    }

    @Test
    void report_failedBook_isAnErrorLineAndAnErrorInTheJsonWithNoScore() {
        final NamesReport report = new NamesReport("m", List.of(new NamesRow("mayday", null, "unreachable")));

        assertThat(report.json())
                .isEqualTo(
                        "{\"suite\":\"names\",\"model\":\"m\",\"books\":[{\"book\":\"mayday\",\"error\":\"unreachable\"}]}");
        assertThat(report.table()).contains("mayday").contains("error unreachable");
    }
}
