package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * The acceptance test of the eval harness v2: a dry run of the mini book on the pseudo model writes a complete
 * version-2 report whose dataset root hash is the one the dataset's own manifest states.
 */
class EvalDryRunTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MANIFEST = "/eval/datasets/mini-en/manifest.json";

    @Test
    void dryRun_pseudoProvider_writesCompleteVersionTwoReportWithManifestRootHash(@TempDir final Path reports)
            throws IOException {
        writeDryReport(reports);

        final JsonNode json = MAPPER.readTree(Files.readString(reports.resolve("pseudo-mini-dry.json")));
        final JsonNode provenance = json.get("provenance");
        assertThat(provenance.get("version").asInt()).isEqualTo(2);
        assertThat(provenance.at("/dataset/id").asText()).isEqualTo("mini-en");
        assertThat(provenance.at("/dataset/pair").asText()).isEqualTo("en-uk");
        assertThat(provenance.at("/dataset/rootHash").asText()).isEqualTo(manifestRootHash());
        assertThat(provenance.at("/judge/mode").asText()).isEqualTo("none");
        assertThat(provenance.at("/repeats/k").asInt()).isEqualTo(1);
        assertThat(provenance.at("/repeats/seeds")).hasToString("[1000]");
        assertThat(provenance.has("model")).isTrue();
        assertThat(provenance.get("noiseBand").isNull()).isTrue();
        assertThat(provenance.get("comparison").isNull()).isTrue();
        assertThat(json.get("cases")).isNotEmpty().allSatisfy(c -> {
            assertThat(c.has("graders")).isTrue();
            assertThat(c.has("time")).isTrue();
            assertThat(c.has("tokens")).isTrue();
            assertThat(c.at("/time/total").isNull()).isTrue();
        });
    }

    private static void writeDryReport(final Path reports) throws IOException {
        final MiniRun run = new MiniRunner(
                        MiniBook.load(MiniBook.MINI_EN), QualityDial.FAST, NamePolicy.TRANSLITERATE, null)
                .run(new PseudoChatModel(MAPPER), Files.createDirectories(reports.resolve("work")));
        final MiniReport report = new MiniReport(
                "pseudo", QualityDial.FAST.name(), run, MiniScorer.score(MiniExpectations.load(MiniBook.MINI_EN), run));

        EvalOutput.write(
                EvalProvenance.capture(MiniReport.SUITE, "pseudo", "dry"),
                "pseudo-mini-dry",
                report.table(),
                report.json(),
                reports,
                Optional.empty());
    }

    private static String manifestRootHash() throws IOException {
        try (InputStream in = EvalDryRunTest.class.getResourceAsStream(MANIFEST)) {
            assertThat(in).as("manifest " + MANIFEST).isNotNull();
            return MAPPER.readTree(in).get("rootHash").asText();
        }
    }
}
