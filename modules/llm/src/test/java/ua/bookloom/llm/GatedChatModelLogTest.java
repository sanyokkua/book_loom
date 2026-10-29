package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * A call that is given up writes one WARN line naming its code, its reason token and its kind, so a night's log says
 * at a glance why a call failed; the reply's body and the prompt reach no line above TRACE.
 */
class GatedChatModelLogTest {

    private static final String OPENAI = "OPENAI_COMPATIBLE, /v1, /v1/chat/completions";
    private static final String OLLAMA = "OLLAMA, '', /api/chat";
    private static final String BODY_MARK = "SECRET-BODY-MARK";
    private static final String PROMPT_MARK = "PROMPT-TEXT-MARK";
    private static final String REPEAT_LIMIT = "{\"error\":\"token repeat limit reached " + BODY_MARK + "\"}";
    private static final String GRAMMAR_TOO_LARGE =
            "{\"error\":\"Failed to initialize samplers: failed to parse grammar " + BODY_MARK + "\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void start() {
        server.start();
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.TRACE);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void stop() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
        server.stop();
    }

    // A looping model is sent once more with a new seed; the second loop gives the call up, and the line says why.
    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void giveUp_logsReasonAndNoBody(final ProviderKind kind, final String basePath, final String chatPath) {
        server.stubFor(post(urlEqualTo(chatPath))
                .willReturn(aResponse().withStatus(500).withBody(REPEAT_LIMIT)));

        model(kind, basePath).chat(request(CallKind.DRAFT));

        assertThat(warnings())
                .anySatisfy(line -> assertThat(line)
                        .contains("gave up provider chat code=upstream reason=repeat-limit kind=DRAFT"));
        assertThat(linesAtOrAbove(Level.DEBUG)).noneMatch(line -> line.contains(BODY_MARK));
        assertThat(linesAtOrAbove(Level.DEBUG)).noneMatch(line -> line.contains(PROMPT_MARK));
    }

    // A request the server says BookLoom built wrong is never repeated, and giving it up is a warning, not a detail.
    @ParameterizedTest
    @CsvSource({OPENAI})
    void giveUp_ruleNone_isWarnNotDebug(final ProviderKind kind, final String basePath, final String chatPath) {
        server.stubFor(post(urlEqualTo(chatPath))
                .willReturn(aResponse().withStatus(400).withBody(GRAMMAR_TOO_LARGE)));

        model(kind, basePath).chat(request(CallKind.TERM_READING));

        assertThat(warnings())
                .filteredOn(line -> line.contains(
                        "gave up provider chat code=validation reason=grammar-too-large kind=TERM_READING"))
                .hasSize(1);
        assertThat(linesAtOrAbove(Level.DEBUG)).noneMatch(line -> line.contains(BODY_MARK));
    }

    private List<String> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private List<String> linesAtOrAbove(final Level level) {
        return logged.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static ChatRequest request(final CallKind callKind) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this. " + PROMPT_MARK)),
                0.2,
                null,
                ReasoningLevel.OFF,
                8192,
                null,
                400,
                null,
                callKind,
                new SamplingParams(null, null, null, null),
                null);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        return new GatedChatModel(
                clients.create(config),
                "test-model",
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}));
    }
}
