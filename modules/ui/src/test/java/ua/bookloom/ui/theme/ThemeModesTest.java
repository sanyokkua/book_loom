package ua.bookloom.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The stored theme and the mode map one to one, so a choice survives a restart unchanged. */
class ThemeModesTest {

    @ParameterizedTest
    @EnumSource(ThemeMode.class)
    void toSettingAndBack_everyMode_same(final ThemeMode mode) {
        assertThat(ThemeModes.of(ThemeModes.toSetting(mode))).isEqualTo(mode);
    }

    @ParameterizedTest
    @CsvSource({"LIGHT, LIGHT", "DARK, DARK", "SYSTEM, SYSTEM"})
    void toSetting_mode_isTheSameNamedSetting(final ThemeMode mode, final String setting) {
        assertThat(ThemeModes.toSetting(mode).name()).isEqualTo(setting);
    }
}
