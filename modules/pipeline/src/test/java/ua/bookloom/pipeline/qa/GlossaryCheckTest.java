package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.project.Severity;

/** The glossary-compliance check: every locked term present in the segment must come back as its rendering. */
class GlossaryCheckTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("passOrSkipCases")
    void run_passOrSkip_matchesExpectedOutcome(
            final String name,
            final String target,
            final List<LockedRendering> lockedRenderings,
            final boolean expectedSkipped) {
        final CheckResult result = GlossaryCheck.run(SoftCheckFixtures.glossary(target, lockedRenderings));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.skipped()).isEqualTo(expectedSkipped);
        assertThat(result.finding()).isNull();
    }

    private static Stream<Arguments> passOrSkipCases() {
        return Stream.of(
                Arguments.of(
                        "every locked term rendered",
                        "Вона поїхала до Гейл і Мілтон вчора.",
                        List.of(new LockedRendering("Hale", "Гейл"), new LockedRendering("Milton", "Мілтон")),
                        false),
                Arguments.of(
                        "no locked term in the segment skips the check",
                        "Дощ не вщухав.",
                        List.<LockedRendering>of(),
                        true),
                Arguments.of("an unlocked term does not count", "вулиця Бейкер", List.<LockedRendering>of(), true));
    }

    @Test
    void run_oneOfTwoRenderingsMissing_blocksWithMediumGlossaryFinding() {
        final CheckResult result = GlossaryCheck.run(SoftCheckFixtures.glossary(
                "Вона поїхала до Гейл вчора.",
                List.of(new LockedRendering("Hale", "Гейл"), new LockedRendering("Milton", "Мілтон"))));

        assertThat(result.margin()).isCloseTo(0.0, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("glossary");
        assertThat(result.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.finding().raisedBy()).isEqualTo("glossary");
    }

    @Test
    void run_renderingOnlyInTheMaskedFormNotTheReplysDisplayText_passes() {
        final CheckResult result = GlossaryCheck.run(SoftCheckFixtures.glossary(
                "відчинив двері.", "Гейл відчинив двері.", List.of(new LockedRendering("Hale", "Гейл"))));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.skipped()).isFalse();
    }

    @Test
    void run_renderingOnlyInsideALongerWord_doesNotCount() {
        final CheckResult result = GlossaryCheck.run(SoftCheckFixtures.glossary(
                "Гейлові відчинили двері.", "Гейлові відчинили двері.", List.of(new LockedRendering("Hale", "Гейл"))));

        assertThat(result.passed()).isFalse();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().raisedBy()).isEqualTo("glossary");
    }
}
