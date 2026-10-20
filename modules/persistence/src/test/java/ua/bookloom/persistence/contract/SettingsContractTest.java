package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ThemeSetting;

/** Behaviour every {@link SettingsRepository} adapter must show. */
public abstract class SettingsContractTest {

    protected abstract SettingsRepository settings();

    static Stream<Arguments> roundTrips() {
        return Stream.of(
                Arguments.of(AppSetting.THEME, ThemeSetting.DARK),
                Arguments.of(AppSetting.LANGUAGE, "uk"),
                Arguments.of(AppSetting.KEEP_AWAKE, false),
                Arguments.of(AppSetting.DEFAULT_DIAL, QualityDial.MAX),
                Arguments.of(AppSetting.CURRENT_MODEL, new ModelSelection("ollama", "gemma4:e4b")),
                Arguments.of(AppSetting.LAST_PROJECT, "p-1"),
                Arguments.of(AppSetting.FIRST_LAUNCH_PROBED, true),
                Arguments.of(AppSetting.sleepLineClosed("p-1"), true));
    }

    @Test
    void get_unsetWithDefault_returnsTheDefault() {
        assertThat(settings().get(AppSetting.THEME).data()).hasValue(ThemeSetting.SYSTEM);
        assertThat(settings().get(AppSetting.KEEP_AWAKE).data()).hasValue(true);
    }

    @Test
    void get_unsetWithoutDefault_returnsEmpty() {
        assertThat(settings().get(AppSetting.LANGUAGE).data()).isEmpty();
        assertThat(settings().get(AppSetting.CURRENT_MODEL).data()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("roundTrips")
    <T> void put_thenGet_returnsTheStoredValue(final AppSetting<T> setting, final T value) {
        assertThat(settings().put(setting, value).isOk()).isTrue();

        assertThat(settings().get(setting).data()).hasValue(value);
    }

    @Test
    void put_twice_keepsTheLastValue() {
        settings().put(AppSetting.THEME, ThemeSetting.DARK);
        settings().put(AppSetting.THEME, ThemeSetting.LIGHT);

        assertThat(settings().get(AppSetting.THEME).data()).hasValue(ThemeSetting.LIGHT);
    }

    @Test
    void put_aValueEqualToTheDefault_isStillStored() {
        settings().put(AppSetting.KEEP_AWAKE, true);

        assertThat(settings().all().data()).containsEntry("automation.keepAwake", "true");
    }

    @Test
    void all_afterPuts_listsTheRawStoredPairs() {
        settings().put(AppSetting.THEME, ThemeSetting.DARK);
        settings().put(AppSetting.LAST_PROJECT, "p-1");

        assertThat(settings().all().data())
                .containsOnly(java.util.Map.entry("ui.theme", "dark"), java.util.Map.entry("app.lastProject", "p-1"));
    }

    @Test
    void all_whenNothingStored_isEmpty() {
        assertThat(settings().all().data()).isEmpty();
    }
}
