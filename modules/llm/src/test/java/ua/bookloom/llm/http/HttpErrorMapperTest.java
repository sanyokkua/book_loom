package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** Proves the ordered transport and HTTP classification contract without exposing response content. */
class HttpErrorMapperTest {

    private static final String PRIVATE_BODY = "PRIVATE-RESPONSE-BODY";

    @ParameterizedTest
    @MethodSource("httpStatusCases")
    void map_httpStatusesSelectD4CodeWithoutLeakingBody(
            int status, String body, ProviderKind kind, HttpErrorMapper.CallPurpose purpose, ErrorCode expectedCode) {
        final HttpReply reply = new HttpReply(status, Map.of(), body);

        final Result<HttpReply> result = HttpErrorMapper.map(reply, config(kind), purpose, "requested-model");

        assertThat(result.isErr()).isTrue();
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.retryable()).isEqualTo(expectedCode.isRetryable());
        assertThat(error.details()).doesNotContain(body).doesNotContain(PRIVATE_BODY);
        assertThat(error.details()).contains("httpStatus=" + status).contains("endpointHost=provider.test");
    }

    @ParameterizedTest
    @MethodSource("transportFailureCases")
    void map_transportFailuresSelectD4CodeWithoutLeakingExceptionMessage(Throwable failure, ErrorCode expectedCode) {
        final AppError error = HttpErrorMapper.map(failure, config(ProviderKind.OPENAI_COMPATIBLE), "requested-model");

        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.retryable()).isEqualTo(expectedCode.isRetryable());
        assertThat(error.details()).doesNotContain("PRIVATE-EXCEPTION-MESSAGE");
    }

    @ParameterizedTest
    @MethodSource("timeoutCases")
    void map_timeoutsRecordTheMatchingConfiguredTimeout(Throwable failure, Duration expectedTimeout) {
        final AppError error = HttpErrorMapper.map(failure, config(ProviderKind.OPENAI_COMPATIBLE), null);

        assertThat(error.code()).isEqualTo(ErrorCode.timeout);
        assertThat(error.details()).contains("timeoutMs=" + expectedTimeout.toMillis());
    }

    @Test
    void map_interruptionRestoresFlagAndReturnsCancelled() {
        final AppError error = HttpErrorMapper.map(
                new InterruptedException("PRIVATE-EXCEPTION-MESSAGE"), config(ProviderKind.OPENAI_COMPATIBLE), null);

        assertThat(error.code()).isEqualTo(ErrorCode.cancelled);
        assertThat(error.retryable()).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void map_probe404_isReachableWhileChat404MeansModelNotFound() {
        final HttpReply reply = new HttpReply(404, Map.of(), "not found");
        final ProviderConfig config = config(ProviderKind.OPENAI_COMPATIBLE);

        final Result<HttpReply> probe = HttpErrorMapper.map(reply, config, HttpErrorMapper.CallPurpose.PROBE, null);
        final Result<HttpReply> chat = HttpErrorMapper.map(reply, config, HttpErrorMapper.CallPurpose.CHAT, "model");

        assertThat(probe.data()).isSameAs(reply);
        assertThat(probe.error()).isNull();
        assertThat(Objects.requireNonNull(chat.error(), "error").code()).isEqualTo(ErrorCode.modelNotFound);
    }

    @Test
    void map_probeOllamaModelErrorBody_remainsReachable() {
        final HttpReply reply = ollamaModelNotFoundReply(200);

        final Result<HttpReply> result =
                HttpErrorMapper.map(reply, config(ProviderKind.OLLAMA), HttpErrorMapper.CallPurpose.PROBE, null);

        assertThat(result.data()).isSameAs(reply);
        assertThat(result.error()).isNull();
    }

    @Test
    void map_ollamaChatContentMentioningMissingModel_isNotAnErrorBody() {
        final HttpReply reply =
                new HttpReply(200, Map.of(), "{\"message\":{\"content\":\"The model 'nope:latest' was not found.\"}}");

        final Result<HttpReply> result = HttpErrorMapper.map(
                reply, config(ProviderKind.OLLAMA), HttpErrorMapper.CallPurpose.CHAT, "requested-model");

        assertThat(result.data()).isSameAs(reply);
        assertThat(result.error()).isNull();
    }

    @ParameterizedTest
    @MethodSource("probeFailureCases")
    void map_probeAuthAndServerStatusesRemainFailures(int status, ErrorCode expectedCode) {
        final Result<HttpReply> result = HttpErrorMapper.map(
                new HttpReply(status, Map.of(), PRIVATE_BODY),
                config(ProviderKind.OPENAI_COMPATIBLE),
                HttpErrorMapper.CallPurpose.PROBE,
                null);

        assertThat(result.isErr()).isTrue();
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.details()).doesNotContain(PRIVATE_BODY);
    }

    private static Stream<Arguments> httpStatusCases() {
        return Stream.of(
                        authenticationAndModelCases(),
                        rateAndFallbackCases(),
                        contextWindowCases(),
                        ollamaModelBodyCases())
                .flatMap(cases -> cases);
    }

    private static Stream<Arguments> authenticationAndModelCases() {
        return Stream.of(
                chatStatus(401, PRIVATE_BODY, ErrorCode.auth),
                chatStatus(403, PRIVATE_BODY, ErrorCode.auth),
                chatStatus(404, PRIVATE_BODY, ErrorCode.modelNotFound),
                statusCase(
                        404,
                        PRIVATE_BODY,
                        ProviderKind.OPENAI_COMPATIBLE,
                        HttpErrorMapper.CallPurpose.DISCOVERY,
                        ErrorCode.internal),
                statusCase(
                        404,
                        "{\"error\":\"model 'nope:latest' not found\"}",
                        ProviderKind.OLLAMA,
                        HttpErrorMapper.CallPurpose.DISCOVERY,
                        ErrorCode.modelNotFound));
    }

    private static Stream<Arguments> ollamaModelBodyCases() {
        return Stream.of(
                statusCase(
                        200,
                        "{\"error\":\"model 'nope:latest' not found\"}",
                        ProviderKind.OLLAMA,
                        HttpErrorMapper.CallPurpose.DISCOVERY,
                        ErrorCode.modelNotFound),
                statusCase(
                        401,
                        "{\"error\":\"model 'nope:latest' not found\"}",
                        ProviderKind.OLLAMA,
                        HttpErrorMapper.CallPurpose.CHAT,
                        ErrorCode.auth));
    }

    private static HttpReply ollamaModelNotFoundReply(int status) {
        return new HttpReply(status, Map.of(), "{\"error\":\"model 'nope:latest' not found\"}");
    }

    private static Stream<Arguments> rateAndFallbackCases() {
        return Stream.of(
                chatStatus(429, PRIVATE_BODY, ErrorCode.rateLimited),
                chatStatus(500, PRIVATE_BODY, ErrorCode.upstream),
                chatStatus(599, PRIVATE_BODY, ErrorCode.upstream),
                chatStatus(400, "{\"error\":\"invalid request " + PRIVATE_BODY + "\"}", ErrorCode.validation),
                chatStatus(302, PRIVATE_BODY, ErrorCode.internal));
    }

    private static Stream<Arguments> contextWindowCases() {
        return Stream.of(
                chatStatus(
                        400, "{\"error\":\"context_length_exceeded " + PRIVATE_BODY + "\"}", ErrorCode.contextWindow),
                chatStatus(400, "{\"error\":\"n_ctx " + PRIVATE_BODY + "\"}", ErrorCode.contextWindow),
                chatStatus(
                        400, "{\"error\":\"context length exceeded " + PRIVATE_BODY + "\"}", ErrorCode.contextWindow),
                chatStatus(400, "{\"error\":\"context is too long " + PRIVATE_BODY + "\"}", ErrorCode.contextWindow),
                chatStatus(
                        400,
                        "{\"error\":\"context is greater than allowed " + PRIVATE_BODY + "\"}",
                        ErrorCode.contextWindow));
    }

    private static Arguments chatStatus(int status, String body, ErrorCode expectedCode) {
        return statusCase(status, body, ProviderKind.OPENAI_COMPATIBLE, HttpErrorMapper.CallPurpose.CHAT, expectedCode);
    }

    private static Arguments statusCase(
            int status, String body, ProviderKind kind, HttpErrorMapper.CallPurpose purpose, ErrorCode expectedCode) {
        return arguments(status, body, kind, purpose, expectedCode);
    }

    private static Stream<Arguments> transportFailureCases() {
        return Stream.of(
                arguments(new HttpTimeoutException("PRIVATE-EXCEPTION-MESSAGE"), ErrorCode.timeout),
                arguments(new HttpConnectTimeoutException("PRIVATE-EXCEPTION-MESSAGE"), ErrorCode.timeout),
                arguments(new ConnectException("PRIVATE-EXCEPTION-MESSAGE"), ErrorCode.unreachable),
                arguments(new UnresolvedAddressException(), ErrorCode.unreachable),
                arguments(new IOException("PRIVATE-EXCEPTION-MESSAGE"), ErrorCode.unreachable),
                arguments(new IllegalStateException("PRIVATE-EXCEPTION-MESSAGE"), ErrorCode.internal));
    }

    private static Stream<Arguments> timeoutCases() {
        return Stream.of(
                arguments(new HttpTimeoutException("PRIVATE-EXCEPTION-MESSAGE"), Duration.ofSeconds(3)),
                arguments(new HttpConnectTimeoutException("PRIVATE-EXCEPTION-MESSAGE"), Duration.ofSeconds(2)));
    }

    private static Stream<Arguments> probeFailureCases() {
        return Stream.of(
                arguments(401, ErrorCode.auth),
                arguments(403, ErrorCode.auth),
                arguments(500, ErrorCode.upstream),
                arguments(599, ErrorCode.upstream));
    }

    private static ProviderConfig config(ProviderKind kind) {
        return new ProviderConfig(
                "test-provider",
                kind,
                URI.create("http://provider.test:1234/v1"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }
}
