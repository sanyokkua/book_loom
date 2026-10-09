package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The model's proposal of the Book Brief: what it reads of the book, and what is kept from the two samples' answers. */
class SetupAssistantBriefTest {

    private static final String LONG_PARAGRAPH =
            "The rain had not stopped for three days, and I was beginning to think the city had forgotten the sun.";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private ScriptedChatModel model;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(
                tempDir, LONG_PARAGRAPH + "\n\n" + LONG_PARAGRAPH + " Nobody came.\n\nOk.\n\n" + LONG_PARAGRAPH + "\n");
        model = new ScriptedChatModel();
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                java.time.Clock.systemUTC());
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private static String brief(
            final String genre,
            final String register,
            final String voice,
            final String narrator,
            final String quote,
            final double confidence) {
        return "{" + field("genre", genre, quote, confidence) + "," + field("register", register, quote, confidence)
                + "," + field("voice", voice, quote, confidence) + "," + field("audience", "", "", confidence) + ","
                + field("narrator", narrator, quote, confidence) + "," + field("narratorGender", "unknown", "", 0.5)
                + "}";
    }

    private static String field(final String name, final String value, final String quote, final double confidence) {
        return "\"" + name + "\":{\"value\":\"" + value + "\",\"evidence\":\"" + quote + "\",\"confidence\":"
                + confidence + "}";
    }

    private static final String QUOTE = "The rain had not stopped for three days";

    private List<String> userMessages() {
        return model.requests().stream()
                .map(request -> request.messages().stream()
                        .filter(message -> message.role() == ChatRole.USER)
                        .map(ChatMessage::content)
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    // IF the model were shown only the opening once, THEN one reading of 590 words would decide the whole brief.
    @Test
    void suggestBrief_twoSamplesAgreeWithQuotesInTheBook_keepsEveryFieldWithItsEvidence() {
        final String reply = brief("noir", "casual", "first-person, short sentences", "first", QUOTE, 0.9);
        model.answer(reply(reply)).answer(reply(reply));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isEqualTo("noir");
        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.voiceEra()).isEqualTo("first-person, short sentences");
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER)).contains(new FieldEvidence(List.of(QUOTE), 2, 2));
        assertThat(suggestion.evidenceOf(BriefField.NARRATOR_GENDER)).contains(new FieldEvidence(List.of(), 2, 2));
        assertThat(model.requests()).hasSize(2);
        assertThat(userMessages().getFirst())
                .contains("Sample (passages", "Passage 1:", "The rain had not stopped", "Dialogue share")
                .doesNotContain("\nOk.\n");
        assertThat(userMessages().getFirst()).isNotEqualTo(userMessages().getLast());
    }

    // IF the prompt invited the model's memory of the book, THEN a famous title would outweigh what the text shows.
    @Test
    void suggestBrief_systemMessage_asksOnlyFromThePassagesAndFixesNoGenderInItsExample() {
        final String reply = brief("noir", "casual", "terse", "first", QUOTE, 0.9);
        model.answer(reply(reply)).answer(reply(reply));

        assertThat(assistant.suggestBrief(desk.projectId(), model).isOk()).isTrue();

        final String system = model.requests().getFirst().messages().getFirst().content();
        assertThat(system)
                .doesNotContain("what you know of this book", "\"narratorGender\":\"male\"")
                .contains("\"narratorGender\":{\"value\":\"unknown\"");
    }

    @Test
    void suggestBrief_samplesDisagree_leavesTheFieldNeutralAndUncertain() {
        model.answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.9)))
                .answer(reply(brief("noir", "formal", "terse", "first", QUOTE, 0.9)));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER)).contains(new FieldEvidence(List.of(), 1, 2));
        assertThat(suggestion.genre()).isEqualTo("noir");
    }

    // IF a quote were taken on trust, THEN a model could cite a line the book never had.
    @Test
    void suggestBrief_quoteNotInTheSample_isNotKept() {
        model.answer(reply(brief("noir", "casual", "terse", "first", "A line this book never had", 0.9)))
                .answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.9)));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.evidenceOf(BriefField.GENRE)).contains(new FieldEvidence(List.of(), 1, 2));
    }

    @Test
    void suggestBrief_lowConfidence_isNotKept() {
        model.answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.3)))
                .answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.9)));

        assertThat(assistant.suggestBrief(desk.projectId(), model).data().register())
                .isEqualTo(Register.NEUTRAL);
    }

    @Test
    void suggestBrief_unknownWordsInTheReply_fallBackToTheNeutralChoices() {
        final String reply = "{\"genre\":\"\",\"register\":\"wild\",\"voice\":\"\",\"audience\":\"\","
                + "\"narrator\":\"fourth\",\"narratorGender\":\"robot\"}";
        model.answer(reply(reply)).answer(reply(reply));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_replyThatIsNotJson_isAValidationError() {
        model.answer(reply("I think it is a noir.")).answer(reply("Still a noir."));

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void suggestBrief_firstCallFails_returnsItsErrorAndAsksNoSecondSample() {
        model.answer(Result.err(AppError.of(ErrorCode.unreachable, "Down", "The server is not answering.")));

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error().code()).isEqualTo(ErrorCode.unreachable);
        assertThat(model.requests()).hasSize(1);
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
        final String reply = brief("noir", "casual", "third-person limited, terse", "third", QUOTE, 0.9);
        model.answer(reply(reply)).answer(reply(reply));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.voiceEra()).isNull();
        assertThat(suggestion.genre()).isEqualTo("noir");
        assertThat(suggestion.evidence()).doesNotContainKeys(BriefField.NARRATOR, BriefField.VOICE);
    }
}
