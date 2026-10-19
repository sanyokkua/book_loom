package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * A name in a quote is the narrator's only when the quote speaks to the narrator or the narrator gives it (15i.B4):
 * a vocative the narrator says ("Mind the step, Lucy," I said) names the person spoken to, so it is no vote for the
 * narrator's gender, while the same words said to the narrator by someone else, or the narrator's own introduction,
 * are. Lucy is on the bundled first-name list as a woman's name.
 */
class BriefRepliesNarratorAddressTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final NarratorGenderEvidence GENDERS = new NarratorGenderEvidence(List.of(), "en");
    private static final String REST =
            "The rain kept on along the river road. I carried the lantern and counted the steps to the gate.";

    private static Map<BriefField, Answer> read(final String passage, final String quote) throws Exception {
        final JsonNode reply = MAPPER.readTree("{\"narrator\":{\"evidence\":\"I carried the lantern\","
                + "\"value\":\"first\",\"confidence\":0.9},"
                + "\"narratorGender\":{\"evidence\":\"" + quote.replace("\"", "\\\"") + "\",\"value\":\"female\","
                + "\"name\":\"Lucy\",\"confidence\":0.9}}");
        return BriefReplies.read(reply, new BookSample(List.of(passage, REST), 0), GENDERS);
    }

    // IF the narrator's own vocative counted, THEN a man who says "Mind the step, Lucy" would be told as a woman.
    @Test
    void read_vocativeTheNarratorSaysAfterTheQuote_isNoVoteForTheNarratorsGender() throws Exception {
        final Map<BriefField, Answer> answers =
                read("\"Mind the step, Lucy,\" I said, and held the lantern higher.", "Mind the step, Lucy");

        assertThat(Objects.requireNonNull(answers.get(BriefField.NARRATOR_GENDER))
                        .value())
                .isEqualTo("unknown");
    }

    @Test
    void read_vocativeTheNarratorSaysBeforeTheQuote_isNoVoteForTheNarratorsGender() throws Exception {
        final Map<BriefField, Answer> answers =
                read("I turned to her and said, \"Mind the step, Lucy.\"", "Mind the step, Lucy");

        assertThat(Objects.requireNonNull(answers.get(BriefField.NARRATOR_GENDER))
                        .value())
                .isEqualTo("unknown");
    }

    @Test
    void read_vocativeSaidToTheNarratorByAnother_isAVoteForTheNamesGender() throws Exception {
        final Map<BriefField, Answer> answers =
                read("\"Mind the step, Lucy,\" Tom said to me, and held the lantern higher.", "Mind the step, Lucy");

        assertThat(Objects.requireNonNull(answers.get(BriefField.NARRATOR_GENDER))
                        .value())
                .isEqualTo("female");
        assertThat(Objects.requireNonNull(answers.get(BriefField.NARRATOR_GENDER))
                        .quote())
                .isEqualTo("Mind the step, Lucy");
    }

    // The narrator giving her own name is the one case where the narrator's speech names the narrator.
    @Test
    void read_narratorIntroducesHerself_isAVoteForTheNamesGender() throws Exception {
        final Map<BriefField, Answer> answers =
                read("\"I'm Lucy,\" I said, and held out my hand to the ferryman.", "I'm Lucy");

        assertThat(Objects.requireNonNull(answers.get(BriefField.NARRATOR_GENDER))
                        .value())
                .isEqualTo("female");
    }
}
