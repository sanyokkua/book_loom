package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The setup suite's report carries the per-field score in its text and its JSON; the other suites' JSON is unchanged. */
class SetupReportTest {

    @TempDir
    private Path dir;

    @Test
    void run_setupSuite_reportsPerFieldInTableAndJson() {
        final var cases = StageCases.setup();
        final StageReport report = StageSuites.run("setup", "scripted", new SetupScriptedModel(cases, Set.of()), dir);

        assertThat(report.table())
                .contains("per field: genre 15/15 register 15/15 narrator 15/15 narratorGender 15/15"
                        + " knowledge.invented n/a knowledge.classic n/a");
        assertThat(report.json())
                .contains("\"perField\":{\"genre\":1.000,\"register\":1.000,\"narrator\":1.000,"
                        + "\"narratorGender\":1.000,\"knowledge.invented\":null,\"knowledge.classic\":null}")
                .endsWith("}}");
    }

    @Test
    void json_otherSuite_hasNoPerField() {
        final StageReport report = new StageReport("terms", "scripted", java.util.List.of());

        assertThat(report.json()).doesNotContain("perField").endsWith("\"callsPerCase\":0.000}");
    }
}
