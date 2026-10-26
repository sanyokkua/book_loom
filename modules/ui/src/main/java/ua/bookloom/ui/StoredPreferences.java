package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.i18n.StoredLanguage;
import ua.bookloom.ui.state.KeepAwakeGate;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeModes;

/**
 * Reads the choices that must be known before the first window is built, the app language, the theme and keep-awake, and
 * hands them to the objects that decide them. The store refuses the FX thread, so this runs once at startup, off it.
 */
@Slf4j
@Singleton
public final class StoredPreferences {

    private final SettingWriter settings;
    private final StoredLanguage language;
    private final ThemeController theme;
    private final KeepAwakeGate gate;

    /**
     * Creates the reader.
     *
     * @param settings reads the choices, an unreadable one as its default
     * @param language receives the stored language choice
     * @param theme receives the stored theme
     * @param gate receives the stored keep-awake choice
     */
    @Inject
    public StoredPreferences(
            final SettingWriter settings,
            final StoredLanguage language,
            final ThemeController theme,
            final KeepAwakeGate gate) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.language = Objects.requireNonNull(language, "language");
        this.theme = Objects.requireNonNull(theme, "theme");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    /**
     * Reads the choices and seeds them; an unreadable store seeds the defaults, never an error.
     *
     * @throws IllegalStateException when called on the FX Application Thread, where the store must not be read
     */
    public void load() {
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("stored preferences are read off the FX thread");
        }
        log.debug("loading the stored language and theme");
        language.seed(settings.stored(AppSetting.LANGUAGE));
        theme.seed(ThemeModes.of(settings.read(AppSetting.THEME)));
        gate.seed(settings.read(AppSetting.KEEP_AWAKE));
    }
}
