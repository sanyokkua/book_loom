package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * The article eval on the built-in pseudo model, which upper-cases its source: the whole article runs, is exported and
 * is written as a complete version-2 report for the {@code article-gravity} dataset, and the protected-span measure
 * reads the export.
 */
class ArticleEvalOfflineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    private static Path workDir;

    private static ArticleRun run;
    private static JsonNode json;

    @BeforeAll
    static void runPseudoArticle() throws IOException {
        final Path repeat = Files.createDirectories(workDir.resolve("repeat"));
        final EvalRepeats.Runs<ArticleRun> runs = new EvalRepeats(1)
                .run(
                        new PseudoChatModel(MAPPER),
                        seeded -> new ArticleEval(QualityDial.FAST, null, "uk").run(seeded, repeat),
                        ArticleRun::flaggedRate);
        run = runs.results().get(0);
        final ArticleReport report = new ArticleReport("pseudo", QualityDial.FAST.name(), run);
        final Path reports = Files.createDirectories(workDir.resolve("reports"));
        EvalOutput.write(
                EvalProvenance.capture(ArticleReport.SUITE, "pseudo", report.label())
                        .withDataset(ArticleGravity.ID)
                        .withAggregates(runs.aggregates()),
                "pseudo-article",
                report.table(),
                report.json(),
                reports,
                Optional.empty());
        json = MAPPER.readTree(Files.readString(reports.resolve("pseudo-article.json")));
    }

    @Test
    void run_pseudoModel_completesAndDecidesEverySegment() {
        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(run.segments()).isEqualTo(152);
        assertThat(run.outputs()).hasSize(152);
        assertThat(run.flagged()).isEqualTo(102);
    }

    // The pseudo model upper-cases what it is sent, yet nothing is lost: the URLs and DOIs reach it only as placeholder
    // tokens (a link or a bare URL is masked) and the fenced code blocks are no segments at all, so the export restores
    // them from the source. A real model is measured on the same spans by ArticleEvalTest.
    @Test
    void run_pseudoModel_keepsTheProtectedSpans() {
        assertThat(run.lostSpans()).isEqualTo(List.of());
        assertThat(run.keptShare()).isEqualTo(1.0);
    }

    @Test
    void report_pseudoRun_isACompleteVersionTwoReportOfTheArticleDataset() {
        final JsonNode provenance = json.get("provenance");

        assertThat(provenance.get("version").asInt()).isEqualTo(2);
        assertThat(provenance.get("suite").asText()).isEqualTo("article");
        assertThat(provenance.at("/dataset/id").asText()).isEqualTo("article-gravity");
        assertThat(provenance.at("/dataset/pair").asText()).isEqualTo("en-uk+en-ru+en-fr");
        assertThat(provenance.at("/dataset/ownerData").asBoolean()).isFalse();
        assertThat(provenance.at("/dataset/rootHash").asText())
                .isEqualTo("0499bbe12ee7bdfb534882bfce8f5ac0f8e3fe68f6e88fe8c5afa05b2fdb6620");
        assertThat(provenance.at("/repeats/k").asInt()).isEqualTo(1);
        assertThat(provenance.get("aggregates")).hasSize(1);
        assertThat(provenance.get("label").asText()).isEqualTo("en-uk-fast");
    }

    @Test
    void report_pseudoRun_carriesTheRateTheSpansAndEveryOutput() {
        assertThat(json.get("suite").asText()).isEqualTo("article");
        assertThat(json.get("target").asText()).isEqualTo("uk");
        assertThat(json.get("flaggedRate").asDouble()).isCloseTo(102.0 / 152, within(0.0001));
        assertThat(json.get("protectedSpansKept").asDouble()).isEqualTo(1.0);
        assertThat(json.get("outputs")).hasSize(152);
    }

    @Test
    void new_targetOutsideTheDataset_isRefused() {
        assertThatThrownBy(() -> new ArticleEval(QualityDial.FAST, null, "de"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de");
    }
}
