package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * The narrator's gender is taken only from a quote the code can check: a name the book calls the narrator by, whose
 * gender the glossary or the first-name list holds, or a word of address the language file lists.
 */
class NarratorGenderEvidenceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BookSample ENGLISH = new BookSample(
            List.of(
                    "\"Hang on to your ass, Jack,\" Bobby said. I lit a cigarette and watched the rain.",
                    "\"Molly, you came back,\" the barman said to me. \"Yes, ma'am, right away,\" the boy answered me."
                            + " \"Sir, the car is ready,\" the driver told me. \"Zorbu, wake up,\" somebody said."),
            40);
    private static final BookSample UKRAINIAN = new BookSample(
            List.of("«Пане, ви забули капелюха», — гукнув мені швейцар. «Дівчино, зачекай!» — крикнула мені сусідка."),
            60);
    private static final GlossaryEntry JACK =
            new GlossaryEntry("g1", "p1", "Jack", null, TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry JACK_PLACE =
            new GlossaryEntry("g2", "p1", "Jack", null, TermType.PLACE, Gender.MALE, false);

    private static Answer read(
            final String value,
            final String quote,
            final String name,
            final BookSample sample,
            final NarratorGenderEvidence evidence)
            throws JsonProcessingException {
        final String reply = "{\"narratorGender\":{\"value\":\"" + value + "\",\"evidence\":\""
                + quote.replace("\"", "\\\"") + "\",\"name\":\"" + name + "\",\"confidence\":0.8}}";
        return BriefReplies.read(MAPPER.readTree(reply), sample, evidence).get(BriefField.NARRATOR_GENDER);
    }

    private static NarratorGenderEvidence english(final GlossaryEntry... glossary) {
        return new NarratorGenderEvidence(List.of(glossary), "en");
    }

    // IF a vocative naming a glossary person were thrown away, THEN Burning Chrome's Jack would stay of unknown gender.
    @Test
    void read_vocativeOfAGlossaryPerson_takesThePersonsGender() throws JsonProcessingException {
        final Answer answer = read("male", "Hang on to your ass, Jack", "Jack", ENGLISH, english(JACK));

        assertThat(answer).isEqualTo(new Answer("male", "Hang on to your ass, Jack"));
    }

    // The gender is the name's, never the model's word for it.
    @Test
    void read_listedFirstNameTheModelGenderedWrongly_takesTheListedGender() throws JsonProcessingException {
        final Answer answer = read("male", "Molly, you came back", "Molly", ENGLISH, english());

        assertThat(answer).isEqualTo(new Answer("female", "Molly, you came back"));
    }

    // IF a quote were taken on trust, THEN a model could name the narrator from a line the book never had.
    @Test
    void read_vocativeQuoteNotInTheSample_leavesTheGenderUnknown() throws JsonProcessingException {
        final Answer answer = read("male", "Hold on to your hat, Jack", "Jack", ENGLISH, english(JACK));

        assertThat(answer).isEqualTo(new Answer("unknown", ""));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // Neither in the glossary nor on the first-name list.
                "Zorbu, wake up|Zorbu",
                // Jack is left off the list on purpose, and the glossary's Jack is a place, not a person.
                "Hang on to your ass, Jack|Jack",
                // A listed name that is not in the quote.
                "Hang on to your ass, Jack|Molly"
            })
    void read_nameTheCodeCannotGender_leavesTheGenderUnknown(final String quote, final String name)
            throws JsonProcessingException {
        final Answer answer = read("male", quote, name, ENGLISH, english(JACK_PLACE));

        assertThat(answer).isEqualTo(new Answer("unknown", ""));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Yes, ma'am, right away|female", "Sir, the car is ready|male"})
    void read_englishAddressTerm_takesTheTermsGender(final String quote, final String gender)
            throws JsonProcessingException {
        final Answer answer = read(gender, quote, "", ENGLISH, english());

        assertThat(answer).isEqualTo(new Answer(gender, quote));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"Пане, ви забули капелюха|male", "Дівчино, зачекай!|female"})
    void read_ukrainianVocativeOfAddress_takesTheTermsGender(final String quote, final String gender)
            throws JsonProcessingException {
        final Answer answer = read(gender, quote, "", UKRAINIAN, new NarratorGenderEvidence(List.of(), "uk"));

        assertThat(answer).isEqualTo(new Answer(gender, quote));
    }

    // IF the model's word were enough, THEN "female" from an ordinary sentence would decide every verb of the book.
    @Test
    void read_genderWithNoNameAndNoAddressTerm_leavesTheGenderUnknown() throws JsonProcessingException {
        final Answer answer = read("female", "I lit a cigarette and watched the rain", "", ENGLISH, english());

        assertThat(answer).isEqualTo(new Answer("unknown", ""));
    }

    @Test
    void read_noEvidence_isTheNeutralUnknown() throws JsonProcessingException {
        final Answer answer = read("unknown", "", "", ENGLISH, english());

        assertThat(answer).isEqualTo(new Answer("unknown", ""));
    }

    @Test
    void genderOf_quoteWithTermsOfBothGenders_provesNothing() {
        assertThat(english().genderOf("Sir and ma'am, welcome", "")).isEmpty();
    }

    @Test
    void genderOf_addressTermInAnotherLanguage_provesNothing() {
        assertThat(new NarratorGenderEvidence(List.of(), "uk").genderOf("Sir, the car is ready", ""))
                .isEmpty();
    }

    @Test
    void genderOf_glossaryPersonByOneWordOfItsTerm_takesItsGender() {
        final GlossaryEntry rikki =
                new GlossaryEntry("g3", "p1", "Rikki Wildside", null, TermType.CHARACTER, Gender.FEMALE, false);

        assertThat(english(rikki).genderOf("Rikki, come here", "Rikki")).contains(Gender.FEMALE);
    }
}
