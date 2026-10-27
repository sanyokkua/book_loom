package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * The brief's synthesis request as the model receives it (15i.B4): a call of its own kind and format, deterministic,
 * short, held to its schema, announced like every setup call, and given the window's verified reading to decide from.
 * The book is short and untitled, so it is one window and no knowledge question.
 */
class SetupAssistantSynthesisRequestTest {

    private static final String QUOTE = "The rain had not stopped for three days";
    private static final String BOOK = QUOTE + ", and the lamps along the quay burned all afternoon.\n\n"
            + "The ferryman counted his fares twice and closed the shed before the tide turned.\n\n"
            + "Nobody came down the lane that evening, and the gulls kept to the roofs.\n";
    private static final String WINDOW = "{\"genre\":{\"evidence\":\"" + QUOTE
            + "\",\"value\":\"quiet harbour mystery\",\"confidence\":0.9},"
            + "\"register\":{\"evidence\":\"\",\"value\":\"neutral\",\"confidence\":0.5},"
            + "\"voice\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"audience\":{\"evidence\":\"\",\"value\":\"\",\"confidence\":0.5},"
            + "\"narrator\":{\"evidence\":\"\",\"value\":\"unspecified\",\"confidence\":0.5},"
            + "\"narratorGender\":{\"evidence\":\"\",\"value\":\"unknown\",\"name\":\"\",\"confidence\":0.5}}";
    private static final String SYNTHESIS = "{\"genre\":{\"basis\":{\"knowledge\":false,\"passages\":[1]},"
            + "\"value\":\"Mystery\"},\"audience\":{\"basis\":{\"knowledge\":false,\"passages\":[]},\"value\":\"\"},"
            + "\"voice\":{\"basis\":{\"knowledge\":false,\"passages\":[]},\"value\":\"\"}}";

    @TempDir
    private Path tempDir;

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final List<JobEvent> events = new ArrayList<>();
    private Desk desk;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, BOOK);
        model.answerTo("brief-suggestion", Result.ok(new ChatResponse(WINDOW, FinishReason.STOP)));
        model.answerTo("brief-synthesis", Result.ok(new ChatResponse(SYNTHESIS, FinishReason.STOP)));
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private BriefSuggestion suggested() {
        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model, null, events::add);
        assertThat(result.error()).isNull();
        return Objects.requireNonNull(result.data());
    }

    private ChatRequest synthesis() {
        return model.requests().stream()
                .filter(request -> request.callKind() == CallKind.BRIEF_SYNTHESIS)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void suggestBrief_synthesisRequest_isOneDeterministicShortCallHeldToItsSchema() {
        suggested();

        assertThat(model.requests())
                .extracting(ChatRequest::callKind)
                .containsExactly(CallKind.BRIEF_SUGGESTION, CallKind.BRIEF_SYNTHESIS);
        final ChatRequest request = synthesis();
        assertThat(Objects.requireNonNull(request.responseFormat()).name()).isEqualTo("brief-synthesis");
        assertThat(Objects.requireNonNull(request.responseFormat()).jsonSchema())
                .isEqualTo(BriefSynthesisSchema.SCHEMA);
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(request.expectedOutputTokens()).isEqualTo(160);
        assertThat(request.maxOutputTokens()).isEqualTo(320);
        assertThat(request.reasoningEnabled()).isEqualTo(ReasoningLevel.OFF);
    }

    // IF the synthesis call were not announced, THEN the busy card would show the windows and then nothing.
    @Test
    void suggestBrief_synthesisCall_isAnnouncedAsABriefSynthesisCall() {
        suggested();

        assertThat(events)
                .filteredOn(event -> event instanceof ModelCallStarted)
                .extracting(event -> ((ModelCallStarted) event).kind())
                .containsExactly(CallKind.BRIEF_SUGGESTION, CallKind.BRIEF_SYNTHESIS);
    }

    @Test
    void suggestBrief_synthesisUserMessage_holdsTheWindowsVerifiedReadingAndItsQuote() {
        suggested();

        final String user = synthesis().messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
        assertThat(user).contains("quiet harbour mystery", QUOTE);
    }

    // The whole story is one window, so its quoted reading is enough for the genre the synthesis names from it.
    @Test
    void suggestBrief_oneWindowBookAndASynthesisCitingIt_keepsTheGenre() {
        assertThat(suggested().genre()).isEqualTo("Mystery");
    }
}
