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
import org.junit.jupiter.params.provider.EnumSource;
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
    @EnumSource(ProviderKind.class)
    void map_ollama400VerbFirst_isContextWindowWithReasonContextOverflow(ProviderKind kind) {
        final HttpReply reply = new HttpReply(
                400, Map.of(), "{\"error\":\"request (696 tokens) exceeds the available context size (512 tokens)\"}");

        final Result<HttpReply> result =
                HttpErrorMapper.map(reply, config(kind), HttpErrorMapper.CallPurpose.CHAT, "test-model");

        assertThat(result.isErr()).isTrue();
        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.contextWindow);
        assertThat(error.details()).contains("reason=context-overflow");
    }

    // A row matches the meaning of the words in any order, whatever the status; no row means the status decides, with
    // the reason "unclassified".
    @ParameterizedTest
    @MethodSource("meaningCases")
    void map_bodyByMeaning_selectsCodeAndReason(
            int status, String body, ErrorCode expectedCode, String expectedReason) {
        final Result<HttpReply> result = HttpErrorMapper.map(
                new HttpReply(status, Map.of(), body),
                config(ProviderKind.OPENAI_COMPATIBLE),
                HttpErrorMapper.CallPurpose.CHAT,
                "test-model");

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(expectedCode);
        assertThat(error.details()).startsWith("reason=" + expectedReason + ",");
    }

    @Test
    void map_401_isAuthAndTheBodyIsNotInTheError() {
        final String body = "{\"error\":\"invalid api key " + PRIVATE_BODY + "\"}";

        final Result<HttpReply> result = HttpErrorMapper.map(
                new HttpReply(401, Map.of(), body),
                config(ProviderKind.OPENAI_COMPATIBLE),
                HttpErrorMapper.CallPurpose.CHAT,
                "test-model");

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(error.title() + error.message() + error.details()).doesNotContain(PRIVATE_BODY);
    }

    // The mapper passes an answered 200 on; the chat reader cannot read it and calls it upstream, never validation.
    @Test
    void map_answered200Html_isLeftToTheReaderWhichCallsItUpstream() {
        final HttpReply reply = new HttpReply(200, Map.of(), "<html>proxy error</html>");
        final ProviderConfig config = config(ProviderKind.OPENAI_COMPATIBLE);

        final Result<HttpReply> result =
                HttpErrorMapper.map(reply, config, HttpErrorMapper.CallPurpose.CHAT, "test-model");

        assertThat(result.data()).isSameAs(reply);
        assertThat(UnreadableReply.error(config, "test-model", null).code()).isEqualTo(ErrorCode.upstream);
    }

    @Test
    void map_verbFirstOverflow_detailsHoldBothCounts() {
        final HttpReply reply = new HttpReply(
                400, Map.of(), "{\"error\":\"request (696 tokens) exceeds the available context size (512 tokens)\"}");

        final Result<HttpReply> result =
                HttpErrorMapper.map(reply, config(ProviderKind.OLLAMA), HttpErrorMapper.CallPurpose.CHAT, "test-model");

        assertThat(Objects.requireNonNull(result.error(), "error").details())
                .startsWith("reason=context-overflow,")
                .endsWith("promptTokens=696, windowTokens=512")
                .doesNotContain("available context size");
    }

    @Test
    void map_llamaServerTypedOverflow_detailsHoldBothCounts() {
        final HttpReply reply = new HttpReply(
                400,
                Map.of(),
                "{\"error\":{\"code\":400,\"message\":\"the request exceeds the available context size, try"
                        + " increasing it\",\"type\":\"exceed_context_size_error\",\"n_prompt_tokens\":9120,"
                        + "\"n_ctx\":8192}}");

        final Result<HttpReply> result = HttpErrorMapper.map(
                reply, config(ProviderKind.OPENAI_COMPATIBLE), HttpErrorMapper.CallPurpose.CHAT, "test-model");

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.contextWindow);
        assertThat(error.details()).endsWith("promptTokens=9120, windowTokens=8192");
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
                        unloadedModelCases(),
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

    // A local server that unloaded an idle model answers the chat with a refusal, which loading the model fixes: it is
    // modelUnavailable, which a run waits through by itself for a while, never a validation failure of the request.
    private static Stream<Arguments> unloadedModelCases() {
        return Stream.of(
                chatStatus(400, "{\"error\":\"Model unloaded\"}", ErrorCode.modelUnavailable),
                chatStatus(400, "{\"error\":{\"message\":\"Model is unloaded.\"}}", ErrorCode.modelUnavailable),
                chatStatus(
                        404,
                        "{\"error\":\"No models loaded. Please load a model in the developer page.\"}",
                        ErrorCode.modelUnavailable),
                chatStatus(400, "{\"error\":\"model 'gemma' is not loaded\"}", ErrorCode.modelUnavailable),
                // Rows read the words whatever the status and purpose (task 3.4), so a DISCOVERY 400 that says the
                // model
                // is unloaded is modelUnavailable now; before, a guard limited this meaning to chat calls.
                statusCase(
                        400,
                        "{\"error\":\"Model unloaded\"}",
                        ProviderKind.OPENAI_COMPATIBLE,
                        HttpErrorMapper.CallPurpose.DISCOVERY,
                        ErrorCode.modelUnavailable));
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
                chatStatus(422, "{\"error\":\"unknown field top_p " + PRIVATE_BODY + "\"}", ErrorCode.validation),
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

    private static Stream<Arguments> meaningCases() {
        return Stream.of(
                arguments(
                        400,
                        "{\"error\":{\"message\":\"This model's maximum context length is 8192 tokens; your request"
                                + " exceeds it\",\"code\":\"context_length_exceeded\"}}",
                        ErrorCode.contextWindow,
                        "context-overflow"),
                arguments(
                        400,
                        "{\"error\":\"input length 9120 is greater than the context length 8192\"}",
                        ErrorCode.contextWindow,
                        "context-overflow"),
                arguments(400, "{\"error\":\"invalid option: temperature\"}", ErrorCode.validation, "unclassified"),
                arguments(503, "", ErrorCode.upstream, "unclassified"),
                arguments(
                        500,
                        "{\"error\":\"request (696 tokens) exceeds the available context size (512 tokens)\"}",
                        ErrorCode.contextWindow,
                        "context-overflow"));
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
