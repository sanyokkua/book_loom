package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ThemeSetting;
import ua.bookloom.ui.i18n.StoredLanguage;
import ua.bookloom.ui.state.KeepAwakeGate;
import ua.bookloom.ui.theme.TestThemes;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeMode;

/** The language and the theme are read once at startup, off the FX thread, and handed to who decides them. */
class StoredPreferencesTest extends FxTestBase {

    private static final long WAIT_SECONDS = 10;

    private final StoredLanguage language = new StoredLanguage();
    private final KeepAwakeGate gate = new KeepAwakeGate();

    @Override
    public void start(final Stage stage) {
        stage.setScene(new Scene(new StackPane(), 100, 100));
    }

    private ThemeController theme(final MemorySettings settings) {
        return TestThemes.over(settings);
    }

    private ThemeMode modeAfterAttach(final ThemeController theme) {
        return ThemeTestSupport.onFx(() -> {
            theme.attach(new Scene(new StackPane(), 100, 100));
            return theme.mode();
        });
    }

    @Test
    void load_ukAndDarkStored_seedsBoth() {
        final MemorySettings settings = new MemorySettings();
        settings.put(AppSetting.LANGUAGE, "uk");
        settings.put(AppSetting.THEME, ThemeSetting.DARK);
        final ThemeController theme = theme(settings);

        new StoredPreferences(new SettingWriter(settings, Runnable::run, Runnable::run), language, theme, gate).load();

        assertThat(language.get()).contains("uk");
        assertThat(language.isSeeded()).isTrue();
        assertThat(modeAfterAttach(theme)).isEqualTo(ThemeMode.DARK);
    }

    @Test
    void load_nothingStored_seedsNoLanguageAndSystemTheme() {
        final MemorySettings settings = new MemorySettings();
        final ThemeController theme = theme(settings);

        new StoredPreferences(new SettingWriter(settings, Runnable::run, Runnable::run), language, theme, gate).load();

        assertThat(language.get()).isEmpty();
        assertThat(language.isSeeded()).isTrue();
        assertThat(modeAfterAttach(theme)).isEqualTo(ThemeMode.SYSTEM);
    }

    @Test
    void load_storeUnreadable_seedsNothing() {
        final MemorySettings settings = new MemorySettings().failGets();
        final ThemeController theme = theme(settings);

        new StoredPreferences(new SettingWriter(settings, Runnable::run, Runnable::run), language, theme, gate).load();

        assertThat(language.get()).isEmpty();
        assertThat(modeAfterAttach(theme)).isEqualTo(ThemeMode.SYSTEM);
    }

    @Test
    void load_keepAwakeOffStored_gateSeededOff() {
        final MemorySettings settings = new MemorySettings();
        settings.put(AppSetting.KEEP_AWAKE, false);

        new StoredPreferences(
                        new SettingWriter(settings, Runnable::run, Runnable::run), language, theme(settings), gate)
                .load();

        assertThat(gate.isOn()).isFalse();
    }

    @Test
    void load_keepAwakeUnreadable_gateStaysOn() {
        final MemorySettings settings = new MemorySettings().failGets();

        new StoredPreferences(
                        new SettingWriter(settings, Runnable::run, Runnable::run), language, theme(settings), gate)
                .load();

        assertThat(gate.isOn()).isTrue();
    }

    @Test
    void load_onFxThread_throws() {
        final MemorySettings settings = new MemorySettings();
        final StoredPreferences preferences = new StoredPreferences(
                new SettingWriter(settings, Runnable::run, Runnable::run), language, theme(settings), gate);
        final CompletableFuture<Void> ran = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                preferences.load();
                ran.complete(null);
            } catch (IllegalStateException e) {
                ran.completeExceptionally(e);
            }
        });

        assertThatThrownBy(() -> ran.get(WAIT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(settings.fxThreadCalls()).isZero();
    }
}
