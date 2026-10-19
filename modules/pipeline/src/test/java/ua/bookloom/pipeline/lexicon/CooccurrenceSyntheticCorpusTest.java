package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.pipeline.lexicon.CooccurrenceLearner.Learned;

/**
 * A generated book of invented sentences (no book text anywhere) with four tracked terms, interleaved with filler:
 * two the translation renders one way in several inflections, and two it renders two ways — a title that alternates
 * between two words and a word with two meanings. The learner must name the first two and say nothing of the others.
 */
class CooccurrenceSyntheticCorpusTest {

    private static final int PER_TERM = 40;
    private static final int FILLER_PER_ROUND = 3;
    private static final int STRIDE = 3;
    private static final List<String[]> ACTIONS = List.of(
            new String[] {"walked home", "пішов додому"},
            new String[] {"sat by the fire", "сидів біля вогню"},
            new String[] {"looked at the sea", "дивився на море"},
            new String[] {"opened the book", "відкрив книжку"},
            new String[] {"laughed loudly", "голосно засміявся"},
            new String[] {"waited in silence", "мовчки чекав"},
            new String[] {"read a letter", "читав листа"},
            new String[] {"climbed the hill", "піднявся на пагорб"});
    private static final List<String[]> FORMS =
            List.of(new String[] {"The %s ", "%s "}, new String[] {"Then the %s ", "Тоді %s "}, new String[] {
                "Nobody saw how the %s ", "Ніхто не бачив, як %s "
            });

    private static final List<String[]> PAIRS = new ArrayList<>();
    private static final CooccurrenceLearner LEARNER = new CooccurrenceLearner();

    @BeforeAll
    static void learnTheBook() {
        for (int i = 0; i < PER_TERM; i++) {
            PAIRS.add(sentence(i, "master", List.of("господар", "господаря", "господарю", "господарем")));
            PAIRS.add(sentence(i, "djinni", List.of("джин", "джина", "джину", "джином")));
            PAIRS.add(sentence(i, "Mr", List.of(i % 2 == 0 ? "містер Баттон" : "пан Хопкінс")));
            PAIRS.add(sentence(i, "staff", List.of(i % 2 == 0 ? "посох" : "персонал")));
            for (int j = 0; j < FILLER_PER_ROUND; j++) {
                final String[] action = ACTIONS.get((i + j * STRIDE) % ACTIONS.size());
                PAIRS.add(new String[] {
                    "He " + action[0] + ", and it was late.", "І він " + action[1] + ", що було пізно."
                });
            }
        }
        for (final String term : List.of("master", "djinni", "Mr", "staff")) {
            LEARNER.track(term);
        }
        PAIRS.forEach(pair -> LEARNER.observe(pair[0], pair[1]));
    }

    private static String[] sentence(final int round, final String term, final List<String> targets) {
        final String[] action = ACTIONS.get((round * STRIDE + term.length()) % ACTIONS.size());
        final String[] form = FORMS.get((round + term.length()) % FORMS.size());
        final String target = targets.get((round / 2 + term.length()) % targets.size());
        final String shown =
                form[1].startsWith("%s") ? Character.toUpperCase(target.charAt(0)) + target.substring(1) : target;
        return new String[] {form[0].formatted(term) + action[0] + ".", form[1].formatted(shown) + action[1] + "."};
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(
            value = {"master,господар", "djinni,джин", "Mr,NULL", "staff,NULL"},
            nullValues = "NULL")
    void established_generatedBook_namesTheOneWayRenderedTermsAndNothingElse(final String term, final String expected) {
        assertThat(LEARNER.established(term, Set.of()).map(Learned::rendering).orElse(null))
                .isEqualTo(expected);
    }

    @Test
    void established_generatedBook_supportCoversEveryOccurrenceOfALearnedTerm() {
        assertThat(LEARNER.established("master", Set.of()))
                .hasValueSatisfying(found -> assertThat(found)
                        .extracting(Learned::support, Learned::occurrences)
                        .containsExactly(PER_TERM, PER_TERM));
    }
}
