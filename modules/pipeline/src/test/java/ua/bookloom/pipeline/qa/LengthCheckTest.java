package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.DisplayText;

/** The length-ratio check: the pair's band, its widening for a short source, and the margin window. */
class LengthCheckTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "a normal Ukrainian target passes,100,115,en,uk,1.0",
        "a target near the band's edge passes with half the margin,200,151,en,uk,0.5",
        "an unlisted pair uses the default band,30,69,uk,el,1.0",
    })
    void run_passWithRepeatedCharacters_matchesExpectedMargin(
            final String name,
            final int sourceChars,
            final int targetChars,
            final String sourceLang,
            final String targetLang,
            final double expectedMargin) {
        final CheckResult result = LengthCheck.run(
                SoftCheckFixtures.length("a".repeat(sourceChars), "b".repeat(targetChars), sourceLang, targetLang));

        assertThat(result.margin()).isCloseTo(expectedMargin, within(1e-9));
        assertThat(result.passed()).isTrue();
        assertThat(result.blocking()).isFalse();
        assertThat(result.finding()).isNull();
    }

    @Test
    void run_shortSourceWidenedBand_passes() {
        final CheckResult result = LengthCheck.run(SoftCheckFixtures.length("Go.", "Йди.", "en", "uk"));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.passed()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "a half-length Ukrainian target fails,100,50,en,uk",
        "a short source's widened band still fails,3,14,en,uk"
    })
    void run_failWithRepeatedCharacters_blocksWithMediumOmissionFinding(
            final String name,
            final int sourceChars,
            final int targetChars,
            final String sourceLang,
            final String targetLang) {
        final CheckResult result = LengthCheck.run(
                SoftCheckFixtures.length("a".repeat(sourceChars), "b".repeat(targetChars), sourceLang, targetLang));

        assertThat(result.margin()).isCloseTo(0.0, within(1e-9));
        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).isTrue();
        assertThat(result.finding()).isNotNull();
        assertThat(result.finding().kind()).isEqualTo("omission");
        assertThat(result.finding().severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.finding().raisedBy()).isEqualTo("length");
    }

    // A source of protected tokens only display-texts to empty, exactly like a real all-placeholder segment would.
    @Test
    void run_emptySource_skipsMeasuringLength() {
        final CheckResult result =
                LengthCheck.run(SoftCheckFixtures.length(DisplayText.of("⟦g0⟧⟦g1⟧"), "b".repeat(10), "en", "uk"));

        assertThat(result.margin()).isCloseTo(1.0, within(1e-9));
        assertThat(result.skipped()).isTrue();
    }
}
