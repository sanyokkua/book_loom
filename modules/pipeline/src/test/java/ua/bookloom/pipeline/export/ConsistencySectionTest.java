package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.pipeline.revision.ConsistencyReport;

/** The report says how much the consistency pass tried, kept and refused, each refusal by the rule that stopped it. */
class ConsistencySectionTest {

    @Test
    void of_passNotRun_saysSo() {
        assertThat(ConsistencySection.of(null)).isEqualTo("The consistency pass was not run.\n");
    }

    @Test
    void of_passWithChecks_countsEveryOutcomeAndNamesRefusalsByRule() {
        final ConsistencyReport pass = new ConsistencyReport(
                0,
                0,
                0,
                List.of("ch1 · p02: fixed against its neighbours"),
                Map.of(),
                1,
                new ConsistencyChecks(2, 3, 4, Map.of("clauses", 2, "rewrite-cap", 1), 5));

        assertThat(ConsistencySection.of(pass))
                .contains("- Drafted again and replaced: 2")
                .contains("- Drafted again, old text kept: 3")
                .contains("- Neighbour check: fixed 1, unchanged 4")
                .contains("- Answers refused: 3 (clauses 2, rewrite-cap 1)")
                .contains("- Skipped, the call failed: 5")
                .contains("- ch1 · p02: fixed against its neighbours");
    }

    @Test
    void of_passThatChangedNothing_saysSo() {
        assertThat(ConsistencySection.of(new ConsistencyReport(0, 0, 0, List.of())))
                .isEqualTo("The consistency pass changed nothing.\n");
    }
}
