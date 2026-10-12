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
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The model's proposal of the Book Brief: what it reads of the book, and what is kept from the windows' answers. */
class SetupAssistantBriefTest {

    // Balanced reads four windows; the book is long enough (about 27,000 tokens) for every dial's windows.
    private static final int WINDOWS = 4;

    @TempDir
    private Path tempDir;

    private Desk desk;
    private ScriptedChatModel model;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, BriefBooks.story(12, 100));
        model = new ScriptedChatModel();
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
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

    private static final String QUOTE = BriefBooks.QUOTE;

    private List<String> userMessages() {
        return model.requests().stream()
                .map(request -> request.messages().stream()
                        .filter(message -> message.role() == ChatRole.USER)
                        .map(ChatMessage::content)
                        .findFirst()
                        .orElseThrow())
                .toList();
    }

    /** Scripts the same reply for every window of the default (Balanced) dial. */
    private void everyWindowAnswers(final String reply) {
        model.answerTimes(WINDOWS, reply(reply));
    }

    // IF the model were shown only the opening once, THEN one reading of 590 words would decide the whole brief.
    @Test
    void suggestBrief_windowsAgreeWithQuotesInTheBook_keepsEveryFieldWithItsEvidence() {
        everyWindowAnswers(brief("noir", "casual", "first-person, short sentences", "first", QUOTE, 0.9));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isEqualTo("noir");
        assertThat(suggestion.register()).isEqualTo(Register.CASUAL);
        assertThat(suggestion.voiceEra()).isEqualTo("first-person, short sentences");
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isTrue());
        assertThat(model.requests()).hasSize(WINDOWS);
        assertThat(userMessages())
                .allSatisfy(
                        user -> assertThat(user).contains("Passage 1:", "The rain had not stopped", "Dialogue share"));
        assertThat(userMessages()).doesNotHaveDuplicates();
    }

    // IF the prompt invited the model's memory of the book, THEN a famous title would outweigh what the text shows.
    @Test
    void suggestBrief_systemMessage_asksOnlyFromThePassageAndFixesNoGenderInItsExample() {
        everyWindowAnswers(brief("noir", "casual", "terse", "first", QUOTE, 0.9));

        assertThat(assistant.suggestBrief(desk.projectId(), model).isOk()).isTrue();

        final String system = model.requests().getFirst().messages().getFirst().content();
        assertThat(system)
                .doesNotContain("what you know of this book", "\"narratorGender\":\"male\"")
                .contains("\"narratorGender\":{\"evidence\"");
    }

    // The combine of today (unanimity) still runs over K answers; 15i.B4 replaces it by a majority.
    @Test
    void suggestBrief_windowsDisagree_leavesTheFieldNeutralAndUncertain() {
        final String casual = brief("noir", "casual", "terse", "first", QUOTE, 0.9);
        final String formal = brief("noir", "formal", "terse", "first", QUOTE, 0.9);
        model.answer(reply(formal)).answer(reply(casual)).answer(reply(formal)).answer(reply(casual));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.evidenceOf(BriefField.REGISTER))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isFalse());
        assertThat(suggestion.genre()).isEqualTo("noir");
    }

    // IF a quote were taken on trust, THEN a model could cite a line the book never had.
    @Test
    void suggestBrief_quoteNotInTheWindow_isNotKept() {
        final String invented = brief("noir", "casual", "terse", "first", "A line this book never had", 0.9);
        model.answer(reply(invented))
                .answer(reply(invented))
                .answer(reply(invented))
                .answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.9)));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.evidenceOf(BriefField.GENRE))
                .hasValueSatisfying(proof -> assertThat(proof.isKept()).isFalse());
    }

    @Test
    void suggestBrief_lowConfidence_isNotKept() {
        final String unsure = brief("noir", "casual", "terse", "first", QUOTE, 0.3);
        model.answer(reply(unsure))
                .answer(reply(unsure))
                .answer(reply(unsure))
                .answer(reply(brief("noir", "casual", "terse", "first", QUOTE, 0.9)));

        assertThat(assistant.suggestBrief(desk.projectId(), model).data().register())
                .isEqualTo(Register.NEUTRAL);
    }

    @Test
    void suggestBrief_unknownWordsInTheReply_fallBackToTheNeutralChoices() {
        everyWindowAnswers("{\"genre\":\"\",\"register\":\"wild\",\"voice\":\"\",\"audience\":\"\","
                + "\"narrator\":\"fourth\",\"narratorGender\":\"robot\"}");

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_replyThatIsNotJson_isAValidationError() {
        everyWindowAnswers("I think it is a noir.");

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void suggestBrief_firstCallFails_returnsItsErrorAndAsksNoFurtherWindow() {
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
        everyWindowAnswers(brief("noir", "casual", "third-person limited, terse", "third", QUOTE, 0.9));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.voiceEra()).isNull();
        assertThat(suggestion.genre()).isEqualTo("noir");
        assertThat(suggestion.evidence()).doesNotContainKeys(BriefField.NARRATOR, BriefField.VOICE);
    }
}
