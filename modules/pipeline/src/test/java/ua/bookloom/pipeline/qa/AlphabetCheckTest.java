package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;

/** The Russian-only letters in a Ukrainian word are a soft finding of that word; a language with no data is skipped. */
class AlphabetCheckTest {

    // The words of the owner's run that hold a Russian-only letter, and two more.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"бэкона,э", "кобыли,ы", "камыша,ы", "Ёлка,ё", "твердый,ы"})
    void find_russianOnlyLetter_isASoftFindingOfThatWord(final String word, final String letter) {
        final List<CheckFinding> found = AlphabetCheck.find("Some text.", "Він бачив " + word + " там.", "uk");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.ALPHABET);
        assertThat(found.getFirst().blocking()).isFalse();
        assertThat(found.getFirst().span().text()).isEqualTo(word);
        assertThat(found.getFirst().explanation()).contains(letter);
    }

    @ParameterizedTest
    @CsvSource({
        "Корчмар посмажив бекону і поставив його перед гостем.",
        "Вона вивела кобилу зі стайні на світанку.",
        "Чапля стояла нерухомо серед очерету.",
        "Це ж єдиний їжак, що гукнув: «Ґанок!»"
    })
    void find_cleanUkrainian_saysNothing(final String target) {
        assertThat(AlphabetCheck.find("Some text.", target, "uk")).isEmpty();
    }

    @Test
    void find_wordAlsoInTheSource_isAKeptForeignWordAndIsLeftAlone() {
        assertThat(AlphabetCheck.find("He wrote Мышь on the wall.", "Він написав Мышь на стіні.", "uk"))
                .isEmpty();
    }

    @Test
    void find_languageWithNoForbiddenLetters_isNotChecked() {
        assertThat(AlphabetCheck.find("Some text.", "Он бачив кобыли.", "ru")).isEmpty();
        assertThat(AlphabetCheck.find("Some text.", "Er sah kobыli.", "de")).isEmpty();
    }
}
