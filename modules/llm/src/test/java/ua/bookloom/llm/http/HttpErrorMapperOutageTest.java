package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.net.URI;
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

/** Outage wording that looks like a catalogue row's words is still an outage, never a flag or a pause. */
class HttpErrorMapperOutageTest {

    // A Go client's deadline and a proxy's own 502 page are outages: no row explains them, so the status decides and
    // the retry falls back to the code's own answer.
    @ParameterizedTest
    @MethodSource("outageBodies")
    void map_outageWording_staysUpstreamUnclassifiedAndRetryable(int status, String body) {
        final Result<HttpReply> result = HttpErrorMapper.map(
                new HttpReply(status, Map.of(), body),
                config(ProviderKind.OLLAMA),
                HttpErrorMapper.CallPurpose.CHAT,
                "test-model");

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.details()).startsWith("reason=unclassified,");
        assertThat(error.retryable()).isTrue();
    }

    private static Stream<Arguments> outageBodies() {
        return Stream.of(
                arguments(500, "{\"error\":\"Post \\\"http://127.0.0.1:1/completion\\\": context deadline exceeded\"}"),
                arguments(502, "<html>502 Bad Gateway</html>"));
    }

    @Test
    void mapStreamedError_contextDeadlineExceeded_isUpstreamUnclassified() {
        final AppError error = HttpErrorMapper.mapStreamedError(
                config(ProviderKind.OLLAMA),
                "test-model",
                "Post \"http://127.0.0.1:1/completion\": context deadline exceeded");

        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.details()).startsWith("reason=unclassified,");
    }

    private static ProviderConfig config(ProviderKind kind) {
        return new ProviderConfig(
                "test-provider",
                kind,
                URI.create("http://provider.test:11434"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(3));
    }
}
