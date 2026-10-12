package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Project;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * One brief call per window of the story: how many windows the dial asks for, how large each is for the model's real
 * context window, and what each call is told (15i.B3).
 */
class SetupAssistantWindowsTest {

    private static final int MOST_WINDOWS = 5;
    // The reply cap of one window's call; the model's window must hold the prompt and this much reply.
    private static final int REPLY_CAP = 512;
    private static final String REPLY = "{\"genre\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"register\":{\"evidence\":\"\",\"value\":\"neutral\",\"confidence\":0.5},"
            + "\"voice\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"audience\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"narrator\":{\"evidence\":\"\",\"value\":\"unspecified\",\"confidence\":0.5},"
            + "\"narratorGender\":{\"evidence\":\"\",\"value\":\"unknown\",\"name\":\"\",\"confidence\":0.5}}";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private ScriptedChatModel model;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, BriefBooks.story(12, 100));
        model = new ScriptedChatModel();
        model.answerTimes(MOST_WINDOWS, Result.ok(new ChatResponse(REPLY, FinishReason.STOP)));
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private void dialIs(final QualityDial dial) {
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects().save(stored.withBrief(stored.brief().withDial(dial)));
    }

    private Result<BriefSuggestion> suggest(@Nullable final Integer detectedContext) {
        return assistant.suggestBrief(desk.projectId(), model, detectedContext, event -> {});
    }

    private List<String> userMessages() {
        return model.requests().stream()
                .map(SetupAssistantWindowsTest::userMessage)
                .toList();
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    private static int promptTokens(final ChatRequest request) {
        return request.messages().stream()
                .mapToInt(message -> TokenEstimator.estimate(message.content(), "en"))
                .sum();
    }

    // IF one call read all the passages, THEN a single odd stretch of the book could decide every field.
    @ParameterizedTest
    @CsvSource({"FAST,3", "BALANCED,4", "MAX,5"})
    void suggestBrief_dial_asksOneCallPerWindowEachShowingOnePassage(final QualityDial dial, final int windows) {
        dialIs(dial);

        assertThat(suggest(null).isOk()).isTrue();

        assertThat(model.requests()).hasSize(windows);
        assertThat(userMessages())
                .allSatisfy(user ->
                        assertThat(user).containsIgnoringCase("one passage").contains("Passage 1:"))
                .allSatisfy(user -> assertThat(user).doesNotContain("Passage 2:"))
                .doesNotHaveDuplicates();
    }

    // IF the old overload asked, THEN a caller that never learned the context would still get the default windows.
    @Test
    void suggestBrief_noDetectedContext_readsWindowsOfTheDialsOwnSize() {
        dialIs(QualityDial.MAX);

        assertThat(assistant.suggestBrief(desk.projectId(), model).isOk()).isTrue();

        assertThat(model.requests()).hasSize(MOST_WINDOWS);
        assertThat(userMessages())
                .allSatisfy(
                        user -> assertThat(TokenEstimator.estimate(user, "en")).isBetween(3200, 4300));
    }

    // IF the windows ignored the model's real context, THEN a 4k model would be sent 4,000 tokens of passage and
    // fail. 4096 less the 512-token reply is 3,584 for the whole prompt, system message included.
    @Test
    void suggestBrief_detectedContextOf4096_sendsSmallerWindowsThanAnUnknownContextDoes() {
        dialIs(QualityDial.MAX);

        assertThat(suggest(4096).isOk()).isTrue();

        assertThat(model.requests()).hasSize(MOST_WINDOWS);
        assertThat(model.requests())
                .allSatisfy(request -> assertThat(promptTokens(request)).isLessThanOrEqualTo(4096 - REPLY_CAP));
        assertThat(userMessages())
                .allSatisfy(
                        user -> assertThat(TokenEstimator.estimate(user, "en")).isBetween(1500, 2800));
    }

    @Test
    void suggestBrief_detectedContextOf16384_readsTheFullDialWindow() {
        dialIs(QualityDial.MAX);

        assertThat(suggest(16384).isOk()).isTrue();

        assertThat(userMessages())
                .allSatisfy(
                        user -> assertThat(TokenEstimator.estimate(user, "en")).isBetween(3200, 4300));
    }

    // IF the schema asked for the label first, THEN the model would decide before it had named what it saw.
    @Test
    void suggestBrief_request_putsEachFieldsEvidenceBeforeItsValueInTheSchemaAndTheExample() {
        assertThat(suggest(null).isOk()).isTrue();

        final ChatRequest request = model.requests().getFirst();
        final String schema = request.responseFormat().jsonSchema();
        final String system = request.messages().getFirst().content();
        assertThat(schema.indexOf("\"evidence\"")).isNotNegative().isLessThan(schema.indexOf("\"value\""));
        assertThat(system).contains("\"genre\":{\"evidence\"");
    }

    // IF a chosen language were still asked for, THEN every window would pay for an answer nobody uses.
    @Test
    void suggestBrief_sourceLanguageChosen_asksNoLanguage() {
        assertThat(suggest(null).isOk()).isTrue();

        assertThat(model.requests())
                .allSatisfy(request -> assertThat(request.messages())
                        .noneMatch(message -> message.content().contains("\"language\"")));
    }
}
