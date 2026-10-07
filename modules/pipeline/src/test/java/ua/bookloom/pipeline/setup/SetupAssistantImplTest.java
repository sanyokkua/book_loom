package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The model's proposals for the file name and the Book Brief: what it is shown, and what is kept from its answer. */
class SetupAssistantImplTest {

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
        assistant =
                new SetupAssistantImpl(desk.projects(), desk.openProjects(), new PromptTemplates(), new ObjectMapper());
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private String userMessage() {
        return model.requests().getFirst().messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    // IF the file name were taken from the model unchecked, THEN a reply could name a path outside the folder.
    @Test
    void suggestFileName_modelReply_isShownTheOriginalNameAndReturnedCleaned() {
        model.answer(reply("{\"target\":\"Джордж Орвел. Ферма тварин. 1945\"}"));

        final Result<String> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.data()).isEqualTo("Джордж Орвел. Ферма тварин. 1945");
        assertThat(userMessage()).contains("File name: Book");
    }

    @Test
    void suggestFileName_replyWithPathCharacters_isMadeASafeName() {
        model.answer(reply("{\"target\":\"../etc/Книга: частина *1*?\"}"));

        assertThat(assistant.suggestFileName(desk.projectId(), model).data()).isEqualTo("etc Книга частина 1");
    }

    @Test
    void suggestFileName_replyThatIsNoName_isAValidationError() {
        model.answer(reply("{\"target\":\"///\"}"));

        final Result<String> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"  A. B. 1999 |A. B. 1999", "x\\y|x y", "..hidden..|hidden"})
    void cleanFileName_proposal_losesWhatAFileNameCannotHold(final String raw, final String expected) {
        assertThat(SetupAssistantImpl.cleanFileName(raw)).isEqualTo(expected);
    }

    // IF the name were cut by characters, THEN 150 Cyrillic letters would be 300 bytes and no file system would take
    // it.
    @Test
    void cleanFileName_longCyrillicName_isCutToTheByteLimitBetweenWholeCharacters() {
        final String cut = SetupAssistantImpl.cleanFileName("Ї".repeat(150));

        assertThat(cut.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(200);
        assertThat(cut).isEqualTo("Ї".repeat(100));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"CON|CON_", "nul.txt|nul.txt_", "Console|Console", "A\u202Eb|A b"})
    void cleanFileName_deviceNamesAndInvisibleFormatCharacters_areMadeSafe(final String raw, final String expected) {
        assertThat(SetupAssistantImpl.cleanFileName(raw)).isEqualTo(expected);
    }

    // IF the model were not shown the opening of the book, THEN it could only guess from the title.
    @Test
    void suggestBrief_modelReply_isShownTheOpeningAndMappedToTheBriefsChoices() {
        model.answer(reply("{\"genre\":\"noir\",\"register\":\"casual\",\"voice\":\"first-person, short sentences\","
                + "\"audience\":\"adult readers\",\"narrator\":\"first\",\"narratorGender\":\"female\"}"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion)
                .isEqualTo(new BriefSuggestion(
                        "noir",
                        Register.CASUAL,
                        "first-person, short sentences",
                        "adult readers",
                        NarratorPerson.FIRST,
                        Gender.FEMALE));
        assertThat(userMessage())
                .contains("Opening paragraphs:", "The rain had not stopped")
                .doesNotContain("\nOk.\n");
    }

    @Test
    void suggestBrief_unknownWordsInTheReply_fallBackToTheNeutralChoices() {
        model.answer(reply("{\"genre\":\"\",\"register\":\"wild\",\"voice\":\"\",\"audience\":\"\","
                + "\"narrator\":\"fourth\",\"narratorGender\":\"robot\"}"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_replyThatIsNotJson_isAValidationError() {
        model.answer(reply("I think it is a noir."));

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void suggestBrief_unknownProject_isAValidationErrorAndAsksNothing() {
        final Result<BriefSuggestion> result = assistant.suggestBrief("no-such-project", model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }
}
