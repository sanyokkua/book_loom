package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.persistence.ModelSettingsRepository;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/** The cache reads through the repository so a save by any writer is seen by the next request. */
class ModelSettingsCacheTest {

    private static final String PROVIDER = "lmstudio";
    private static final String MODEL = "test-model";

    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();

    @Test
    void current_nothingStored_returnsDefaults() {
        final ModelSettings found = new ModelSettingsCache(repository).current(PROVIDER, MODEL);

        assertThat(found).isEqualTo(ModelSettings.defaults(PROVIDER, MODEL));
    }

    // A save made straight into the repository (by the Models tab) is seen without the cache being told.
    @Test
    void current_afterRepositorySave_returnsSaved() {
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        cache.current(PROVIDER, MODEL);
        repository.save(saved(0.4));

        assertThat(cache.current(PROVIDER, MODEL).temperature()).isEqualTo(0.4);
    }

    // IF the repository cannot be read, THEN the last settings seen stay in force.
    @Test
    void current_repositoryFails_returnsLastSeenAndWarns() {
        final FailingAfterFirstRead failing = new FailingAfterFirstRead();
        failing.stored = saved(0.4);
        final ModelSettingsCache cache = new ModelSettingsCache(failing);
        cache.current(PROVIDER, MODEL);

        assertThat(cache.current(PROVIDER, MODEL).temperature()).isEqualTo(0.4);
    }

    @Test
    void save_writesThroughAndIsReadBack() {
        final ModelSettingsCache cache = new ModelSettingsCache(repository);

        cache.save(saved(0.6));

        assertThat(repository.find(PROVIDER, MODEL).data())
                .hasValueSatisfying(value -> assertThat(value.temperature()).isEqualTo(0.6));
        assertThat(cache.current(PROVIDER, MODEL).temperature()).isEqualTo(0.6);
    }

    private static ModelSettings saved(final double temperature) {
        return new ModelSettings(
                PROVIDER,
                MODEL,
                TemperatureChoice.VALUE,
                temperature,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(),
                null);
    }

    /** Answers the first read, then fails every later one. */
    private static final class FailingAfterFirstRead implements ModelSettingsRepository {

        private @Nullable ModelSettings stored;
        private int reads;

        @Override
        public Result<Optional<ModelSettings>> find(final String providerId, final String modelId) {
            reads++;
            return reads == 1
                    ? Result.ok(Optional.ofNullable(stored))
                    : Result.err(AppError.of(ErrorCode.internal, "Unreadable", "The store cannot be read."));
        }

        @Override
        public Result<ModelSettings> save(final ModelSettings settings) {
            return Result.ok(settings);
        }

        @Override
        public Result<Boolean> delete(final String providerId, final String modelId) {
            return Result.ok(false);
        }

        @Override
        public Result<List<ModelSettings>> all() {
            return Result.ok(List.of());
        }
    }
}
