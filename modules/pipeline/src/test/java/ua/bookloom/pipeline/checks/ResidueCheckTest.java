package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Reply residue (JSON closers, wrapper labels) the model leaves in a target, from the Burning Chrome hand run. */
class ResidueCheckTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "brace-bracket tail seg 182|\"Miles, where'd she go?\"|«Майлз, куди вона пішла?»}]}Outcome: {",
                "closers and dollar seg 31|\"Looks Russian to me,\" he said.|«Схоже на російську», — сказав він.»}]}}$",
                "wrapper label only|He left.|Він пішов. Translation: готово",
            })
    void find_residueInTarget_isBlocking(final String name, final String source, final String target) {
        assertThat(ResidueCheck.find(source, target)).hasValueSatisfying(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.PROTOCOL_LEAK);
            assertThat(finding.blocking()).isTrue();
        });
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "clean dialogue|\"Miles, where'd she go?\"|«Майлз, куди вона пішла?»",
                "braces the source has|Use {x} here.|Використай {x} тут.",
                "brackets the source has|See note [1].|Дивись примітку [1].",
            })
    void find_cleanTarget_isEmpty(final String name, final String source, final String target) {
        assertThat(ResidueCheck.find(source, target)).isEmpty();
    }

    @Test
    void run_textChecks_includesResidueFinding() {
        assertThat(TextChecks.run("He left.", "Він пішов.}]}", "en", "uk"))
                .extracting(CheckFinding::kind)
                .contains(FindingKind.PROTOCOL_LEAK);
    }
}
