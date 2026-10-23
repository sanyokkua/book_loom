package ua.bookloom.ui;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.api.pipeline.DialProfiles;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReviewModeChoice;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.SystemLanguage;
import ua.bookloom.ui.theme.ColorSchemeProvider;
import ua.bookloom.ui.theme.ThemeBlock;

/** What a test graph says about the launch: the operating system, the build, the logs, storage and overrides. */
final class LaunchPorts {

    private StorageInfo storage = ScriptedStorageInfo.typical();
    private ReviewModeChoice reviewModeChoice = new RecordingReviewModeChoice();
    private DiagnosticLog diagnosticLog = DiagnosticLogs.of(UiTestInjector.TEST_LOG_DIR, false);
    private AppRestart restart = new RecordingAppRestart();
    private GenerationOverrides overrides = ScriptedGenerationOverrides.none();
    private @Nullable Locale osLocale;
    private Optional<ThemeBlock> colorScheme = Optional.of(ThemeBlock.LIGHT);

    void storage(final StorageInfo value) {
        storage = value;
    }

    void reviewModeChoice(final ReviewModeChoice value) {
        reviewModeChoice = value;
    }

    void diagnosticLog(final DiagnosticLog value) {
        diagnosticLog = value;
    }

    void restart(final AppRestart value) {
        restart = value;
    }

    void overrides(final GenerationOverrides value) {
        overrides = value;
    }

    void osLocale(final Locale value) {
        osLocale = value;
    }

    void colorScheme(final Optional<ThemeBlock> value) {
        colorScheme = value;
    }

    /** The bindings, for a graph whose display language is {@code locale}. */
    Module module(final Locale locale) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(LocaleProvider.class).toInstance(() -> locale);
                bind(ColorSchemeProvider.class).toInstance(() -> colorScheme);
                bind(SystemLanguage.class).toInstance(() -> osLocale != null ? osLocale : locale);
                bind(AppRestart.class).toInstance(restart);
                bind(GenerationOverrides.class).toInstance(overrides);
                bind(StorageInfo.class).toInstance(storage);
                bind(ReviewModeChoice.class).toInstance(reviewModeChoice);
                bind(DialProfiles.class).toInstance(new FixedDialProfiles());
                bind(String.class).annotatedWith(BuildVersion.class).toInstance(UiTestInjector.DEV_VERSION);
                bind(DiagnosticLog.class).toInstance(diagnosticLog);
            }
        };
    }
}
