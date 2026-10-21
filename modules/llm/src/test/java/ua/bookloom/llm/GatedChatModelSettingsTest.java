package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/** A model's own settings shape each request the bound model sends, read afresh for every call. */
class GatedChatModelSettingsTest {

    private static final String PROVIDER = "lmstudio";
    private static final String MODEL = "test-model";

    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();
    private final RecordingClient client = new RecordingClient();

    // IF the choice is Let BookLoom choose, THEN the kind's temperature stays; My value replaces it; Don't send clears
    // it.
    @ParameterizedTest
    @CsvSource(value = {"CHOOSE,,0.2", "VALUE,0.7,0.7", "OMIT,,"})
    void chat_temperatureChoice_sendsExpectedTemperature(
            final TemperatureChoice choice, @Nullable final Double fixed, @Nullable final Double expected) {
        repository.save(settings(choice, fixed, null, null, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, null));

        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.temperature()).isEqualTo(expected));
    }

    @Test
    void chat_maxOutputBelowRequestCap_lowersMaxOutputTokens() {
        repository.save(settings(TemperatureChoice.CHOOSE, null, null, 128, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, 400));

        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.maxOutputTokens()).isEqualTo(128));
    }

    @Test
    void chat_maxOutputAboveRequestCap_keepsRequestCap() {
        repository.save(settings(TemperatureChoice.CHOOSE, null, null, 900, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, 400));

        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.maxOutputTokens()).isEqualTo(400));
    }

    // A call that states no cap is left as unbounded as before (spec: "WHEN it carries none, neither field").
    @Test
    void chat_maxOutputAndNoRequestCap_sendsNoCap() {
        repository.save(settings(TemperatureChoice.CHOOSE, null, null, 128, OutputLengthField.MAX_TOKENS));

        model().chat(request(0.2, null));

        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.maxOutputTokens()).isNull());
    }

    @Test
    void chat_outputFieldMaxCompletionTokens_setsRequestOutputField() {
        repository.save(settings(TemperatureChoice.CHOOSE, null, null, null, OutputLengthField.MAX_COMPLETION_TOKENS));

        model().chat(request(0.2, 400));

        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS));
    }

    // IF the temperature is saved between two calls, THEN only the second request carries it.
    @Test
    void chat_temperatureSavedBetweenTwoCalls_changesOnlySecondRequest() {
        final ChatModel model = model();
        model.chat(request(0.2, null));
        repository.save(settings(TemperatureChoice.VALUE, 0.9, null, null, OutputLengthField.MAX_TOKENS));

        model.chat(request(0.2, null));

        assertThat(client.sent).extracting(ChatRequest::temperature).containsExactly(0.2, 0.9);
    }

    // The context window is not put on the request here: a chunk is packed for its window by the pipeline.
    @Test
    void contextWindowOverride_stored8192_returns8192AndRequestContextUnchanged() {
        repository.save(settings(TemperatureChoice.CHOOSE, null, 8192, null, OutputLengthField.MAX_TOKENS));
        final ChatModel model = model();

        model.chat(request(0.2, null).sizedTo(16384, 4096));

        assertThat(model.contextWindowOverride()).isEqualTo(8192);
        assertThat(client.sent)
                .singleElement()
                .satisfies(sent -> assertThat(sent.contextWindow()).isEqualTo(16384));
    }

    // IF the first attempt times out, THEN the retry keeps the settings applied to the call.
    @Test
    void chat_timeoutRetry_keepsAppliedSettings() {
        repository.save(settings(TemperatureChoice.VALUE, 0.7, null, 128, OutputLengthField.MAX_COMPLETION_TOKENS));
        client.failFirstWithTimeout = true;

        final Result<ChatResponse> result = model().chat(request(0.2, 400));

        assertThat(result.isOk()).isTrue();
        assertThat(client.sent).hasSize(2).allSatisfy(sent -> {
            assertThat(sent.temperature()).isEqualTo(0.7);
            assertThat(sent.outputField()).isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
        });
        assertThat(client.sent.get(1).maxOutputTokens()).isEqualTo(96);
    }

    private ChatModel model() {
        final RetryPolicy retries = new RetryPolicy(
                Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC), () -> 0.5, ignored -> {});
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        return new GatedChatModel(client, PROVIDER, MODEL, new InferenceGate(), retries, cache::current);
    }

    private static ModelSettings settings(
            final TemperatureChoice choice,
            @Nullable final Double temperature,
            @Nullable final Integer window,
            @Nullable final Integer maxOutput,
            final OutputLengthField field) {
        return new ModelSettings(PROVIDER, MODEL, choice, temperature, window, maxOutput, field, List.of(), null);
    }

    private static ChatRequest request(final Double temperature, @Nullable final Integer cap) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                temperature,
                null,
                null,
                null,
                null,
                cap,
                null,
                CallKind.DRAFT,
                null,
                null);
    }

    /** Hand-written provider fake that records what the bound model hands it. */
    private static final class RecordingClient implements ProviderClient {

        private final List<ChatRequest> sent = new ArrayList<>();
        private boolean failFirstWithTimeout;

        @Override
        public ProviderCallResult<Boolean> probe() {
            return ProviderCallResult.withoutRetryAfter(Result.ok(true));
        }

        @Override
        public ProviderCallResult<List<ModelInfo>> listModels() {
            return ProviderCallResult.withoutRetryAfter(Result.ok(List.of()));
        }

        @Override
        public ProviderCallResult<ChatResponse> chat(final String modelId, final ChatRequest request) {
            sent.add(request);
            if (failFirstWithTimeout && sent.size() == 1) {
                return ProviderCallResult.withoutRetryAfter(
                        Result.err(AppError.of(ErrorCode.timeout, "Timed out", "No reply in time.")));
            }
            return ProviderCallResult.withoutRetryAfter(Result.ok(new ChatResponse("ok", FinishReason.STOP)));
        }

        @Override
        public ProviderKind kind() {
            return ProviderKind.OPENAI_COMPATIBLE;
        }
    }
}
