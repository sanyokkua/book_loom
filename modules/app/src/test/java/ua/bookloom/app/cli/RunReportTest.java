package ua.bookloom.app.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.RenderingConsistency;

/** The report's lexicon section and its export section's Proofread figures. */
class RunReportTest {

    @TempDir
    private Path tempDir;

    @Test
    void lexicon_learnedAndReportedTerms_writesBothFigures() throws IOException {
        final Path book = Files.writeString(tempDir.resolve("Book.txt"), "One.");
        final RunReport report = new RunReport(
                Objects.requireNonNull(
                        TranslateArguments.parse(List.of(book.toString())).data()),
                tempDir.resolve("out.txt"),
                Instant.EPOCH);
        report.lexicon(RenderingConsistency.of(List.of(
                LexiconEntry.of("p1", "master").withLearned(new LexiconEntry.Learned("господар", 9, 10)),
                LexiconEntry.of("p1", "imp").seen("біс").seen("біс"),
                LexiconEntry.of("p1", "Mr"))));

        final Path file = tempDir.resolve("report.json");
        report.write(file, Instant.EPOCH, 0);

        final JsonNode lexicon = new ObjectMapper().readTree(file.toFile()).get("lexicon");
        assertThat(lexicon.get("terms").asInt()).isEqualTo(3);
        assertThat(lexicon.get("learned").asInt()).isEqualTo(1);
        assertThat(lexicon.get("learnedCoverage").asDouble()).isEqualTo(0.9);
        assertThat(lexicon.get("distinctRenderingsPerTerm").asDouble()).isEqualTo(1.0);
    }

    @Test
    void export_proofreadSummary_writesReReadAndWaiting() throws IOException {
        final Path book = Files.writeString(tempDir.resolve("Book.txt"), "One.");
        final RunReport report = new RunReport(
                Objects.requireNonNull(
                        TranslateArguments.parse(List.of(book.toString())).data()),
                tempDir.resolve("out.txt"),
                Instant.EPOCH);
        report.export(
                new ExportReport(
                        tempDir.resolve("out.txt"),
                        5,
                        0,
                        0,
                        0,
                        5,
                        0,
                        List.of(),
                        5,
                        new ProofreadSummary(4, 2),
                        0,
                        List.of(),
                        List.of()),
                false);

        final Path file = tempDir.resolve("report.json");
        report.write(file, Instant.EPOCH, 0);

        final JsonNode export = new ObjectMapper().readTree(file.toFile()).get("export");
        assertThat(export.get("proofreadReRead").asInt()).isEqualTo(4);
        assertThat(export.get("proofreadWaiting").asInt()).isEqualTo(2);
    }
}
