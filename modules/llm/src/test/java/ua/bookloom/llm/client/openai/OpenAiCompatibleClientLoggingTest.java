package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;

/** Ensures unreadable provider bodies stay out of non-TRACE log output. */
class OpenAiCompatibleClientLoggingTest {

    private static final String PRIVATE_BODY = "<html>private-proxy-body-marker</html>";
    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void chat_unreadableResponseKeepsBodyOutOfErrorLog() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withStatus(200).withBody(PRIVATE_BODY)));
        final Logger logger = (Logger) LoggerFactory.getLogger(OpenAiCompatibleClient.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            final Result<ChatResponse> result =
                    client().chat("model-a", request()).result();

            assertThat(result.error()).extracting("code").isEqualTo(ErrorCode.internal);
            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE_BODY);
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private OpenAiCompatibleClient client() {
        final ProviderConfig config = new ProviderConfig(
                "lmstudio",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));
        return new OpenAiCompatibleClient(config, new HttpExchange(new HttpClients()), new LlmModule().objectMapper());
    }

    private static ChatRequest request() {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")));
    }
}
