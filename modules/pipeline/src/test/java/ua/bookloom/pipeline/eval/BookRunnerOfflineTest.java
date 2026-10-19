package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * The whole-book runner on the built-in pseudo model, which upper-cases its source: every stage a typical user passes
 * through runs on the invented EPUB, both exports are written beside the glossary CSV and the report, and the counts
 * file holds values a pseudo run can meet.
 */
class BookRunnerOfflineTest {

    @TempDir
    private static Path workDir;

    private static Path outDir;
    private static Path book;
    private static BookRunSummary summary;
    private static JsonNode json;

    @BeforeAll
    static void runPseudoBook() throws IOException {
        book = MiniBook.load().writeEpub(workDir);
        outDir = Files.createDirectory(workDir.resolve("out"));
        summary = new BookRunner(new BookRunner.Options(book, outDir, "en", "uk", QualityDial.FAST, null))
                .run(new PseudoChatModel(new ObjectMapper()), "pseudo");
        json = new ObjectMapper().readTree(Files.readString(outDir.resolve("book-run.json")));
    }

    @Test
    void run_pseudoModel_writesBothExportsThatOpenAsBooks() {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);

        final Document without = ExportedBooks.open(documents, outDir.resolve("mini-book.no_consistency_pass.epub"));
        final Document with = ExportedBooks.open(documents, outDir.resolve("mini-book.with_consistency_pass.epub"));

        assertThat(ExportedBooks.sourceCount(without)).isEqualTo(34);
        assertThat(ExportedBooks.sourceCount(with)).isEqualTo(34);
    }

    @Test
    void run_pseudoModel_writesTheGlossaryCsvAndTheReportOfBothExports() {
        assertThat(outDir.resolve("mini-book.no_consistency_pass.glossary.csv")).isRegularFile();
        assertThat(outDir.resolve("mini-book.with_consistency_pass.glossary.csv"))
                .isRegularFile();
        assertThat(outDir.resolve("mini-book.no_consistency_pass.report.md")).isRegularFile();
        assertThat(outDir.resolve("mini-book.with_consistency_pass.report.md")).isRegularFile();
    }

    @ParameterizedTest
    @CsvSource({
        "/dial,FAST",
        "/end,COMPLETED",
        "/brief/suggested,true",
        "/brief/names,TRANSLITERATE",
        "/brief/narratorGender,UNKNOWN",
        "/segments,34",
        "/flaggedSegments,26",
        "/calls/briefSuggestion,2",
        "/calls/names,2",
        "/characters/verified,0",
        "/characters/suggested,0",
        "/characters/none,0",
        "/noConsistencyPass/aligned,34",
        "/noConsistencyPass/unaligned,0",
        "/noConsistencyPass/latinWords,676",
        "/noConsistencyPass/alphabetWords,0",
        "/noConsistencyPass/awaitingGenderSegments,0",
        "/noConsistencyPass/detectors/echo,34",
        "/withConsistencyPass/aligned,34",
        "/withConsistencyPass/latinWords,676",
        "/withConsistencyPass/detectors/script,26"
    })
    void run_pseudoModel_holdsTheCountsOfTheRun(final String pointer, final String expected) {
        assertThat(json.at(pointer).asText()).as(pointer).isEqualTo(expected);
    }

    @Test
    void run_pseudoModel_reportsNoErrorAndCountsEveryStagesCalls() {
        assertThat(json.get("errors")).isEmpty();
        assertThat(summary.errors()).isEmpty();
        assertThat(summary.calls().translation()).isPositive();
        assertThat(summary.calls().consistencyPass()).isPositive();
        assertThat(summary.minutes()).isNotNegative();
    }
}
