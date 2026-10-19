package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;

/** {@link SoftChecks#run(SoftCheckInput)}'s fixed check order. */
class SoftChecksTest {

    @Test
    void run_allChecks_returnsResultsInFixedOrderAllPassing() {
        final SoftCheckInput input = SoftCheckFixtures.scriptEcho(
                "He opened the old door.",
                "Він відчинив старі двері.",
                "en",
                "uk",
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.TRANSLATE,
                null,
                List.of());

        final List<CheckResult> results = SoftChecks.run(input);

        assertThat(results)
                .extracting(CheckResult::check)
                .containsExactly(
                        CheckName.SCRIPT, CheckName.ECHO, CheckName.REPETITION, CheckName.LENGTH, CheckName.GLOSSARY);
        assertThat(results).allMatch(CheckResult::passed);
        assertThat(results).noneMatch(CheckResult::blocking);
    }
}
