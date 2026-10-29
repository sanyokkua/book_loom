package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The group's acceptance test: a word both Russian and Ukrainian use is never residue in a Ukrainian text. */
class FunctionWordsHomographTest {

    @ParameterizedTest
    @ValueSource(strings = {"просто", "тебе", "через", "того", "себе", "перед"})
    void sourceOnly_russianToUkrainian_holdsNoneOfTheHomographs(final String homograph) {
        assertThat(FunctionWords.sourceOnly("ru", "uk")).doesNotContain(homograph);
    }

    @Test
    void sourceOnly_russianToUkrainian_holdsRussianOnlyWords() {
        // Proves the computation is not vacuous: a Russian-only word is in the set.
        assertThat(FunctionWords.sourceOnly("ru", "uk")).contains("что");
    }

    @Test
    void sourceOnly_ukrainianFileWithoutProsto_holdsProsto() {
        // Proves the test can fail: with the word missing from the Ukrainian file, it is residue.
        final Set<String> russian = FunctionWords.parse("просто\nчто");
        final Set<String> ukrainianWithoutProsto = FunctionWords.parse("що\nі");
        assertThat(FunctionWords.difference(russian, ukrainianWithoutProsto)).contains("просто");
    }
}
