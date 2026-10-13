package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.Register;

/**
 * The model's proposal of the Book Brief end to end over a scripted model (15i.B4): what the model knows of the book,
 * one reading per window, and one synthesis call over the facts, the accepted knowledge and the verified readings; each
 * field is then decided once, on a basis the code checks, and says what it stands on.
 */
class SetupAssistantBriefTest extends SetupAssistantBriefTestBase {
    // IF each field were decided by its own call, THEN six calls could disagree; one synthesis reads everything once.
    @Test
    void suggestBrief_titledBook_asksWhatTheModelKnowsThenEachWindowThenOneSynthesis() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        suggested();

        assertThat(kinds())
                .containsExactly(
                        CallKind.BOOK_KNOWLEDGE,
                        CallKind.BRIEF_SUGGESTION,
                        CallKind.BRIEF_SUGGESTION,
                        CallKind.BRIEF_SUGGESTION,
                        CallKind.BRIEF_SUGGESTION,
                        CallKind.BRIEF_SYNTHESIS);
    }

    // Each field stands on what the code can check, and says so: genre on three passages, audience on what the model
    // knows, register on four passages, the narrator on the detector's count; the voice one window offered is not
    // kept, because what the model knows of the book does not say it.
    @Test
    void suggestBrief_windowsKnowledgeAndSynthesis_decideEachFieldOnItsOwnBasis() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.genre()).isEqualTo("Crime fiction");
        assertThat(suggestion.evidenceOf(BriefField.GENRE))
                .hasValueSatisfying(proof -> assertThat(proof.describe()).isEqualTo("from 3 of 4 passages"));
        assertThat(suggestion.audience()).isEqualTo("adult");
        assertThat(suggestion.evidenceOf(BriefField.AUDIENCE))
                .hasValueSatisfying(
                        proof -> assertThat(proof.describe()).isEqualTo("from what the model knows of this book"));
        assertThat(suggestion.voiceEra()).isNull();
        assertThat(suggestion.evidenceOf(BriefField.VOICE))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isFalse());
        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .hasValueSatisfying(proof -> assertThat(proof.describe()).isEqualTo("from 4 of 4 passages"));
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.THIRD);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
        assertThat(suggestion.languageProposal()).isNull();
    }

    @Test
    void suggestBrief_synthesisRequest_carriesTheFactsTheAcceptedKnowledgeAndTheVerifiedReadings() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        suggested();

        assertThat(synthesisUserMessage()).contains(TITLE, "crime novel", "crime story", QUOTE);
    }

    // IF a refused answer reached the synthesis, THEN what the echo check threw out would still steer the genre.
    @Test
    void suggestBrief_knowledgeRefusedForLowConfidence_isNotShownToTheSynthesis() {
        knowledgeAnswers(KNOWN.replace("\"crime novel\"", "\"pirate romance\"").replace("0.9}", "0.3}"));
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        final BriefSuggestion suggestion = suggested();

        assertThat(synthesisUserMessage()).doesNotContain("pirate romance");
        assertThat(suggestion.audience()).isNull();
    }

    // IF an unproven reading reached the synthesis, THEN a line the book never had could still decide its genre.
    @Test
    void suggestBrief_windowReadingWithAQuoteTheBookLacks_isNotShownToTheSynthesis() {
        knowledgeAnswers(KNOWN);
        windowsAnswer(
                window("crime story", "casual"),
                window("", "space opera", "A line this book never had", "casual"),
                window("crime story", "casual"),
                window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        suggested();

        assertThat(synthesisUserMessage()).contains("crime story").doesNotContain("space opera");
    }

    // IF a failed synthesis failed the whole suggestion, THEN four good readings of the register would be thrown away.
    @Test
    void suggestBrief_synthesisCallFails_keepsTheWindowFieldsAndLeavesTheSynthesizedOnesUncertain() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        model.answerTo(
                "brief-synthesis",
                Result.err(AppError.of(ErrorCode.unreachable, "Down", "The server is not answering.")));

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.audience()).isNull();
        assertThat(suggestion.evidenceOf(BriefField.GENRE))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isFalse());
    }

    @Test
    void suggestBrief_registerSplitTwoAndTwo_isNeutralAndUncertain() {
        knowledgeAnswers(KNOWN);
        windowsAnswer(
                window("crime story", "formal"),
                window("crime story", "casual"),
                window("crime story", "formal"),
                window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isFalse());
    }

    // IF the windows' language were applied, THEN the model, not the person, would choose what the book is read as.
    @Test
    void suggestBrief_autoSourceAndThreeWindowsInFrench_proposesFrenchAndLeavesTheBriefOnAuto() {
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withLanguages(null, "uk")));
        knowledgeAnswers(KNOWN);
        windowsAnswer(
                window("\"language\":\"fr\",", "crime story", QUOTE, "casual"),
                window("\"language\":\"fr\",", "crime story", QUOTE, "casual"),
                window("\"language\":\"en\",", "crime story", QUOTE, "casual"),
                window("\"language\":\"fr\",", "crime story", QUOTE, "casual"));
        synthesisAnswers(SYNTHESIS);

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.languageProposal()).isEqualTo("fr");
        assertThat(desk.projects()
                        .find(desk.projectId())
                        .data()
                        .orElseThrow()
                        .brief()
                        .sourceLanguage())
                .isNull();
    }

    @Test
    void suggestBrief_chosenSourceAndWindowsThatNameALanguage_proposesNone() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("\"language\":\"fr\",", "crime story", QUOTE, "casual"));
        synthesisAnswers(SYNTHESIS);

        assertThat(suggested().languageProposal()).isNull();
    }

    @Test
    void suggestBrief_everyWindowReplyIsNotJson_isAValidationErrorAndAsksNoSynthesis() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers("I think it is a noir.");

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(requestsOf(CallKind.BRIEF_SYNTHESIS)).isEmpty();
    }

    @Test
    void suggestBrief_unknownProject_isAValidationErrorAndAsksNothing() {
        final Result<BriefSuggestion> result = assistant.suggestBrief("no-such-project", model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    // IF a first-person book carried a "third-person limited" voice note, THEN every prompt would contradict itself.
    @Test
    void suggestBrief_narratorAlreadySet_isKeptAndAContradictingVoiceNoteIsDropped() {
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects()
                .save(stored.withBrief(stored.brief().withNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE))));
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(
                SYNTHESIS.replace(
                        "\"voice\":{\"basis\":{\"knowledge\":false,\"passages\":[2]},\"value\":\"terse and wry\"}",
                        "\"voice\":{\"basis\":{\"knowledge\":false,\"passages\":[1,2]},\"value\":\"third-person limited, terse\"}"));

        final BriefSuggestion suggestion = suggested();

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.voiceEra()).isNull();
        assertThat(suggestion.genre()).isEqualTo("Crime fiction");
        assertThat(suggestion.evidence()).doesNotContainKeys(BriefField.NARRATOR, BriefField.VOICE);
    }

    // IF the window prompt invited the model's memory of the book, THEN a famous title would outweigh what the text
    // shows; what the model knows is asked apart, and checked.
    @Test
    void suggestBrief_windowSystemMessage_asksOnlyFromThePassageAndFixesNoGenderInItsExample() {
        knowledgeAnswers(KNOWN);
        everyWindowAnswers(window("crime story", "casual"));
        synthesisAnswers(SYNTHESIS);

        suggested();

        final String system = requestsOf(CallKind.BRIEF_SUGGESTION)
                .getFirst()
                .messages()
                .getFirst()
                .content();
        assertThat(system)
                .doesNotContain("what you know of this book", "\"narratorGender\":\"male\"")
                .contains("\"narratorGender\":{\"evidence\"");
    }
}
