package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * A register is the narration's (15i.B4 gate): a window that quotes a line a character speaks as its register shows how
 * that character talks, so the reading is no answer and counts against every register in the majority; a line of the
 * narration stands, and so does a genre quoted from a character's speech.
 */
class BriefRepliesRegisterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final NarratorGenderEvidence GENDERS = new NarratorGenderEvidence(List.of(), "en");
    private static final BookSample SAMPLE = new BookSample(List.of("""
                    "Shut the gate before the goats get out, will you?" Orla called from the yard.
                    Whereupon the assembled company, being much fatigued, withdrew to the parlour."""), 40, true, "en");

    private static Map<BriefField, Answer> read(final String field, final String value, final String quote)
            throws Exception {
        final JsonNode reply = MAPPER.readTree(
                "{\"" + field + "\":{\"evidence\":\"" + quote + "\",\"value\":\"" + value + "\",\"confidence\":0.9}}");
        return BriefReplies.read(reply, SAMPLE, GENDERS);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "casual|Shut the gate before the goats get out, will you?",
                "casual|before the goats get out",
                "formal|Whereupon the assembled company, being much fatigued, withdrew"
            })
    void read_registerQuote_standsOnlyWhenItIsNarration(final String value, final String quote) throws Exception {
        final Map<BriefField, Answer> answers = read("register", value, quote);

        assertThat(answers.containsKey(BriefField.REGISTER)).isEqualTo("formal".equals(value));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "pastoral drama|Shut the gate before the goats get out, will you?",
                "pastoral drama|Orla called from the yard"
            })
    void read_genreQuote_standsInSpeechAndNarrationAlike(final String value, final String quote) throws Exception {
        final Map<BriefField, Answer> answers = read("genre", value, quote);

        assertThat(answers.get(BriefField.GENRE)).isEqualTo(new Answer(value, quote));
    }
}
