package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What the sentence-count and vocative checks hold a translation to, and what they let through. */
class SentenceCountCheckTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Mr. Smith left the room. She stayed behind there.|2",
                "He met J. R. Tolkien at the station. They talked for hours.|2",
                "Wait! Why would anyone do that? Nobody knows why.|2",
                "It was about 5 p.m. and the street was quiet.|1",
                "“Come with me,” she said. “Now.”|1"
            })
    void significant_titlesInitialsAndShortLines_countOnlyRealSentences(final String text, final int expected) {
        assertThat(Sentences.significant(text)).hasSize(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "The door was open. Nobody came to answer it.|Двері були відчинені.|true",
                "The door was open. Nobody came to answer it.|Двері були відчинені. Ніхто не прийшов.|false",
                "The door was open. Nobody came to answer it.|двері були відчинені. ніхто не прийшов.|false",
                "Mr. Smith left the room quietly.|Пан Сміт тихо вийшов.|false",
                "He nodded. Yes.|Він кивнув.|false",
                "One long sentence only, with a few clauses.|Одне довге речення.|false"
            })
    void dropsSentence_targetWithFewerSentences_isFlagged(
            final String source, final String target, final boolean flagged) {
        assertThat(SentenceCount.dropsSentence(source, target)).isEqualTo(flagged);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "The door was open. Nobody came to answer it.|扉は開いていた。|false",
                "扉は開いていた。誰も出なかった。|The door was open.|false"
            })
    void dropsSentence_unspacedScript_isNeverJudged(final String source, final String target, final boolean flagged) {
        assertThat(SentenceCount.dropsSentence(source, target)).isEqualTo(flagged);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Finn, you never listen.|Ти ніколи не слухаєш.|true",
                "You never listen, Finn!|Ти ніколи не слухаєш, Фінне!|false",
                "You never listen, Finn!|Ти ніколи не слухаєш.|true",
                "Finn is here.|Він тут.|false",
                "No, master.|Ні, учителю.|false"
            })
    void vocative_calledOutName_mustSurviveInSomeForm(final String source, final String target, final boolean lost) {
        final List<CheckFinding> found =
                VocativeCheck.find(source, target, List.of("Finn → Фінн", "master → господар"), List.of("гзж"));

        assertThat(!found.isEmpty()).isEqualTo(lost);
    }
}
