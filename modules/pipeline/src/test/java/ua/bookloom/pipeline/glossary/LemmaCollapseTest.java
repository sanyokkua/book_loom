package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A written form that fits two names collapses into one only when both names have the same target: then it is the
 * name of the class more names of the book decline by, ties going to the language file's order. All names are
 * invented.
 */
class LemmaCollapseTest {

    private static final String SAME = "Градовський";

    // Both names give the same target, and more names decline like the adjective: the form is that name's.
    @Test
    void collapse_sameTargetMoreFrequentClass_choosesThatClass() {
        assertThat(LemmaCollapse.collapse(
                        "Градовского",
                        "ru",
                        Map.of("Градовский", SAME, "Градовской", SAME),
                        Map.of("ий|m", 3, "ой|m", 1)))
                .contains("Градовский");
    }

    // A tie goes to the row the language file lists first (-ой before the adjective -ий).
    @Test
    void collapse_sameTargetTie_choosesTheEarlierRow() {
        assertThat(LemmaCollapse.collapse(
                        "Градовского", "ru", Map.of("Градовский", SAME, "Градовской", SAME), Map.of()))
                .contains("Градовской");
    }

    // Two names of the same gender with different targets are two people: the form is not collapsed.
    @Test
    void collapse_differentTargets_none() {
        assertThat(LemmaCollapse.collapse(
                        "Градимиром",
                        "ru",
                        Map.of("Градимир", "Градимир", "Градимирой", "Градимирой", "Градимирий", "Градимирій"),
                        Map.of("C|m", 5)))
                .isEmpty();
    }

    // A competing name with no target yet decides nothing.
    @Test
    void collapse_aNameWithoutTarget_none() {
        assertThat(LemmaCollapse.collapse("Градовского", "ru", Map.of("Градовский", SAME), Map.of("ий|m", 3)))
                .isEmpty();
    }

    // A form with only one possible name is no competition.
    @Test
    void collapse_oneReading_none() {
        assertThat(LemmaCollapse.collapse("Рудаковой", "ru", Map.of("Рудакова", "Рудакова"), Map.of()))
                .isEmpty();
    }
}
