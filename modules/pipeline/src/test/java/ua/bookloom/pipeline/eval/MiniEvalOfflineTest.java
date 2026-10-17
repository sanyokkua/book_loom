package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

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
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * The mini suite on the built-in pseudo model, which upper-cases its source: the whole flow (names pass, job,
 * consistency pass) runs on the invented EPUB, every block gets a decision and the report is written, and the
 * expectations a pseudo translation can meet are met.
 */
class MiniEvalOfflineTest {

    // The echo stores findings of no check these expectations name and keeps no brand, a material or a place out of the
    // glossary, so exactly the absence and optional-entry expectations hold.
    private static final List<String> PSEUDO_MET = List.of(
            "dialogue-sentences-m04",
            "dialogue-sentences-m25",
            "dialogue-sentences-m26",
            "title-names-m16",
            "term-names-m14",
            "term-names-m15",
            "evidence-woman-m03",
            "evidence-woman-m20",
            "evidence-woman-m21",
            "long-sentences",
            "address-mix-m25",
            "glossary-pell",
            "glossary-charrow",
            "glossary-almanac",
            "glossary-velmora-written",
            "glossary-dravik-written",
            "glossary-quorrel-written",
            "glossary-corvanium",
            "glossary-sedrafine",
            "glossary-no-sentence-starters");

    @TempDir
    private static Path workDir;

    private static MiniRun run;
    private static MiniReport report;

    @BeforeAll
    static void runPseudoBook() {
        run = new MiniRunner(MiniBook.load(), QualityDial.FAST, NamePolicy.TRANSLITERATE, null)
                .run(new PseudoChatModel(new ObjectMapper()), workDir);
        report = new MiniReport("pseudo", QualityDial.FAST.name(), run, MiniScorer.score(MiniExpectations.load(), run));
    }

    @Test
    void run_pseudoModel_completesAndDecidesEveryBlockOfTheBook() {
        assertThat(run.errors()).isEmpty();
        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(run.outcomes()).hasSize(MiniBook.load().blocks().size());
        assertThat(run.calls().names()).isPositive();
        assertThat(run.calls().translation()).isPositive();
    }

    @Test
    void score_pseudoModel_scoresEveryApplicableExpectationOnce() {
        final MiniExpectations expectations = MiniExpectations.load();
        final long other = expectations.segments().stream()
                        .filter(e -> !e.policies().isEmpty() && !e.policies().contains(NamePolicy.TRANSLITERATE))
                        .count()
                + expectations.glossary().stream()
                        .filter(e -> !e.policies().isEmpty() && !e.policies().contains(NamePolicy.TRANSLITERATE))
                        .count();

        assertThat(report.results()).hasSize(expectations.ids().size() - (int) other);
        assertThat(report.results()).extracting(MiniResult::id).doesNotHaveDuplicates();
    }

    @Test
    void score_pseudoModel_meetsTheExpectationsAnUppercasedEchoCanMeet() {
        assertThat(report.results())
                .filteredOn(MiniResult::passed)
                .extracting(MiniResult::id)
                .containsExactlyInAnyOrderElementsOf(PSEUDO_MET);
    }

    @Test
    void write_pseudoReport_landsAsJsonAndTableWithTheSuiteName(@TempDir final Path reports) throws IOException {
        EvalOutput.write(
                EvalProvenance.capture(MiniReport.SUITE, "pseudo", "TRANSLITERATE"),
                "pseudo-mini-TRANSLITERATE",
                report.table(),
                report.json(),
                reports,
                Optional.empty());

        final JsonNode json =
                new ObjectMapper().readTree(Files.readString(reports.resolve("pseudo-mini-TRANSLITERATE.json")));
        assertThat(json.get("suite").asText()).isEqualTo("mini");
        assertThat(json.get("expectations").asInt()).isEqualTo(report.results().size());
        assertThat(json.get("results")).hasSize(report.results().size());
        assertThat(Files.readString(reports.resolve("pseudo-mini-TRANSLITERATE.txt")))
                .contains("expectations");
    }
}
