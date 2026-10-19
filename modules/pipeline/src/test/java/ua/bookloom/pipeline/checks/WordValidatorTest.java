package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The {@link WordValidator} port: the no-op default, and the contract any word list meets through a fake one. */
class WordValidatorTest {

    private static final Set<String> KNOWN =
            Set.of("він", "сидів", "на", "кафедрі", "й", "мовчки", "дивився", "у", "вікно");

    private static final WordValidator DICTIONARY = new DictionaryWordValidator(KNOWN::contains);

    @Test
    void none_anyText_hasNoOpinion() {
        assertThat(WordValidator.none().find("Він сидів на кафедрахрі.", "uk")).isEmpty();
        assertThat(WordValidator.none()).isSameAs(WordValidator.none());
    }

    @Test
    void find_unknownWord_isOneSoftFindingSpanningIt() {
        final String text = "Він сидів на кафедрахрі й мовчки дивився у вікно.";

        final List<CheckFinding> found = DICTIONARY.find(text, "uk");

        assertThat(found).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(FindingKind.UNKNOWN_WORD);
            assertThat(finding.blocking()).isFalse();
            assertThat(finding.span().text()).isEqualTo("кафедрахрі");
            assertThat(finding.span().start()).isEqualTo(13);
            assertThat(finding.span().end()).isEqualTo(23);
            assertThat(text.substring(finding.span().start(), finding.span().end()))
                    .isEqualTo("кафедрахрі");
        });
    }

    @Test
    void find_knownWordsInAnyCase_areNotFlagged() {
        assertThat(DICTIONARY.find("ВІН сидів на кафедрі, й мовчки дивився у вікно.", "uk"))
                .isEmpty();
    }

    @Test
    void find_severalUnknownWords_arriveInTextOrder() {
        assertThat(DICTIONARY.find("Він розлізяв на дірявтиму.", "uk"))
                .extracting(finding -> finding.span().text())
                .containsExactly("розлізяв", "дірявтиму");
    }

    @Test
    void find_noWords_isEmpty() {
        assertThat(DICTIONARY.find("— 12 … ?!", "uk")).isEmpty();
    }
}
