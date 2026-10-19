package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.glossary.TermReading;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The coreference probe (15i.E2) read by the production term reading (15i.N4) against a real local model: every
 * invented window of {@code eval/coref/windows.json} is one {@code term-reading} call, and only the votes the reading
 * keeps are scored. Local-only: {@code scripts/eval-matrix.sh --suite coref}, or {@code BOOKLOOM_EVAL_SUITE=coref
 * ./gradlew :pipeline:promptEval} with {@code BOOKLOOM_EVAL_URL} and optionally {@code BOOKLOOM_EVAL_PROVIDER} and
 * {@code BOOKLOOM_EVAL_MODEL}. It measures and asserts no floor; the gate (at most 5 % wrong votes, else that model's
 * votes only break ties) is read from the report in {@code build/reports/promptEval/<model>-coref.json} and
 * {@code .txt}.
 */
@Slf4j
@Tag("promptEval")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_EVAL_SUITE", matches = "coref")
class CorefEvalTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    @Test
    void coref_realModel_reportsRightWrongAndAbstainedVotes() throws IOException {
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final List<CorefWindow> windows = CorefCorpus.windows();
        final CorefReader reader = new TermReadingCorefReader(
                new TermReading(new PromptTemplates(), new ObjectMapper()), FRAME, PromptEvalTest.calls(model));

        final CorefReport report = CorefScorer.score(windows, reader);

        final String name = model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + CorefReport.SUITE;
        EvalOutput.write(
                EvalProvenance.capture(CorefReport.SUITE, model, ""), name, report.table(model), report.json(model));
        log.info("Coref eval report {}\n{}", name, report.table(model));
        assertThat(report.rows()).hasSize(windows.size());
    }
}
