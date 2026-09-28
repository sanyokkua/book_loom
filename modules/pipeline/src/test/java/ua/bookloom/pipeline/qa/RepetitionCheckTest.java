package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Severity;

/** The repetition check: a 3-word decode loop fails, an ordinary repeated phrase does not. */
class RepetitionCheckTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
                    two repeats pass at half margin | двері відчинилися знову двері відчинилися знову і він увійшов | 0.5
                    no repeated sequence passes at full margin | двері відчинилися знову і він тихо увійшов | 1.0
                    """)
    void run_pass_matchesExpectedMargin(final String name, final String target, final double expectedMargin) {
        final CheckResult result = RepetitionCheck.run(SoftCheckFixtures.repetition(target));

        assertThat(result.margin()).isCloseTo(expectedMargin, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    @Test
    void run_threeRepeatsInARow_blocksWithMediumFluencyFinding() {
        final CheckResult result = RepetitionCheck.run(SoftCheckFixtures.repetition(
                "двері відчинилися знову двері відчинилися знову двері відчинилися знову"));

        assertThat(result.margin()).isCloseTo(0.0, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("fluency");
        assertThat(result.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.finding().raisedBy()).isEqualTo("repetition");
    }
}
