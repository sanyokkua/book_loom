package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/** A server that writes past the answer cap and stops by itself earns one WARN per model; its reply is used. */
class GatedChatModelCapIgnoredTest {

    private static final int CAP = 100;
    private static final String PATH = "/v1/chat/completions";
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

    @Test
    void chat_replyWithMoreTokensThanCap_warnsOnceAndKeepsReply() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(reply(250, "stop"))));
        final GatedChatModel model = model("model-a", new CapIgnoredLog());

        final Result<ChatResponse> first = model.chat(request(ReasoningLevel.OFF));
        final Result<ChatResponse> second = model.chat(request(ReasoningLevel.OFF));

        assertThat(first.isOk() && second.isOk()).isTrue();
        assertThat(first.data()).isNotNull().extracting(ChatResponse::content).isEqualTo("done");
        assertThat(warnings())
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .isEqualTo("Server wrote more tokens than the answer cap reason=cap-ignored provider=test"
                                + " model=model-a cap=100"));
    }

    @Test
    void chat_capIgnoredOnTwoModelsSharingTheLog_warnsOncePerModel() {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(reply(250, "stop"))));
        final CapIgnoredLog shared = new CapIgnoredLog();

        model("model-a", shared).chat(request(ReasoningLevel.OFF));
        model("model-a", shared).chat(request(ReasoningLevel.OFF));
        model("model-b", shared).chat(request(ReasoningLevel.OFF));

        assertThat(warnings()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"100, stop, OFF", "99, stop, OFF", "250, length, OFF", "250, stop, ON", "1124, stop, ON"})
    void chat_replyWithinTheCapOrCutByIt_warnsNothing(
            final int completion, final String finish, final ReasoningLevel level) {
        server.stubFor(post(urlEqualTo(PATH)).willReturn(okJson(reply(completion, finish))));

        model("model-a", new CapIgnoredLog()).chat(request(level));

        assertThat(warnings()).isEmpty();
    }

    // The client sent the call again with four times the cap after the reasoning spent it; the reply is measured
    // against that raised cap (400 plus the reasoning room = 1424), not the first one (1124). Ollama's /v1 keeps the
    // reasoning control, so both requests think.
    @Test
    void chat_capRaisedAfterReasoningSpentIt_measuresTheReplyAgainstTheRaisedCap() {
        server.stubFor(post(urlEqualTo(PATH))
                .inScenario("raised")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson("{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"\","
                        + "\"reasoning_content\":\"thinking\"},\"finish_reason\":\"length\"}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":100}}"))
                .willSetStateTo("second"));
        server.stubFor(post(urlEqualTo(PATH))
                .inScenario("raised")
                .whenScenarioStateIs("second")
                .willReturn(okJson(reply(1200, "stop"))));

        final Result<ChatResponse> answered =
                model("model-a", new CapIgnoredLog(), ServerEngine.OLLAMA).chat(request(ReasoningLevel.ON));

        assertThat(answered.data())
                .isNotNull()
                .extracting(ChatResponse::content)
                .isEqualTo("done");
        server.verify(2, postRequestedFor(urlEqualTo(PATH)));
        assertThat(warnings()).isEmpty();
    }

    private List<String> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains("reason=cap-ignored"))
                .toList();
    }

    private static String reply(final int completion, final String finish) {
        return "{\"model\":\"m\",\"choices\":[{\"message\":{\"content\":\"done\"},\"finish_reason\":\"" + finish
                + "\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":" + completion + "}}";
    }

    private static ChatRequest request(final ReasoningLevel level) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                null,
                level,
                8192,
                null,
                CAP,
                null,
                CallKind.DRAFT,
                null,
                null);
    }

    private GatedChatModel model(final String modelId, final CapIgnoredLog capIgnored) {
        return model(modelId, capIgnored, ServerEngine.GENERIC);
    }

    private GatedChatModel model(final String modelId, final CapIgnoredLog capIgnored, final ServerEngine engine) {
        final ProviderConfig config = new ProviderConfig(
                        "test",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5))
                .withEngine(engine);
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        return new GatedChatModel(
                clients.create(config),
                "test",
                modelId,
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}),
                ModelSettings::defaults,
                null,
                CallLedger.none(),
                new BuiltWrongLog(),
                capIgnored);
    }
}
