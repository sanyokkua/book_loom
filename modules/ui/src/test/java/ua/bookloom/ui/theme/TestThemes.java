package ua.bookloom.ui.theme;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.ui.SettingWriter;

/** Builds a {@link ThemeController} whose store writes happen on the calling thread, so a test reads them at once. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestThemes {

    /**
     * A controller that reports no operating-system preference.
     *
     * @param settings where it stores a choice
     * @return the controller
     */
    public static ThemeController over(final SettingsRepository settings) {
        return new ThemeController(
                () -> java.util.Optional.empty(), new SettingWriter(settings, Runnable::run, Runnable::run));
    }
}
