package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;

/**
 * A pinned target's case forms and vocative come from the target language's name table and its vowel changes; a
 * language with no table gives the target alone. All names are invented or common first names.
 */
class NameFormsForwardTest {

    // Each row: the target, its gender, some case forms ("|"-joined) and its vocative, all hard-coded.
    @ParameterizedTest
    @CsvSource({
        "Зотов, MALE, Зотова|Зотову|Зотовим, Зотове",
        "Лев, MALE, Льва|Лева|Леву|Левом, Леве",
        "Алєксєй, MALE, Алєксєя|Алєксєєм, Алєксєю",
        "Мирена, FEMALE, Миреною|Мирену, Мирено",
        "Сокіл, MALE, Сокола|Соколом, Соколе"
    })
    void forms_pinnedUkrainianTarget_includesCaseFormsAndVocative(
            final String target, final Gender gender, final String cases, final String vocative) {
        final NameForms.TargetForms forms = NameForms.forms(target, gender, "uk");

        assertThat(forms.cases()).contains(cases.split("\\|"));
        assertThat(forms.vocatives()).contains(vocative);
        assertThat(forms.all()).contains(target);
    }

    // A respelling is not one of the forms: Зотев is another name, not a case of Зотов.
    @Test
    void forms_pinnedTarget_excludesARespelling() {
        assertThat(NameForms.forms("Зотов", Gender.MALE, "uk").all()).doesNotContain("Зотев", "Зотова́");
    }

    // A woman's surname takes the feminine row only, so no masculine instrumental is generated.
    @Test
    void forms_feminineSurname_usesTheFeminineRow() {
        assertThat(NameForms.forms("Рудакова", Gender.FEMALE, "uk").cases())
                .contains("Рудакової", "Рудаковою")
                .doesNotContain("Рудаковим");
    }

    // Every word of a two-word target declines, and the forms combine.
    @Test
    void forms_twoWordTarget_declinesEachWord() {
        assertThat(NameForms.forms("Тарас Зотов", Gender.MALE, "uk").cases())
                .contains("Тараса Зотова", "Тарасом Зотовим");
    }

    // With no gender known, the forms of both genders are allowed.
    @Test
    void forms_unknownGender_unitesBothGenders() {
        assertThat(NameForms.forms("Мирена", Gender.UNKNOWN, "uk").cases()).contains("Миреною");
    }

    // A language with no name table gives only the target.
    @ParameterizedTest
    @CsvSource({"en, Tobin Reed", "xx, Мирена"})
    void forms_noTable_givesTargetOnly(final String language, final String target) {
        final NameForms.TargetForms forms = NameForms.forms(target, Gender.MALE, language);

        assertThat(forms.all()).containsExactly(target);
        assertThat(Arrays.asList(forms.cases().size(), forms.vocatives().size()))
                .containsExactly(0, 0);
    }
}
