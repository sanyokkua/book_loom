package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.Gender;

/**
 * The past-tense reader reads the source file's past endings: the first past verb after a mention, in the same clause,
 * outside speech and before any other name, and the narrator's own «я + past verb» forms. All names are invented.
 */
class PastTenseReaderTest {

    private static PastTenseReader reader(final String language) {
        return Objects.requireNonNull(PastTenseReader.of(language), language);
    }

    private static Optional<Gender> voteAfter(final String language, final String text, final String name) {
        final int start = text.indexOf(name);
        return reader(language).voteAfter(text, start, start + name.length()).map(PastTenseReader.Vote::gender);
    }

    // A feminine verb right after the name is one vote for female.
    @ParameterizedTest
    @CsvSource({
        "ru, Ольга пришла домой., Ольга, FEMALE",
        "ru, Велемир тихо ушёл., Велемир, MALE",
        "ru, Мирена кивнула и вышла., Мирена, FEMALE",
        "uk, Ольга прийшла додому., Ольга, FEMALE",
        "uk, Тарас не відповів., Тарас, MALE"
    })
    void votes_pastFemaleAfterName_countedOnceAndNotAcrossAQuote(
            final String language, final String text, final String name, final Gender expected) {
        assertThat(voteAfter(language, text, name)).contains(expected);
    }

    // A verb inside the quoted speech that starts after the name is the speaker's, not the narration's.
    @Test
    void votes_verbInsideAQuoteAfterTheName_none() {
        assertThat(voteAfter("ru", "Ольга: «пришла я рано», и всё.", "Ольга")).isEmpty();
    }

    // Another name between the mention and the verb takes the verb.
    @Test
    void votes_otherNameBetween_onlyNearer() {
        final String text = "Ольга и Велемир ушёл.";

        assertThat(voteAfter("ru", text, "Ольга")).isEmpty();
        assertThat(voteAfter("ru", text, "Велемир")).contains(Gender.MALE);
    }

    // A plural verb and a verb inside quoted speech are no evidence for the name.
    @ParameterizedTest
    @ValueSource(strings = {"«Ольга пришла», сказал он.", "Ольга и Велемир пришли.", "Ольга, наверное, ушла."})
    void votes_pluralAndSpeech_none(final String text) {
        assertThat(voteAfter("ru", text, "Ольга")).isEmpty();
    }

    // The reader votes; whether a genitive may take the verb is decided by the caller, which counts only the term.
    @Test
    void votes_genitiveMention_none() {
        final List<String> texts = List.of("Ключ Велемира лежал на столе.");

        assertThat(PronounGender.of("Велемир", texts, "ru").male()).isZero();
    }

    // A word that only ends like a past tense is no verb.
    @ParameterizedTest
    @ValueSource(strings = {"стол", "мало", "школа"})
    void genderOf_wordOnTheNotVerbList_isEmpty(final String word) {
        assertThat(reader("ru").genderOf(word)).isEmpty();
    }

    // The ending decides, with the irregular masculine forms and the short adjectives read too.
    @ParameterizedTest
    @CsvSource({
        "ru, сказала, FEMALE",
        "ru, вернулся, MALE",
        "ru, смог, MALE",
        "ru, рада, FEMALE",
        "ru, было, NEUTER",
        "uk, сказав, MALE",
        "uk, повернулася, FEMALE",
        "uk, було, NEUTER"
    })
    void genderOf_pastForm_isTheEndingsGender(final String language, final String word, final Gender expected) {
        assertThat(reader(language).genderOf(word)).contains(expected);
    }

    // A particle or an adverb between the pronoun and the verb is allowed.
    @Test
    void firstPerson_particleBetween_counts() {
        final PastTenseReader.FirstPersonCount count = reader("ru").firstPerson(List.of("Я тоже не спал всю ночь."));

        assertThat(count.male()).isEqualTo(1);
        assertThat(count.female()).isZero();
    }

    // The narrator's forms in the Ukrainian narration, each counted once by its ending.
    @ParameterizedTest
    @CsvSource({
        "Я пішла додому., 1, 0",
        "Потім я сказала правду., 1, 0",
        "Я не бачив нікого., 0, 1",
        "Моя пішла додому., 0, 0",
        "Ми пішли додому., 0, 0",
        "Я йду додому., 0, 0"
    })
    void firstPerson_ukrainianNarration_countsByEnding(final String text, final int female, final int male) {
        final PastTenseReader.FirstPersonCount count = reader("uk").firstPerson(List.of(text));

        assertThat(count.female()).isEqualTo(female);
        assertThat(count.male()).isEqualTo(male);
    }

    // The narrator's own form inside a character's quoted speech is not counted.
    @Test
    void firstPerson_insideQuotedSpeech_notCounted() {
        assertThat(reader("ru")
                        .firstPerson(List.of("«Я пришла рано», сказала она."))
                        .forms())
                .isZero();
    }

    // Three forms of which two agree are under the 80 % bar; three of three are verified, with their quotes.
    @ParameterizedTest
    @CsvSource({
        "Я пришла. Я ушла. Я вернулся., false",
        "Я пришла. Я ушла. Я вернулась., true",
        "Я пришла. Я ушла., false"
    })
    void firstPerson_threeFormsAtEightyPercent_verified(final String text, final boolean verified) {
        final PastTenseReader.FirstPersonCount count = reader("ru").firstPerson(List.of(text));

        assertThat(count.verified()).isEqualTo(verified);
    }

    // The quotes of a verified count are the narrator's own words, at most three.
    @Test
    void firstPerson_verifiedCount_keepsTheFirstQuotes() {
        final PastTenseReader.FirstPersonCount count =
                reader("ru").firstPerson(List.of("Я пришла рано.", "Потом я ушла.", "Я вернулась. Я легла."));

        assertThat(count.quotes()).containsExactly("Я пришла", "я ушла", "Я вернулась");
    }

    // A language with no gendered past forms has no reader.
    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "xx"})
    void en_noReader(final String language) {
        assertThat(PastTenseReader.of(language)).isNull();
    }
}
