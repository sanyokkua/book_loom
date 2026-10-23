package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.persistence.memory.InMemorySettingsRepository;

/**
 * The reasoning level and the sampling mode a run starts with: the environment variable (else the property) when set,
 * as a whole replacement of the stored Generation setting; else the stored setting; else Off and the server's own
 * sampling (finalize 4.8a).
 */
class RequestDefaultsSourceTest {

    private final SettingsRepository settings = new InMemorySettingsRepository();

    private RequestDefaultsSource source(
            final Map<String, String> env,
            @Nullable final String reasoningProperty,
            @Nullable final String samplingProperty) {
        final Supplier<Map<String, String>> environment = () -> env;
        return new RequestDefaultsSource(environment, () -> reasoningProperty, () -> samplingProperty, settings);
    }

    private RequestDefaultsSource source(final Map<String, String> env) {
        return source(env, null, null);
    }

    // IF BOOKLOOM_REASONING=low is set while Off is stored, THEN every call asks for Low.
    @ParameterizedTest
    @EnumSource(PromptName.class)
    void current_envLowWithStoredOff_everyCallIsLow(final PromptName name) {
        settings.put(AppSetting.REASONING, ReasoningSetting.OFF);

        final RequestDefaults defaults =
                source(Map.of(ReasoningPlan.ENV_NAME, "low")).current();

        assertThat(defaults.reasoningFor(name)).isEqualTo(ReasoningLevel.LOW);
    }

    // IF the environment variable is unset, THEN the stored level applies to every call.
    @ParameterizedTest
    @CsvSource({"OFF,OFF", "LOW,LOW", "ON,ON"})
    void current_envUnsetStoredLevel_everyCallCarriesIt(final ReasoningSetting stored, final ReasoningLevel expected) {
        settings.put(AppSetting.REASONING, stored);

        final RequestDefaults defaults = source(Map.of()).current();

        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(expected);
        assertThat(defaults.reasoningFor(PromptName.REVIEWER)).isEqualTo(expected);
    }

    @Test
    void current_nothingStored_isOffForEveryCall() {
        assertThat(source(Map.of()).current().reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.OFF);
    }

    // The set environment plan replaces the stored level as a whole: Low stored, DRAFT=off set, so the reviewer is Off
    // too.
    @Test
    void current_envNamesDraftOffWithStoredLow_replacesTheStoredLevelAsAWhole() {
        settings.put(AppSetting.REASONING, ReasoningSetting.LOW);

        final RequestDefaults defaults =
                source(Map.of(ReasoningPlan.ENV_NAME, "DRAFT=off")).current();

        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.OFF);
        assertThat(defaults.reasoningFor(PromptName.REVIEWER)).isEqualTo(ReasoningLevel.OFF);
    }

    @Test
    void current_propertyWithEnvUnset_isUsed() {
        settings.put(AppSetting.REASONING, ReasoningSetting.OFF);

        final RequestDefaults defaults = source(Map.of(), "REVIEWER=on", null).current();

        assertThat(defaults.reasoningFor(PromptName.REVIEWER)).isEqualTo(ReasoningLevel.ON);
        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.OFF);
    }

    @Test
    void current_envAndProperty_envWins() {
        final RequestDefaults defaults =
                source(Map.of(ReasoningPlan.ENV_NAME, "low"), "on", null).current();

        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.LOW);
    }

    // Sampling: the environment mode wins over the stored one.
    @Test
    void current_envTunedWithStoredProgramDefaults_isTuned() {
        settings.put(AppSetting.SAMPLING, SamplingSetting.PROGRAM_DEFAULTS);

        assertThat(source(Map.of(SamplingMode.ENV_NAME, "tuned")).current().sampling())
                .isEqualTo(SamplingMode.TUNED);
    }

    @Test
    void current_samplingEnvUnsetStoredTuned_isTuned() {
        settings.put(AppSetting.SAMPLING, SamplingSetting.TUNED);

        assertThat(source(Map.of()).current().sampling()).isEqualTo(SamplingMode.TUNED);
    }

    @Test
    void current_samplingStoredProgramDefaults_isServer() {
        settings.put(AppSetting.SAMPLING, SamplingSetting.PROGRAM_DEFAULTS);

        assertThat(source(Map.of()).current().sampling()).isEqualTo(SamplingMode.SERVER);
    }

    @Test
    void current_samplingNothingStored_isServer() {
        assertThat(source(Map.of()).current().sampling()).isEqualTo(SamplingMode.SERVER);
    }

    // IF the environment names a mode nobody knows, THEN the stored setting is used instead of failing.
    @Test
    void current_unknownSamplingEnvWithStoredTuned_fallsBackToStored() {
        settings.put(AppSetting.SAMPLING, SamplingSetting.TUNED);

        assertThat(source(Map.of(SamplingMode.ENV_NAME, "bogus")).current().sampling())
                .isEqualTo(SamplingMode.TUNED);
    }

    // Reasoning and sampling resolve independently of each other.
    @Test
    void current_reasoningFromEnvSamplingFromStore_resolveIndependently() {
        settings.put(AppSetting.SAMPLING, SamplingSetting.TUNED);

        final RequestDefaults defaults =
                source(Map.of(ReasoningPlan.ENV_NAME, "on")).current();

        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.ON);
        assertThat(defaults.sampling()).isEqualTo(SamplingMode.TUNED);
    }

    // IF the stored settings cannot be read, THEN the run starts with today's defaults, never fails.
    @Test
    void current_settingsReadFails_usesOffAndServer() {
        final SettingsRepository broken = new SettingsRepository() {
            @Override
            public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
                return Result.err(AppError.of(ErrorCode.internal, "Unreadable", "The settings could not be read."));
            }

            @Override
            public <T> Result<T> put(final AppSetting<T> setting, final T value) {
                return Result.ok(value);
            }

            @Override
            public Result<Boolean> remove(final AppSetting<?> setting) {
                return Result.ok(false);
            }

            @Override
            public Result<Map<String, String>> all() {
                return Result.ok(Map.of());
            }
        };

        final RequestDefaults defaults = new RequestDefaultsSource(Map::of, () -> null, () -> null, broken).current();

        assertThat(defaults.reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.OFF);
        assertThat(defaults.sampling()).isEqualTo(SamplingMode.SERVER);
    }

    // The next run sees a change made in Settings in between: nothing is cached.
    @Test
    void current_storedLevelChangesBetweenCalls_secondCallSeesTheNewLevel() {
        final RequestDefaultsSource source = source(Map.of());
        settings.put(AppSetting.REASONING, ReasoningSetting.OFF);
        assertThat(source.current().reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.OFF);

        settings.put(AppSetting.REASONING, ReasoningSetting.LOW);

        assertThat(source.current().reasoningFor(PromptName.DRAFT)).isEqualTo(ReasoningLevel.LOW);
    }
}
