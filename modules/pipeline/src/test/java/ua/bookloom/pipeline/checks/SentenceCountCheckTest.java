package ua.bookloom.pipeline.checks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
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
        assertThat(SentenceCount.dropsSentence(source, target, "en", "uk")).isEqualTo(flagged);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "The door was open. Nobody came to answer it.|扉は開いていた。|false",
                "扉は開いていた。誰も出なかった。|The door was open.|false"
            })
    void dropsSentence_unspacedScript_isNeverJudged(final String source, final String target, final boolean flagged) {
        assertThat(SentenceCount.dropsSentence(source, target, "en", "uk")).isEqualTo(flagged);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Dane, you never listen.|Ти ніколи не слухаєш.|true",
                "You never listen, Dane!|Ти ніколи не слухаєш, Дейне!|false",
                "You never listen, Dane!|Ти ніколи не слухаєш.|true",
                "Dane is here.|Він тут.|false",
                "No, master.|Ні, учителю.|false",
                "Yes, Sir.|Так, пане.|false"
            })
    void vocative_calledOutName_mustSurviveInSomeForm(final String source, final String target, final boolean lost) {
        final List<CheckFinding> found = VocativeCheck.find(
                source, target, List.of("Dane → Дейн", "master → господар", "Sir → Сер"), List.of("гзж"), "en");

        assertThat(!found.isEmpty()).isEqualTo(lost);
    }

    // IF a script whose sentences end in other marks were counted, THEN its every paragraph would read as one sentence.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "hi|दरवाज़ा खुला था। कोई जवाब देने नहीं आया।",
                "ar|كان الباب مفتوحا. لم يأت أحد ليجيب.",
                "hy|Դուռը բաց էր։ Ոչ ոք չեկավ պատասխանելու։"
            })
    void dropsSentence_targetInAScriptNotCounted_isNeverJudged(final String language, final String target) {
        assertThat(SentenceCount.dropsSentence("The door was open. Nobody came to answer it.", target, "en", language))
                .isFalse();
    }

    // The Ukrainian convention turns the English quote's full stop into a comma before the dash (15j.J9); the
    // sentences are invented.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "\"Wait a moment.\" Brin raised her hand. Nobody moved at all.|«Зачекайте хвилинку», — Брін підняла руку. Ніхто не ворухнувся.|false",
                "\"Wait a moment.\" Brin raised her hand. Nobody moved at all.|«Зачекайте хвилинку» — Брін підняла руку. Ніхто не ворухнувся.|false",
                "\"Wait a moment.\" Brin raised her hand. Nobody moved at all.|«Зачекайте хвилинку», — Брін підняла руку.|true",
                "\"Wait a moment,\" Brin said. Nobody moved at all. The room was quiet.|«Зачекайте хвилинку», — сказала Брін. Ніхто не ворухнувся.|true",
                "\"Wait a moment,\" Brin said. Nobody moved at all.|«Зачекайте хвилинку», — сказала Брін. Ніхто не ворухнувся.|false",
                "\"Stop!\" she said.|«Стій!» — сказала вона.|false",
                "\"Stop!\" she said. Nobody moved at all.|«Стій!» — сказала вона. Ніхто не ворухнувся.|false",
                "\"Wait,\" he said. Nobody moved at all.|— Зачекай, — сказав він. Ніхто не ворухнувся.|false",
                "\"Wait,\" he said. Nobody moved at all.|— Зачекай, — сказав він.|true",
                "\"Wait for me here!\" she said quietly. Nobody answered at all.|«Чекай мене тут!» — тихо сказала вона. Ніхто не відповів.|false",
                "\"Is it far?\" she asked quietly. Nobody answered at all.|«Це далеко?» — тихо спитала вона. Ніхто не відповів.|false",
                "\"Wait for me here!\" she said quietly. Nobody answered at all. The room was quiet.|«Чекай мене тут!» — тихо сказала вона. Ніхто не відповів.|true"
            })
    void dropsSentence_dialogueDashAttribution_isASentenceBoundaryOnlyWhereTheSourceEndedTheQuote(
            final String source, final String target, final boolean flagged) {
        assertThat(SentenceCount.dropsSentence(source, target, "en", "uk")).isEqualTo(flagged);
    }

    // A Russian source's abbreviation is no sentence end, so one sentence in is one sentence out.
    @Test
    void dropsSentence_russianAbbreviationBeforeACapital_isOneSentence() {
        assertThat(SentenceCount.dropsSentence(
                        "Он жил в г. Москве и работал там много лет.",
                        "He lived in Moscow and worked there.",
                        "ru",
                        "en"))
                .isFalse();
    }
}
