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
import com.google.inject.Guice;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** The call line names what ended a call, also when the failure is the client's own timeout. */
class CallLineOutcomeTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final int SERVER_DELAY_MILLIS = 3_000;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .willReturn(aResponse().withFixedDelay(SERVER_DELAY_MILLIS).withStatus(200)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF a call that never answered were logged as "unclassified", THEN a stalled hosted endpoint could not be told
    // from an unknown failure in the log; the line must say it was a timeout.
    @Test
    void chat_noAnswerBeforeTheTimeout_callLineSaysTimeout() {
        final ChatModel model = hostedModel();
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        final Level before = bookloom.getLevel();
        final ListAppender<ILoggingEvent> logged = new ListAppender<>();
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
        try {
            assertThat(Objects.requireNonNull(model.chat(draft()).error(), "call must fail")
                            .code())
                    .isEqualTo(ErrorCode.timeout);
        } finally {
            bookloom.detachAppender(logged);
            bookloom.setLevel(before);
        }

        assertThat(logged.list.stream()
                        .map(ILoggingEvent::getFormattedMessage)
                        .filter(message -> message.startsWith("Provider chat call ")))
                .allSatisfy(line -> assertThat(line).contains("outcome=timeout"));
    }

    private ChatModel hostedModel() {
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        assertThat(configs.save(new ProviderConfig(
                                "hosted",
                                ProviderKind.OPENAI_COMPATIBLE,
                                URI.create(server.baseUrl() + "/v1"),
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(1),
                                null,
                                ProviderAuth.NONE,
                                null,
                                null,
                                null,
                                null,
                                Map.of(),
                                List.of(),
                                ServerEngine.GENERIC,
                                ServerEngine.GENERIC,
                                null,
                                false))
                        .isOk())
                .isTrue();
        return Objects.requireNonNull(
                Guice.createInjector(new LlmModule(name -> null), new LlmTestStorageModule(configs))
                        .getInstance(ChatModelFactory.class)
                        .create(new ModelSelection("hosted", "vendor/chat-model"))
                        .data(),
                "chat model");
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                200,
                null,
                CallKind.DRAFT);
    }
}
