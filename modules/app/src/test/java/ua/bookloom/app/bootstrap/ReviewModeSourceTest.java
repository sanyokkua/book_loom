package ua.bookloom.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.app.StartupContext;
import ua.bookloom.persistence.memory.InMemorySettingsRepository;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/** The review mode a run starts with: a flag set on purpose, else the stored choice, else Unattended. */
class ReviewModeSourceTest {

    private static final long FX_WAIT_SECONDS = 10;

    @TempDir
    private Path dataDir;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger sourceLog = (Logger) LoggerFactory.getLogger(ReviewModeSource.class);

    @BeforeEach
    void captureLog() {
        sourceLog.setLevel(Level.DEBUG);
        appender.start();
        sourceLog.addAppender(appender);
    }

    @AfterEach
    void releaseLog() {
        sourceLog.detachAppender(appender);
        appender.stop();
    }

    private StartupContext startup(@Nullable String env, @Nullable String property) {
        final Function<String, @Nullable String> getEnv = lookup("BOOKLOOM_REVIEW_MODE", env);
        final Function<String, @Nullable String> getProperty = lookup("bookloom.review.mode", property);
        return new StartupContext(
                AppPaths.of(dataDir, dataDir), AppEnvironment.DEV, ReviewModeResolver.resolve(getEnv, getProperty));
    }

    private static Function<String, @Nullable String> lookup(String name, @Nullable String value) {
        final Map<String, String> values = new HashMap<>();
        values.put(name, value);
        return values::get;
    }

    private static SettingsRepository storing(@Nullable ReviewMode stored) {
        final SettingsRepository repository = new InMemorySettingsRepository();
        if (stored != null) {
            repository.put(AppSetting.REVIEW_MODE, stored);
        }
        return repository;
    }

    // IF a flag was set on purpose (environment, else property, or a typo), THEN it wins over the stored choice;
    // IF none was set, THEN the stored choice applies, and with nothing stored the run is Unattended.
    @ParameterizedTest
    @CsvSource(
            nullValues = "NIL",
            value = {
                "manual, NIL, ASSISTED, MANUAL",
                "NIL, manual, ASSISTED, MANUAL",
                "auto, manual, MANUAL, UNATTENDED",
                "strict, NIL, MANUAL, UNATTENDED",
                "NIL, NIL, ASSISTED, ASSISTED",
                "NIL, NIL, MANUAL, MANUAL",
                "NIL, NIL, NIL, UNATTENDED"
            })
    void current_flagAndStoredChoice_pickTheModeTheRunUses(
            @Nullable String env, @Nullable String property, @Nullable ReviewMode stored, ReviewMode expected) {
        final ReviewModeSource source = new ReviewModeSource(startup(env, property), storing(stored));

        assertThat(source.current()).isEqualTo(expected);
    }

    // IF the setting is changed between two reads, THEN the next read sees it without a restart.
    @Test
    void current_storedChoiceChanges_nextReadSeesItWithoutRestart() {
        final SettingsRepository repository = storing(null);
        final ReviewModeSource source = new ReviewModeSource(startup(null, null), repository);

        final ReviewMode before = source.current();
        repository.put(AppSetting.REVIEW_MODE, ReviewMode.MANUAL);

        assertThat(before).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(source.current()).isEqualTo(ReviewMode.MANUAL);
    }

    // IF the store cannot be read, THEN the run is Unattended and one WARN says so.
    @Test
    void current_storeUnreadable_isUnattendedWithOneWarning() {
        final ReviewModeSource source = new ReviewModeSource(startup(null, null), new UnreadableSettings());

        assertThat(source.current()).isEqualTo(ReviewMode.UNATTENDED);
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .hasSize(1);
    }

    // IF the mode comes from the stored setting, THEN a DEBUG line names the mode and where it came from.
    @Test
    void current_storedChoice_logsTheModeAndItsSource() {
        new ReviewModeSource(startup(null, null), storing(ReviewMode.ASSISTED)).current();

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.DEBUG)
                .anyMatch(event -> event.getFormattedMessage().contains("ASSISTED")
                        && event.getFormattedMessage().contains("stored"));
    }

    @Test
    void launchOverride_envSet_namesBookloomReviewMode() {
        final ReviewModeSource source = new ReviewModeSource(startup("manual", null), storing(null));

        assertThat(source.launchOverride()).contains("BOOKLOOM_REVIEW_MODE");
    }

    @Test
    void launchOverride_rejectedPropertyValue_namesProperty() {
        final ReviewModeSource source = new ReviewModeSource(startup(null, "strict"), storing(null));

        assertThat(source.launchOverride()).contains("bookloom.review.mode");
    }

    @Test
    void launchOverride_nothingSet_empty() {
        final ReviewModeSource source = new ReviewModeSource(startup(null, null), storing(null));

        assertThat(source.launchOverride()).isEmpty();
    }

    @Test
    void current_envUnattendedStoredManual_unattended() {
        final ReviewModeSource source = new ReviewModeSource(startup("unattended", null), storing(ReviewMode.MANUAL));

        assertThat(source.current()).isEqualTo(ReviewMode.UNATTENDED);
    }

    @Test
    void current_nothingStoredNoFlag_unattended() {
        final ReviewModeSource source = new ReviewModeSource(startup(null, null), storing(null));

        assertThat(source.current()).isEqualTo(ReviewMode.UNATTENDED);
    }

    @Test
    void stored_manual_fxReadSeesManual() throws Exception {
        final ReviewModeSource source = new ReviewModeSource(startup(null, null), storing(null));
        source.stored(ReviewMode.MANUAL);

        assertThat(readOnFxThread(source)).isEqualTo(ReviewMode.MANUAL);
    }

    private static ReviewMode readOnFxThread(final ReviewModeSource source) throws Exception {
        try {
            final CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(FX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            // The toolkit is shared by the test JVM; a running one is what is wanted.
        }
        final CompletableFuture<ReviewMode> read = new CompletableFuture<>();
        Platform.runLater(() -> read.complete(source.current()));
        return read.get(FX_WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class UnreadableSettings implements SettingsRepository {

        @Override
        public <T> Result<Optional<T>> get(AppSetting<T> setting) {
            return Result.err(AppError.of(ErrorCode.internal, "Settings", "unreadable"));
        }

        @Override
        public <T> Result<T> put(AppSetting<T> setting, T value) {
            return Result.err(AppError.of(ErrorCode.internal, "Settings", "unreadable"));
        }

        @Override
        public Result<Boolean> remove(AppSetting<?> setting) {
            return Result.err(AppError.of(ErrorCode.internal, "Settings", "unreadable"));
        }

        @Override
        public Result<Map<String, String>> all() {
            return Result.err(AppError.of(ErrorCode.internal, "Settings", "unreadable"));
        }
    }
}
