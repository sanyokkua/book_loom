package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;

/** An SSE {@code event: error} frame inside an HTTP 200: a failure of the call, never a wrong path. */
class HttpErrorMapperEventErrorTest {

    private static final String PRIVATE_BODY = "PRIVATE-RESPONSE-BODY";

    @Test
    void map_eventErrorWithUnknownMessage_isUpstreamUnclassifiedAndKeepsTheBodyOut() {
        final HttpReply reply =
                new HttpReply(200, Map.of(), "event: error\ndata: {\"message\":\"" + PRIVATE_BODY + "\"}\n\n");

        final Result<HttpReply> result = HttpErrorMapper.map(
                reply, config(ProviderKind.OPENAI_COMPATIBLE), HttpErrorMapper.CallPurpose.CHAT, "test-model");

        final AppError error = Objects.requireNonNull(result.error(), "error");
        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.details()).startsWith("reason=unclassified,");
        assertThat(error.title() + error.message() + error.details()).doesNotContain(PRIVATE_BODY);
    }

    @Test
    void map_eventErrorOnAnOllamaChat_isLeftToTheStreamReader() {
        final HttpReply reply = new HttpReply(200, Map.of(), "event: error\ndata: {}\n\n");

        final Result<HttpReply> result =
                HttpErrorMapper.map(reply, config(ProviderKind.OLLAMA), HttpErrorMapper.CallPurpose.CHAT, "test-model");

        assertThat(result.data()).isSameAs(reply);
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
