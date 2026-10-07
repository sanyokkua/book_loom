package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A name the source calls out must still be in the target, wherever the dialogue tag puts it. */
class VocativeCheckTest {

    private static final List<String> PAIRS = List.of("Bobby → Боббі");
    private static final List<String> ALTERNATIONS = List.of("гзж", "кцч", "хсш");

    private static List<CheckFinding> find(final String source, final String target) {
        return VocativeCheck.find(source, target, PAIRS, ALTERNATIONS, "en");
    }

    // IF a name inside a dialogue tag were not read as called out, THEN a draft that drops it passes (seg 95).
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"‘The Boys,' Bobby,\" I said.|«Хлопці», — сказав я.",
                "\"Come on, Bobby,\" he said.|«Ходімо», — сказав він.",
                "\"Come on, Bobby,\" he said, and left.|«Ходімо», — сказав він і пішов.",
                "\"Come on, Bobby!\" he said.|«Ходімо!» — сказав він.",
                "Come on, Bobby.|Ходімо.",
                "Bobby, come on.|Ходімо."
            })
    void find_calledOutNameMissingFromTheTarget_isBlocking(final String source, final String target) {
        assertThat(find(source, target)).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.VOCATIVE_MISSING);
            assertThat(finding.blocking()).isTrue();
        });
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"‘The Boys,' Bobby,\" I said.|«Хлопці», Боббі, — сказав я.",
                "\"Come on, Bobby,\" he said.|«Ходімо, Боббі», — сказав він.",
                "\"Come on, Bobby,\" he said.|«Ходімо, Бобі», — сказав він."
            })
    void find_calledOutNameKept_saysNothing(final String source, final String target) {
        assertThat(find(source, target)).isEmpty();
    }

    // IF any comma-name-words tail counted, THEN every narration naming a character after a comma would be a vocative.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "He saw Tom, Bobby walked away.|Він побачив Тома, Боббі пішов.",
                "He liked it, Bobby thought, and left.|Йому сподобалося."
            })
    void find_nameThatIsNotCalledOut_isNotChecked(final String source, final String target) {
        assertThat(find(source, target)).isEmpty();
    }
}
