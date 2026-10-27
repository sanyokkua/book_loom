package ua.bookloom.llm.http;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Credential and header values never reach a log line at any level; a missing variable is named, never valued. */
class HttpExchangeLoggingTest {

    private static final String KEY_VALUE = "sk-log-secret-987";
    private static final String HEADER_VALUE = "team-private-value-42";
    private static final List<String> LOGGERS =
            List.of("ua.bookloom.llm.http.HttpExchange", "ua.bookloom.llm.http.RequestHeaders");

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Map<Logger, Level> previousLevels = new HashMap<>();

    @BeforeEach
    void start() {
        server.start();
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson("{\"data\":[]}")));
        server.stubFor(post(urlEqualTo("/v1/models")).willReturn(okJson("{}")));
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        LOGGERS.forEach(name -> {
            final Logger logger = (Logger) LoggerFactory.getLogger(name);
            previousLevels.put(logger, logger.getLevel());
            logger.setLevel(Level.TRACE);
            logger.addAppender(appender);
        });
    }

    @AfterEach
    void stop() {
        previousLevels.forEach((logger, level) -> {
            logger.detachAppender(appender);
            logger.setLevel(level);
        });
        appender.stop();
        server.stop();
    }

    // IF a request carries a key and a custom header value, THEN no log line, throwable or MDC value holds either.
    @Test
    void get_withKeyAndCustomHeader_logsNeitherValue() {
        exchange().get(signedConfig(Map.of("X-Team", "${env:TEAM_NAME}")), "/models");

        assertThat(appender.list).isNotEmpty();
        assertThat(everythingLogged()).doesNotContain(KEY_VALUE).doesNotContain(HEADER_VALUE);
    }

    // IF a streamed request carries a key and a custom header value, THEN no log line holds either.
    @Test
    void postStreamed_withKeyAndCustomHeader_logsNeitherValue() {
        exchange()
                .postStreamed(
                        signedConfig(Map.of("X-Team", "${env:TEAM_NAME}")),
                        "/models",
                        "{}",
                        Duration.ofSeconds(2),
                        Long.MAX_VALUE,
                        line -> true);

        assertThat(appender.list).isNotEmpty();
        assertThat(everythingLogged()).doesNotContain(KEY_VALUE).doesNotContain(HEADER_VALUE);
    }

    // IF the credential variable is unset, THEN a warning names the variable and the call is refused.
    @Test
    void get_unsetCredentialVariable_logsTheVariableNameAtWarn() {
        new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get)
                .get(signedConfig(Map.of()), "/models");

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("BOOKLOOM_LOG_KEY"));
    }

    // IF a streamed call's credential variable is unset, THEN a warning names the variable and nothing is sent.
    @Test
    void postStreamed_unsetCredentialVariable_logsTheVariableNameAtWarn() {
        new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get)
                .postStreamed(
                        signedConfig(Map.of()), "/models", "{}", Duration.ofSeconds(2), Long.MAX_VALUE, line -> true);

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("BOOKLOOM_LOG_KEY"));
    }

    private HttpExchange exchange() {
        final Map<String, String> env = Map.of("BOOKLOOM_LOG_KEY", KEY_VALUE, "TEAM_NAME", HEADER_VALUE);
        return new HttpExchange(new HttpClients(), System::nanoTime, env::get);
    }

    private ProviderConfig signedConfig(Map<String, String> headers) {
        return new ProviderConfig(
                "remote",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                null,
                ProviderAuth.BEARER,
                null,
                "BOOKLOOM_LOG_KEY",
                null,
                null,
                headers,
                List.of());
    }

    private String everythingLogged() {
        final StringBuilder all = new StringBuilder();
        appender.list.forEach(event -> {
            all.append(event.getFormattedMessage()).append('\n').append(event.getMDCPropertyMap());
            all.append(Optional.ofNullable(event.getThrowableProxy())
                    .map(ThrowableProxyUtil::asString)
                    .orElse(""));
        });
        return all.toString();
    }
}
