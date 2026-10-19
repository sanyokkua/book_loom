package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.eval.RealRunRow.Outcome;

class RealRunReportTest {

    private static RealRunRow row(final String id, final String kind, final String call, final Outcome outcome) {
        return new RealRunRow(id, kind, call, outcome, true, false, 0, 0, 0, "d");
    }

    @Test
    void cells_groupByKindAndCall_andKeepKnownFailuresOutOfTheRate() {
        final RealRunReport report = new RealRunReport(
                "m",
                List.of(
                        row("a", "quotes", "draft", Outcome.PASS),
                        row("b", "quotes", "draft", Outcome.FAIL),
                        row("c", "quotes", "draft", Outcome.KNOWN_RED),
                        row("d", "short-line", "review", Outcome.KNOWN_GREEN)));

        assertThat(report.cells())
                .containsExactly(
                        new RealRunReport.Cell("quotes/draft", 1, 2, 1, 0),
                        new RealRunReport.Cell("short-line/review", 0, 0, 1, 1));
        assertThat(report.cells().getFirst().rate()).isEqualTo(0.5);
        assertThat(report.cells().getLast().rate()).isEqualTo(1.0);
    }

    @Test
    void metrics_batchAndReviewerRows_sumTheirCounts() {
        final RealRunReport report = new RealRunReport(
                "m",
                List.of(
                        new RealRunRow("b1", "batch-terms", "batch", Outcome.FAIL, true, false, 4, 1, 2, "d"),
                        new RealRunRow("b2", "batch-terms", "batch", Outcome.PASS, true, false, 4, 0, 0, "d"),
                        new RealRunRow("r#1", "reviewer-long", "review-batch", Outcome.PASS, false, true, 0, 0, 0, "d"),
                        new RealRunRow(
                                "r#2", "reviewer-long", "review-batch", Outcome.PASS, true, false, 0, 0, 0, "d")));

        assertThat(report.tooShortRate()).isEqualTo(0.125);
        assertThat(report.leakedRate()).isEqualTo(0.25);
        assertThat(report.truncatedCalls()).isEqualTo(1);
        assertThat(report.stability()).isEqualTo(0.5);
    }

    @Test
    void json_report_namesTheSuiteAndEveryGroup() {
        final RealRunReport report = new RealRunReport("m", List.of(row("a", "quotes", "draft", Outcome.PASS)));

        assertThat(report.json())
                .startsWith("{\"suite\":\"realrun\",\"model\":\"m\"")
                .contains("\"key\":\"quotes/draft\",\"rate\":1.000,\"passed\":1,\"counted\":1");
    }

    @Test
    void table_report_listsEveryRowAndTheGroupRates() {
        final RealRunReport report = new RealRunReport(
                "m",
                List.of(row("a", "quotes", "draft", Outcome.PASS), row("b", "short-line", "draft", Outcome.KNOWN_RED)));

        assertThat(report.table()).contains("quotes/draft", "short-line/draft", "known", "truncated reviewer calls 0");
    }
}
