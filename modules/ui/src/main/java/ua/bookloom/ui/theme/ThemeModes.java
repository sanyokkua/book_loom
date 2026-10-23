package ua.bookloom.ui.theme;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.settings.ThemeSetting;

/** The one place a stored theme and the window's theme mode are turned into each other. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThemeModes {

    /**
     * The stored form of a mode.
     *
     * @param mode the mode; never null
     * @return the setting that stores it
     */
    public static ThemeSetting toSetting(final ThemeMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case LIGHT -> ThemeSetting.LIGHT;
            case DARK -> ThemeSetting.DARK;
            case SYSTEM -> ThemeSetting.SYSTEM;
        };
    }

    /**
     * The mode a stored theme stands for.
     *
     * @param setting the stored theme; never null
     * @return the mode
     */
    public static ThemeMode of(final ThemeSetting setting) {
        return switch (Objects.requireNonNull(setting, "setting")) {
            case LIGHT -> ThemeMode.LIGHT;
            case DARK -> ThemeMode.DARK;
            case SYSTEM -> ThemeMode.SYSTEM;
        };
    }
}
