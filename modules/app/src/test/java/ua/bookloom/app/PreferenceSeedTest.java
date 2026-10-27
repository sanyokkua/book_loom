package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.ThemeSetting;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.persistence.sqlite.SqliteDatabase;
import ua.bookloom.ui.Theme;
import ua.bookloom.ui.UiModule;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.i18n.OsLocaleProvider;
import ua.bookloom.ui.i18n.SystemLanguage;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeMode;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The stored language and theme reach the window of the next launch: they are read in {@code init()}, before the
 * first message is resolved and before the scene is themed.
 */
class PreferenceSeedTest {

    private static final long FX_WAIT_SECONDS = 10;
    private static final Locale UKRAINIAN_OS = Locale.forLanguageTag("uk-UA");

    @TempDir
    private Path dataDir;

    private StartupContext startup() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
    }

    /** A fresh launch over the same database file, with the operating system's language named. */
    private Injector launch(final StartupContext startup, final Locale os) {
        final Injector injector = Guice.createInjector(
                BookLoomApplication.windowCore(startup),
                Modules.override(new UiModule())
                        .with(binder -> binder.bind(SystemLanguage.class).toInstance(() -> os)));
        final AppLifecycle lifecycle = new AppLifecycle();
        lifecycle.phaseOne(injector);
        lifecycle.phaseTwo(injector);
        return injector;
    }

    private void storedBefore(final StartupContext startup, final SettingsWriter write) {
        final Injector first = launch(startup, Locale.ENGLISH);
        write.accept(first.getInstance(SettingsRepository.class));
        first.getInstance(SqliteDatabase.class).close();
    }

    @FunctionalInterface
    private interface SettingsWriter {
        void accept(SettingsRepository settings);
    }

    private String languageOfNextLaunch(final StartupContext startup, final Locale os) {
        final Injector second = launch(startup, os);
        BookLoomApplication.seedPreferences(second);
        final String language = second.getInstance(Messages.class).locale().getLanguage();
        second.getInstance(SqliteDatabase.class).close();
        return language;
    }

    @Test
    void seed_storedUkOsEnglish_messagesUkrainian() throws Exception {
        final StartupContext startup = startup();
        storedBefore(startup, settings -> settings.put(AppSetting.LANGUAGE, "uk"));

        assertThat(languageOfNextLaunch(startup, Locale.ENGLISH)).isEqualTo("uk");
    }

    @Test
    void seed_storedEnOsUkrainian_messagesEnglish() throws Exception {
        final StartupContext startup = startup();
        storedBefore(startup, settings -> settings.put(AppSetting.LANGUAGE, "en"));

        assertThat(languageOfNextLaunch(startup, UKRAINIAN_OS)).isEqualTo("en");
    }

    // The development-stage graph (the real window) builds Messages lazily, so the stored choice is read first. The
    // resolve-all smoke builds the production stage, which constructs ProviderForm and RunStatusBar (and so decides the
    // language) before init() can seed; that throwaway run is the only path allowed to say so.
    @Test
    void seed_developmentStage_languageNeverDecidedBeforeTheStoredChoice() throws Exception {
        final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OsLocaleProvider.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> lines =
                new ch.qos.logback.core.read.ListAppender<>();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        lines.start();
        logger.addAppender(lines);
        final StartupContext startup = startup();
        try {
            storedBefore(startup, settings -> settings.put(AppSetting.LANGUAGE, "uk"));
            languageOfNextLaunch(startup, Locale.ENGLISH);
        } finally {
            logger.detachAppender(lines);
        }

        assertThat(lines.list)
                .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .anyMatch(text -> text.contains("comes from the stored choice"))
                .noneMatch(text -> text.contains("decided before the stored choice was read"));
    }

    @Test
    void seed_nothingStoredOsUkrainian_messagesUkrainian() throws Exception {
        assertThat(languageOfNextLaunch(startup(), UKRAINIAN_OS)).isEqualTo("uk");
    }

    @Test
    void seed_darkStored_newInjectorOverSameFileAttachesDark() throws Exception {
        final StartupContext startup = startup();
        storedBefore(startup, settings -> settings.put(AppSetting.THEME, ThemeSetting.DARK));
        final Injector second = launch(startup, Locale.ENGLISH);
        BookLoomApplication.seedPreferences(second);
        final ThemeController theme = second.getInstance(ThemeController.class);

        final Scene scene = attachOnFx(theme);
        second.getInstance(SqliteDatabase.class).close();

        assertThat(theme.mode()).isEqualTo(ThemeMode.DARK);
        assertThat(scene.getRoot().getStyleClass()).contains(Theme.DARK_STYLE_CLASS);
    }

    private static Scene attachOnFx(final ThemeController theme) throws Exception {
        try {
            final CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(FX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit is shared by the test JVM; a running one is what is wanted.
        }
        final CompletableFuture<Scene> attached = new CompletableFuture<>();
        Platform.runLater(() -> {
            final Scene scene = new Scene(new StackPane(), 100, 100);
            theme.attach(scene);
            attached.complete(scene);
        });
        return attached.get(FX_WAIT_SECONDS, TimeUnit.SECONDS);
    }
}
