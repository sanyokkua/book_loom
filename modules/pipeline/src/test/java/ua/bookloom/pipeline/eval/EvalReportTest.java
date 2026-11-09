package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalReportTest {

    @Test
    void floors_noReviewSeparationFloor_isNotAskedFor() {
        final EvalRow reviewFails = new EvalRow(
                "c",
                "draft",
                EvalRow.Check.PASS,
                EvalRow.Check.PASS,
                EvalRow.Check.PASS,
                EvalRow.Check.PASS,
                EvalRow.Check.PASS,
                EvalRow.Check.FAIL,
                "");
        final EvalReport report = new EvalReport("gemma4:e4b", List.of(reviewFails));

        assertThat(report.meetsThresholds()).isTrue();
        assertThat(report.json()).doesNotContain("reviewSeparation");
        assertThat(report.table()).doesNotContain("review ");
    }
}
