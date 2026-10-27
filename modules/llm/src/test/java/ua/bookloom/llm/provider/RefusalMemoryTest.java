package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/** A refusal is stored on the model's own settings row, once, and read back to strip the next request. */
class RefusalMemoryTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "gpt-oss:20b";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);
    private static final int EIGHT = 8;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();
    private final ModelSettingsCache cache = new ModelSettingsCache(repository);
    private final RefusalMemory memory = new RefusalMemory(cache, CLOCK);

    // THEN the row gets (temperature, today, omitted) and the person's other choices stay.
    @Test
    void remember_modelWithMeasuredSpeed_keepsSpeedAndSource() {
        repository.save(ModelSettings.defaults(PROVIDER, MODEL).withMeasuredSpeed(1.2, MeasuredFrom.RUN));

        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);

        final ModelSettings found = cache.current(PROVIDER, MODEL);
        assertThat(found.measuredSecondsPerSegment()).isEqualTo(1.2);
        assertThat(found.measuredFrom()).isEqualTo(MeasuredFrom.RUN);
    }

    @Test
    void remember_temperature_addsRowAndKeepsOtherSettings() {
        repository.save(new ModelSettings(
                PROVIDER,
                MODEL,
                TemperatureChoice.VALUE,
                0.7,
                8192,
                512,
                OutputLengthField.MAX_TOKENS,
                List.of(),
                null,
                null));

        final boolean stored = memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);

        assertThat(stored).isTrue();
        final ModelSettings saved = cache.current(PROVIDER, MODEL);
        assertThat(saved.refused()).containsExactly(new RefusedSetting("temperature", DAY, "omitted"));
        assertThat(saved.temperatureChoice()).isEqualTo(TemperatureChoice.VALUE);
        assertThat(saved.temperature()).isEqualTo(0.7);
        assertThat(saved.contextWindow()).isEqualTo(8192);
        assertThat(saved.maxOutput()).isEqualTo(512);
    }

    @Test
    void remember_sameSettingTwice_keepsOneRow() {
        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);
        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);

        assertThat(cache.current(PROVIDER, MODEL).refused()).hasSize(1);
    }

    // The field is not flipped on the stored settings, so Send again truly reverts it.
    @Test
    void remember_outputField_storesMaxTokensWithoutChangingTheOutputField() {
        memory.remember(PROVIDER, MODEL, RejectedCapability.OUTPUT_FIELD);

        final ModelSettings saved = cache.current(PROVIDER, MODEL);
        assertThat(saved.refused()).containsExactly(new RefusedSetting("max_tokens", DAY, "max_completion_tokens"));
        assertThat(saved.outputField()).isEqualTo(OutputLengthField.MAX_TOKENS);
    }

    @Test
    void remember_oneModel_leavesAnotherModelOfTheProviderAlone() {
        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);

        assertThat(memory.remembered(cache.current(PROVIDER, "gemma4:e4b"))).isEmpty();
        assertThat(memory.remembered(cache.current(PROVIDER, MODEL))).containsExactly(RejectedCapability.TEMPERATURE);
    }

    @Test
    void withoutRemembered_temperatureRefusedUnderMyValue_sendsNoTemperature() {
        repository.save(new ModelSettings(
                PROVIDER,
                MODEL,
                TemperatureChoice.VALUE,
                0.7,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(),
                null,
                null));
        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);

        final ChatRequest stripped = memory.withoutRemembered(request(0.7), cache.current(PROVIDER, MODEL));

        assertThat(stripped.temperature()).isNull();
        assertThat(stripped.messages()).hasSize(1);
    }

    // Send again is the person's save of the record without the row.
    @Test
    void withoutRemembered_afterTheRowIsRemoved_keepsTheTemperature() {
        memory.remember(PROVIDER, MODEL, RejectedCapability.TEMPERATURE);
        final ModelSettings withRow = cache.current(PROVIDER, MODEL);
        repository.save(new ModelSettings(
                PROVIDER,
                MODEL,
                withRow.temperatureChoice(),
                withRow.temperature(),
                withRow.contextWindow(),
                withRow.maxOutput(),
                withRow.outputField(),
                List.of(),
                null,
                null));

        final ChatRequest kept = memory.withoutRemembered(request(0.2), cache.current(PROVIDER, MODEL));

        assertThat(kept.temperature()).isEqualTo(0.2);
    }

    @Test
    void remembered_storedNameNobodyKnows_isIgnored() {
        repository.save(new ModelSettings(
                PROVIDER,
                MODEL,
                TemperatureChoice.CHOOSE,
                null,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(new RefusedSetting("frequency_penalty", DAY, "omitted")),
                null,
                null));

        assertThat(memory.remembered(cache.current(PROVIDER, MODEL))).isEmpty();
    }

    // IF remembering rewrote the whole record from an earlier read, THEN a speed stored in between would be lost.
    @Test
    void remember_speedAlreadyStored_keepsTheSpeed() {
        repository.update(PROVIDER, MODEL, row -> row.withMeasuredSpeed(1.2, MeasuredFrom.TEST));

        memory.remember(PROVIDER, MODEL, RejectedCapability.SEED);

        final ModelSettings found = cache.current(PROVIDER, MODEL);
        assertThat(found.measuredSecondsPerSegment()).isEqualTo(1.2);
        assertThat(found.refused()).hasSize(1);
    }

    @Test
    void remember_storeCannotSave_returnsFalseWithoutThrowing() {
        final ModelSettingsRepository failing = new ModelSettingsRepository() {
            @Override
            public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
                return repository.find(providerId, modelId);
            }

            @Override
            public Result<ModelSettings> save(final ModelSettings settings) {
                return Result.err(AppError.of(ErrorCode.internal, "Save failed", "The store is closed."));
            }

            @Override
            public Result<ModelSettings> update(
                    final String providerId, final String modelId, final UnaryOperator<ModelSettings> change) {
                return Result.err(AppError.of(ErrorCode.internal, "Save failed", "The store is closed."));
            }

            @Override
            public Result<Boolean> delete(final String providerId, final String modelId) {
                return repository.delete(providerId, modelId);
            }

            @Override
            public Result<List<ModelSettings>> all() {
                return repository.all();
            }
        };

        final boolean stored = new RefusalMemory(new ModelSettingsCache(failing), CLOCK)
                .remember(PROVIDER, MODEL, RejectedCapability.SEED);

        assertThat(stored).isFalse();
    }

    // Eight settings refused at once on one model all survive; none overwrites another.
    @Test
    void remember_eightThreadsOneModel_keepsAllEightRows() {
        final ExecutorService pool = Executors.newFixedThreadPool(EIGHT);
        try {
            CompletableFuture.allOf(EnumSet.complementOf(EnumSet.of(
                                    RejectedCapability.STRUCTURED_OUTPUT, RejectedCapability.REASONING_CONTROL))
                            .stream()
                            .map(setting ->
                                    CompletableFuture.runAsync(() -> memory.remember(PROVIDER, MODEL, setting), pool))
                            .toArray(CompletableFuture[]::new))
                    .join();
        } finally {
            pool.shutdownNow();
        }

        assertThat(cache.current(PROVIDER, MODEL).refused()).hasSize(EIGHT);
    }

    private static ChatRequest request(final double temperature) {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate this.")), temperature, null);
    }
}
