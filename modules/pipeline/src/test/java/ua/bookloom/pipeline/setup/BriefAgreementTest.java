package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/** A brief field is kept only when both samples of the book say the same. */
class BriefAgreementTest {

    private static Map<BriefField, Answer> register(final String value, final String quote) {
        return Map.of(BriefField.REGISTER, new Answer(value, quote));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "GENRE|science fiction|cyberpunk science fiction|true",
                "GENRE|Noir|noir|true",
                "GENRE|noir|science fiction|false",
                "GENRE|noir||false",
                "AUDIENCE|adult readers|adult readers of crime fiction|true",
                "AUDIENCE|adult readers|young readers|false",
                "VOICE|first-person, short sentences|terse first-person narration|true",
                "VOICE|first-person, short sentences|ornate Victorian prose|false",
                "REGISTER|casual|formal|false",
                "NARRATOR|first|first|true"
            })
    void agree_twoAnswers_agreeByTheFieldsRule(
            final BriefField field, final String one, final String other, final boolean expected) {
        assertThat(BriefAgreement.agree(field, new Answer(one, ""), new Answer(other == null ? "" : other, "")))
                .isEqualTo(expected);
    }

    @Test
    void combine_bothSamplesSayCasualWithQuotes_keepsItWithBothQuotes() {
        final BriefSuggestion suggestion = BriefAgreement.combine(
                List.of(register("casual", "Hang on, man"), register("casual", "yeah, sure")), 2);

        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .contains(new FieldEvidence(List.of("Hang on, man", "yeah, sure"), 2, 2));
    }

    // IF one sample's answer were kept, THEN e4b's formal and 26b's casual reading of one text would both be "sure".
    @Test
    void combine_samplesDisagree_leavesTheFieldNeutralAndUncertain() {
        final BriefSuggestion suggestion = BriefAgreement.combine(
                List.of(register("casual", "Hang on, man"), register("formal", "Thus spake he")), 2);

        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER)).contains(new FieldEvidence(List.of(), 1, 2));
    }

    @Test
    void combine_oneReplyUnreadable_leavesEveryFieldUncertain() {
        final BriefSuggestion suggestion =
                BriefAgreement.combine(Arrays.asList(register("casual", "Hang on, man"), null), 2);

        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.evidence().values()).noneMatch(FieldEvidence::isAgreed);
    }

    @Test
    void combine_onlyOneSampleTaken_countsTheMissingOneAsNoAnswer() {
        final BriefSuggestion suggestion = BriefAgreement.combine(List.of(register("casual", "Hang on, man")), 2);

        assertThat(suggestion.evidenceOf(BriefField.REGISTER)).contains(new FieldEvidence(List.of(), 1, 2));
    }

    @Test
    void combine_textFieldsAgree_keepsTheShorterPhrase() {
        final BriefSuggestion suggestion = BriefAgreement.combine(
                List.of(
                        Map.of(BriefField.GENRE, new Answer("cyberpunk science fiction", "the matrix")),
                        Map.of(BriefField.GENRE, new Answer("science fiction", "orbital station"))),
                2);

        assertThat(suggestion.genre()).isEqualTo("science fiction");
    }
}
