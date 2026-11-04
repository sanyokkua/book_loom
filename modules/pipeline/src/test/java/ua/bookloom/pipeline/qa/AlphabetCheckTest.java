package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.FindingKind;

/** The Russian-only letters in a Ukrainian word are a blocking finding of that word; a language with no data is skipped. */
class AlphabetCheckTest {

    // The words of the owner's run that hold a Russian-only letter, and two more.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"бэкона,э", "кобыли,ы", "камыша,ы", "Ёлка,ё", "твердый,ы"})
    void find_russianOnlyLetter_isABlockingFindingOfThatWord(final String word, final String letter) {
        final List<CheckFinding> found =
                AlphabetCheck.find("Some text.", "Він бачив " + word + " там.", "en", "uk", List.of());

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().kind()).isEqualTo(FindingKind.ALPHABET);
        assertThat(found.getFirst().blocking()).isTrue();
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
        assertThat(AlphabetCheck.find("Some text.", target, "en", "uk", List.of()))
                .isEmpty();
    }

    @Test
    void check_ruToUkWordHeldBySource_stillFlagsLetter() {
        final List<CheckFinding> found =
                AlphabetCheck.find("Он нашёл ещё ключ.", "Він знайшов ещё ключ.", "ru", "uk", List.of());

        assertThat(found)
                .singleElement()
                .satisfies(f -> assertThat(f.span().text()).isEqualTo("ещё"));
    }

    @Test
    void check_ruToUkWordNotHeldBySource_isStillFlagged() {
        assertThat(AlphabetCheck.find("Он купил кобылу.", "Він купив кобылу.", "ru", "uk", List.of()))
                .singleElement()
                .satisfies(f -> assertThat(f.explanation()).contains("ы"));
    }

    @Test
    void check_enToUkLatinNameInSource_exempt() {
        assertThat(AlphabetCheck.find(
                        "He wrote Мышь on the wall.", "Він написав Мышь на стіні.", "en", "uk", List.of()))
                .isEmpty();
    }

    @Test
    void find_languageWithNoForbiddenLetters_isNotChecked() {
        assertThat(AlphabetCheck.find("Some text.", "Он бачив кобыли.", "en", "ru", List.of()))
                .isEmpty();
        assertThat(AlphabetCheck.find("Some text.", "Er sah kobыli.", "en", "de", List.of()))
                .isEmpty();
    }

    @Test
    void find_glossaryRenderingWithARussianLetter_isWhatThePersonTyped() {
        final List<String> pairs = List.of("Sin → Сын");

        assertThat(AlphabetCheck.find("Sin spoke.", "Сын мовчав. Сына не було.", "en", "uk", pairs))
                .isEmpty();
        assertThat(AlphabetCheck.find("Sin spoke.", "Сын мовчав. Кобыли не було.", "en", "uk", pairs))
                .hasSize(1);
    }
}
