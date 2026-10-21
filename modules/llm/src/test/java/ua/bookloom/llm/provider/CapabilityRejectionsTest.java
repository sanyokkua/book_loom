package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.llm.http.HttpReply;

/** A reply is read as a refusal only when it names an optional setting the request actually sent. */
class CapabilityRejectionsTest {

    private static final ProviderKind OPENAI = ProviderKind.OPENAI_COMPATIBLE;
    private static final ProviderKind OLLAMA = ProviderKind.OLLAMA;
    private static final String OPENAI_TEMPERATURE = "{\"error\":{\"message\":\"Unsupported value: 'temperature' does "
            + "not support 0.2 with this model. Only the default (1) value is supported.\"}}";

    // IF the body names a setting the request sent, THEN that setting is the refusal; else none.
    @ParameterizedTest
    @MethodSource("cases")
    void from_replyAndRequest_namesOnlyASentSetting(
            final int status,
            final String body,
            final ChatRequest request,
            final ProviderKind kind,
            @Nullable final RejectedCapability expected) {
        final RejectedCapability found =
                CapabilityRejections.from(new HttpReply(status, Map.of(), body), request, kind);

        assertThat(found).isEqualTo(expected);
    }

    private static Stream<Arguments> cases() {
        return Stream.of(outputAndSamplingCases(), samplingCases(), sentSettingCases(), dialectCases())
                .flatMap(cases -> cases);
    }

    private static Stream<Arguments> outputAndSamplingCases() {
        return Stream.of(
                arguments(
                        400,
                        "{\"error\":\"Unsupported parameter: 'temperature'\"}",
                        temperature(),
                        OPENAI,
                        RejectedCapability.TEMPERATURE),
                arguments(400, OPENAI_TEMPERATURE, temperature(), OPENAI, RejectedCapability.TEMPERATURE),
                arguments(
                        400,
                        "{\"error\":\"max_tokens is not supported, use max_completion_tokens\"}",
                        capped(null),
                        OPENAI,
                        RejectedCapability.OUTPUT_FIELD),
                arguments(
                        400,
                        "{\"error\":\"max_tokens is not supported, use max_completion_tokens\"}",
                        capped(OutputLengthField.MAX_COMPLETION_TOKENS),
                        OPENAI,
                        null));
    }

    private static Stream<Arguments> samplingCases() {
        return Stream.of(
                arguments(
                        400,
                        "{\"error\":\"max_tokens is not supported, use max_completion_tokens\"}",
                        capped(null),
                        OLLAMA,
                        null),
                arguments(400, "{\"error\":\"temperature is invalid\"}", plain(), OPENAI, null),
                arguments(
                        422,
                        "{\"error\":\"unknown field top_p\"}",
                        sampling(new SamplingParams(0.9, null, null, null)),
                        OPENAI,
                        RejectedCapability.TOP_P),
                arguments(400, "{\"error\":\"unsupported parameter: top_k\"}", temperature(), OPENAI, null),
                arguments(
                        400,
                        "{\"error\":\"unsupported parameter: top_k\"}",
                        sampling(new SamplingParams(0.9, null, null, null)),
                        OPENAI,
                        null),
                arguments(400, "{\"error\":\"invalid request\"}", temperature(), OPENAI, null));
    }

    private static Stream<Arguments> sentSettingCases() {
        return Stream.of(
                arguments(
                        400,
                        "{\"error\":\"unknown option top_k\"}",
                        sampling(new SamplingParams(null, 40, null, null)),
                        OLLAMA,
                        RejectedCapability.TOP_K),
                arguments(
                        400,
                        "{\"error\":\"unknown field min_p\"}",
                        sampling(new SamplingParams(null, null, 0.05, null)),
                        OPENAI,
                        RejectedCapability.MIN_P),
                arguments(
                        400,
                        "{\"error\":\"unsupported parameter: repeat_penalty\"}",
                        sampling(new SamplingParams(null, null, null, 1.1)),
                        OPENAI,
                        RejectedCapability.REPEAT_PENALTY),
                arguments(
                        400, "{\"error\":\"unsupported parameter: seed\"}", seeded(), OPENAI, RejectedCapability.SEED),
                arguments(
                        400, "{\"error\":\"unknown option num_ctx\"}", windowed(), OLLAMA, RejectedCapability.NUM_CTX),
                arguments(400, "{\"error\":\"unknown option num_ctx\"}", windowed(), OPENAI, null),
                arguments(500, "{\"error\":\"Unsupported parameter: 'temperature'\"}", temperature(), OPENAI, null));
    }

    private static Stream<Arguments> dialectCases() {
        return Stream.of(
                arguments(404, "{\"error\":\"Unsupported parameter: 'temperature'\"}", temperature(), OPENAI, null),
                arguments(
                        400,
                        "{\"error\":\"unsupported: reasoning_effort and temperature\"}",
                        reasoningAndTemperature(),
                        OPENAI,
                        RejectedCapability.REASONING_CONTROL),
                arguments(
                        400,
                        "{\"error\":\"response_format is unsupported\"}",
                        structured(),
                        OPENAI,
                        RejectedCapability.STRUCTURED_OUTPUT),
                arguments(
                        400,
                        "{\"error\":\"unknown field think\"}",
                        reasoningOff(),
                        OLLAMA,
                        RejectedCapability.REASONING_CONTROL));
    }

    private static ChatRequest build(
            @Nullable Double temperature,
            @Nullable ResponseFormat format,
            @Nullable Boolean reasoning,
            @Nullable Integer window,
            @Nullable Integer cap,
            @Nullable Integer seed,
            @Nullable SamplingParams sampling,
            @Nullable OutputLengthField field) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                temperature,
                format,
                reasoning,
                window,
                null,
                cap,
                seed,
                null,
                sampling,
                field);
    }

    private static ChatRequest plain() {
        return build(null, null, null, null, null, null, null, null);
    }

    private static ChatRequest temperature() {
        return build(0.2, null, null, null, null, null, null, null);
    }

    private static ChatRequest capped(@Nullable OutputLengthField field) {
        return build(null, null, null, null, 400, null, null, field);
    }

    private static ChatRequest sampling(SamplingParams sampling) {
        return build(null, null, null, null, null, null, sampling, null);
    }

    private static ChatRequest seeded() {
        return build(null, null, null, null, null, 7, null, null);
    }

    private static ChatRequest windowed() {
        return build(null, null, null, 8192, null, null, null, null);
    }

    private static ChatRequest reasoningAndTemperature() {
        return build(0.2, null, false, null, null, null, null, null);
    }

    private static ChatRequest reasoningOff() {
        return build(null, null, false, null, null, null, null, null);
    }

    private static ChatRequest structured() {
        return build(null, new ResponseFormat("draft", "{\"type\":\"object\"}"), null, null, null, null, null, null);
    }
}
