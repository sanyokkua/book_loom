package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalReportTest {

    private static final List<DefectRow> ROWS = List.of(
            new DefectRow("a", "quotes", true, true, true, true, 1, 0),
            new DefectRow("b", "quotes", true, false, true, true, 1, 0),
            new DefectRow("c", "gender", false, false, true, true, 1, 0));

    @Test
    void table_corpusRows_endWithTheShareRightPerKind() {
        final EvalReport report = new EvalReport("gemma4:e4b", List.of(), ROWS, "language");

        assertThat(report.table()).contains("corpus by kind  quotes 50%  gender 100%");
    }

    @Test
    void json_corpusRows_carryTheShareRightPerKind() {
        final EvalReport report = new EvalReport("gemma4:e4b", List.of(), ROWS, "language");

        assertThat(report.json()).contains("\"byKind\":{\"quotes\":0.500,\"gender\":1.000}");
    }

    @Test
    void kindSummary_noCorpusRun_isEmpty() {
        assertThat(new EvalReport("gemma4:e4b", List.of()).kindSummary()).isEmpty();
    }
}
