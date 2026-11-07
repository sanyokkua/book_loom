package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.eval.SequenceRun.Decided;

/** The sequence report's table and its JSON with every repeat's figures, over hand-written runs. */
class SequenceReportTest {

    private static final SequenceFixture FIXTURE = SequenceFixture.load();
    private static final Decided ONE = new Decided(
            "s0",
            0,
            NarratorPerson.THIRD,
            "The master came.",
            "Господар прийшов.",
            SegmentStatus.ACCEPTED,
            SegmentPath.DRAFT,
            0);

    @Test
    void table_runWithNoRepeatedTerm_printsLtcr1AsNotApplicable() {
        final SequenceReport report = new SequenceReport(measured(run(0)));

        assertThat(report.table()).contains("LTCR-1 n/a");
    }

    @Test
    void json_threeRepeats_writesEachRepeatsFigures() throws IOException {
        final List<SequenceRun> runs = List.of(run(2), run(5), run(0));
        final List<SequenceMetrics> repeats =
                runs.stream().map(SequenceReportTest::measured).toList();

        final JsonNode json = new ObjectMapper()
                .readTree(new SequenceReport(repeats.getFirst()).json(runs, List.of(1000, 1001, 1002), repeats));

        assertThat(json.get("ltcr1PerRepeat").size()).isEqualTo(3);
        assertThat(json.get("ltcr1PerRepeat").get(1).asDouble()).isEqualTo(-1.0);
        assertThat(json.get("perRepeat").get(1).get("seed").asInt()).isEqualTo(1001);
        assertThat(json.get("perRepeat").get(1).get("hardGateFailuresRound0").asInt())
                .isEqualTo(5);
        assertThat(json.get("hardGateFailuresRound0").asInt()).isEqualTo(2);
        assertThat(json.get("outputs").get("s0").asText()).isEqualTo("Господар прийшов.");
        assertThat(json.get("perRepeat").get(2).get("outputs").get("s0").asText())
                .isEqualTo("Господар прийшов.");
        assertThat(json.get("perRepeat")
                        .get(1)
                        .get("summaryGate")
                        .get("dropShare")
                        .asDouble())
                .isEqualTo(-1.0);
    }

    @Test
    void repeatLines_twoRepeats_oneLinePerRepeat() {
        final String lines = SequenceReport.repeatLines(List.of(measured(run(1)), measured(run(3))));

        assertThat(lines.lines()).hasSize(2);
        assertThat(lines).contains("repeat 1  LTCR-1 n/a  hardGateRound0 3").doesNotContain("asksStopped");
    }

    private static SequenceRun run(final int hardGateFailures) {
        return new SequenceRun(
                new JobReport(BookFormat.MARKDOWN, JobState.COMPLETED, 1, 1, 0, List.of(), null),
                Duration.ofSeconds(1),
                List.of(),
                List.of(ONE),
                List.of(),
                Map.of(),
                0,
                hardGateFailures,
                Map.of(),
                0,
                SequenceNarratorMode.UNSET);
    }

    private static SequenceMetrics measured(final SequenceRun run) {
        return new SequenceMeasure(FIXTURE).measure("hand", "BALANCED", run);
    }
}
