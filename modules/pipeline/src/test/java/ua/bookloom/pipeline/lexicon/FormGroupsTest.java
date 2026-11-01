package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The stem groups of the recurring terms: the forms of one word count together and are read as one. */
class FormGroupsTest {

    @Test
    void group_inflectedForms_shareOneKeyAndCountTogether() {
        final List<FormGroups.Group> groups = FormGroups.of(Map.of("каменя", 5, "каменю", 1), "ru");

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.key()).isEqualTo("камен");
            assertThat(group.uses()).isEqualTo(6);
            assertThat(group.forms()).containsOnlyKeys("каменя", "каменю");
            assertThat(group.uses()).isGreaterThanOrEqualTo(FormGroups.MIN_GROUP_USES);
        });
    }

    @Test
    void group_threeForms_oneGroupWithTheNominativeAsRepresentative() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("фонаря", 4);
        counts.put("фонарь", 3);
        counts.put("фонарём", 2);

        final List<FormGroups.Group> groups = FormGroups.of(counts, "ru");

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.uses()).isEqualTo(9);
            assertThat(group.representative()).isEqualTo("фонарь");
            assertThat(group.others()).containsExactlyInAnyOrder("фонаря", "фонарём");
        });
    }

    @Test
    void group_stemUnderFourLetters_staysTwoGroups() {
        final List<FormGroups.Group> groups = FormGroups.of(Map.of("дом", 4, "дома", 3), "ru");

        assertThat(groups).extracting(FormGroups.Group::representative).containsExactlyInAnyOrder("дом", "дома");
    }

    @ParameterizedTest(name = "[{index}] {0} + {1} = {2}")
    @CsvSource({"2, 2, 4", "3, 2, 5"})
    void group_usesAddUp_acrossForms(final int base, final int oblique, final int expected) {
        final List<FormGroups.Group> groups = FormGroups.of(Map.of("фонарь", base, "фонаря", oblique), "ru");

        assertThat(groups).singleElement().extracting(FormGroups.Group::uses).isEqualTo(expected);
    }

    @Test
    void group_languageWithoutNounEndings_oneGroupPerForm() {
        final List<FormGroups.Group> groups = FormGroups.of(Map.of("lantern", 3, "lanterns", 2), "en");

        assertThat(groups).hasSize(2);
    }

    @Test
    void group_noSourceLanguage_oneGroupPerForm() {
        final List<FormGroups.Group> groups = FormGroups.of(Map.of("фонарь", 3, "фонаря", 2), null);

        assertThat(groups).hasSize(2);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({"фонарём, фонар", "Фонаря, фонар", "дома, дома", "дом, дом", "лес, лес"})
    void keyOf_form_dropsTheLongestEndingOnlyOverAFourLetterStem(final String form, final String expected) {
        assertThat(FormGroups.keyOf(form, List.of("ь", "ем", "ём", "я", "а", "ом")))
                .isEqualTo(expected);
    }
}
