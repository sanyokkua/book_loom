package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A glossary name of the source must not be replaced by another glossary name in the target. */
class NameSwapCheckTest {

    private static final List<String> PAIRS = List.of("Flint → Флінт", "Corvin → Корвін", "Chisel → Чизел");
    private static final List<String> UK = List.of("гзж", "кцч", "хсш");

    @Test
    void find_otherGlossaryNameStandsWhereTheSourceHasThisOne_isBlocking() {
        final List<CheckFinding> found =
                NameSwapCheck.find("Flint opened the door.", "Корвін відчинив двері.", PAIRS, UK, "en");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.NAME_SWAP);
        assertThat(found.getFirst().blocking()).isTrue();
        assertThat(found.getFirst().explanation())
                .contains("Flint")
                .contains("Флінт")
                .contains("Корвін");
    }

    @Test
    void find_sourceNameKeptInADeclinedForm_isClean() {
        assertThat(NameSwapCheck.find("Flint opened the door.", "Двері відчинив Флінт.", PAIRS, UK, "en"))
                .isEmpty();
        assertThat(NameSwapCheck.find("Corvin thanked Flint.", "Корвін подякував Флінту.", PAIRS, UK, "en"))
                .isEmpty();
    }

    @Test
    void find_nameReplacedByAPronoun_isNotASwap() {
        assertThat(NameSwapCheck.find("Flint opened the door.", "Він відчинив двері.", PAIRS, UK, "en"))
                .isEmpty();
    }

    @Test
    void find_bothNamesInTheSourceAndTheTarget_isClean() {
        assertThat(NameSwapCheck.find("Flint and Corvin sat.", "Флінт і Корвін сиділи.", PAIRS, UK, "en"))
                .isEmpty();
    }

    @Test
    void find_otherNameInTheTargetWhileTheSourceNamesBothAndOneIsLost_isNotASwap() {
        assertThat(NameSwapCheck.find("Flint and Corvin sat.", "Корвін сидів.", PAIRS, UK, "en"))
                .isEmpty();
    }
}
