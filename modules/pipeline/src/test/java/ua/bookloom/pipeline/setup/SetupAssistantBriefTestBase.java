package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The scripted model, the book and the helpers the brief suggestion tests share (15i.B4). */
abstract class SetupAssistantBriefTestBase {

    // Balanced reads four windows; the book is long enough (about 27,000 tokens) for every dial's windows.
    static final int WINDOWS = 4;
    static final String TITLE = "The Lantern Ledger";
    // The story has no "I" in its narration, so the detector reads it as third person whatever the windows say.
    static final String BOOK = "---\ntitle: " + TITLE + "\n---\n\n" + BriefBooks.story(12, 100);
    static final String QUOTE = BriefBooks.QUOTE;
    static final String KNOWN = """
            {"known":true,"title":"The Lantern Ledger","author":"","year":1898,"genre":"crime novel",\
            "audience":"adult readers","era":"late twentieth century","narration":"third person",\
            "summary":"A clerk keeps the books of a lamp works.","confidence":0.9}""";
    // Genre on three windows, audience on what the model knows, voice on one window that knowledge does not support.
    static final String SYNTHESIS = """
            {"genre":{"basis":{"knowledge":false,"passages":[1,2,3]},"value":"Crime fiction"},\
            "audience":{"basis":{"knowledge":true,"passages":[]},"value":"adult"},\
            "voice":{"basis":{"knowledge":false,"passages":[2]},"value":"terse and wry"}}""";

    @TempDir
    Path tempDir;

    Desk desk;
    ScriptedChatModel model;
    SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, BOOK);
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

    static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    static String window(final String language, final String genre, final String genreQuote, final String register) {
        return "{" + language + field("genre", genre, genreQuote) + "," + field("register", register, QUOTE) + ","
                + field("voice", "terse", QUOTE) + "," + field("audience", "adult", QUOTE) + ","
                + field("narrator", "first", QUOTE) + ","
                + "\"narratorGender\":{\"evidence\":\"\",\"value\":\"unknown\",\"name\":\"\",\"confidence\":0.5}}";
    }

    static String window(final String genre, final String register) {
        return window("", genre, QUOTE, register);
    }

    static String field(final String name, final String value, final String quote) {
        return "\"" + name + "\":{\"evidence\":\"" + quote + "\",\"value\":\"" + value + "\",\"confidence\":0.9}";
    }

    void knowledgeAnswers(final String json) {
        model.answerTo("book-knowledge", reply(json));
    }

    void windowsAnswer(final String... replies) {
        IntStream.range(0, replies.length).forEach(index -> model.answerTo("brief-suggestion", reply(replies[index])));
    }

    void everyWindowAnswers(final String json) {
        windowsAnswer(IntStream.range(0, WINDOWS).mapToObj(index -> json).toArray(String[]::new));
    }

    void synthesisAnswers(final String json) {
        model.answerTo("brief-synthesis", reply(json));
    }

    BriefSuggestion suggested() {
        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);
        assertThat(result.error()).isNull();
        return Objects.requireNonNull(result.data());
    }

    List<CallKind> kinds() {
        return model.requests().stream().map(ChatRequest::callKind).toList();
    }

    List<ChatRequest> requestsOf(final CallKind kind) {
        return model.requests().stream()
                .filter(request -> request.callKind() == kind)
                .toList();
    }

    static String userMessage(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    String synthesisUserMessage() {
        return userMessage(requestsOf(CallKind.BRIEF_SYNTHESIS).getFirst());
    }
}
