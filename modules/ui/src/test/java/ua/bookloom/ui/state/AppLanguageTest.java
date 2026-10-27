package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.RecordingAppRestart;
import ua.bookloom.ui.SettingWriter;
import ua.bookloom.ui.i18n.LanguageChoice;
import ua.bookloom.ui.i18n.StoredLanguage;

/** The language choice is stored at once, applies after a restart, and a restart waits for the write. */
class AppLanguageTest {

    private static final Locale UKRAINIAN_COMPUTER = Locale.forLanguageTag("uk-UA");

    private final MemorySettings settings = new MemorySettings();
    private final StoredLanguage stored = new StoredLanguage();
    private final RecordingAppRestart restart = new RecordingAppRestart();
    private final List<Runnable> held = new ArrayList<>();

    private AppLanguage language(final MemorySettings store, final Executor background, final Locale os) {
        return new AppLanguage(
                new SettingWriter(store, background, Runnable::run),
                stored,
                () -> os,
                EnglishMessages.create(),
                restart);
    }

    private AppLanguage language() {
        return language(settings, Runnable::run, Locale.GERMANY);
    }

    private void runNewestFirst() {
        while (!held.isEmpty()) {
            held.remove(held.size() - 1).run();
        }
    }

    private void runHeld() {
        while (!held.isEmpty()) {
            held.remove(0).run();
        }
    }

    @Test
    void choose_uk_storesUk() {
        language().choose(LanguageChoice.UKRAINIAN);

        assertThat(settings.get(AppSetting.LANGUAGE).data()).contains("uk");
    }

    @Test
    void choose_system_removesStored() {
        final AppLanguage language = language();
        language.choose(LanguageChoice.UKRAINIAN);

        language.choose(LanguageChoice.SYSTEM);

        assertThat(settings.get(AppSetting.LANGUAGE).data()).isEmpty();
        assertThat(language.choiceProperty().get()).isEqualTo(LanguageChoice.SYSTEM);
    }

    @Test
    void choiceProperty_seededUk_startsAsUkrainian() {
        stored.seed(Optional.of("uk"));

        assertThat(language().choiceProperty().get()).isEqualTo(LanguageChoice.UKRAINIAN);
    }

    @Test
    void restartNeeded_ukInEnglishWindow_true() {
        final AppLanguage language = language();

        language.choose(LanguageChoice.UKRAINIAN);

        assertThat(language.restartNeededProperty().get()).isTrue();
    }

    @Test
    void restartNeeded_englishInEnglishWindow_false() {
        final AppLanguage language = language();

        language.choose(LanguageChoice.ENGLISH);

        assertThat(language.restartNeededProperty().get()).isFalse();
    }

    @Test
    void restartNeeded_systemOnUkrainianComputerInEnglishWindow_true() {
        final AppLanguage language = language(settings, Runnable::run, UKRAINIAN_COMPUTER);

        language.choose(LanguageChoice.SYSTEM);

        assertThat(language.restartNeededProperty().get()).isTrue();
    }

    @Test
    void restart_afterPendingWrite_callsRestartOnce() {
        final AtomicReference<String> seenAtRestart = new AtomicReference<>("");
        restart.onCall(() -> seenAtRestart.set(
                String.valueOf(settings.get(AppSetting.LANGUAGE).data())));
        final AppLanguage language = language(settings, held::add, Locale.GERMANY);
        language.choose(LanguageChoice.UKRAINIAN);

        language.restart(() -> {});
        runHeld();

        assertThat(restart.calls()).isEqualTo(1);
        assertThat(seenAtRestart.get()).isEqualTo("Optional[uk]");
    }

    @Test
    void restart_notStarted_runsOnFailure() {
        final RecordingAppRestart cannot = new RecordingAppRestart(Result.ok(false));
        final AppLanguage language = new AppLanguage(
                new SettingWriter(settings, Runnable::run, Runnable::run),
                stored,
                () -> Locale.GERMANY,
                EnglishMessages.create(),
                cannot);
        final AtomicBoolean failed = new AtomicBoolean();

        language.restart(() -> failed.set(true));

        assertThat(failed).isTrue();
    }

    @Test
    void restart_attemptFailed_runsOnFailure() {
        final RecordingAppRestart broken = new RecordingAppRestart(
                Result.err(AppError.of(ErrorCode.internal, "BookLoom could not restart", "It could not.")));
        final AppLanguage language = new AppLanguage(
                new SettingWriter(settings, Runnable::run, Runnable::run),
                stored,
                () -> Locale.GERMANY,
                EnglishMessages.create(),
                broken);
        final AtomicBoolean failed = new AtomicBoolean();

        language.restart(() -> failed.set(true));

        assertThat(failed).isTrue();
    }

    @Test
    void choose_storeFails_saveFailed() {
        final AppLanguage language = language(new MemorySettings().failPuts(), Runnable::run, Locale.GERMANY);

        language.choose(LanguageChoice.UKRAINIAN);

        assertThat(language.saveFailedProperty().get()).isTrue();
        assertThat(language.choiceProperty().get()).isEqualTo(LanguageChoice.UKRAINIAN);
    }

    @Test
    void restart_writeFailed_doesNotRestart() {
        final AppLanguage language = language(new MemorySettings().failPuts(), Runnable::run, Locale.GERMANY);
        language.choose(LanguageChoice.UKRAINIAN);

        language.restart(() -> {});

        assertThat(restart.calls()).isZero();
    }

    // Writes may be picked up by different threads in any order; the last choice must still be the stored one.
    @Test
    void choose_twiceWritesRunNewestFirst_lastChoiceStays() {
        final AppLanguage language = language(settings, held::add, Locale.GERMANY);

        language.choose(LanguageChoice.UKRAINIAN);
        language.choose(LanguageChoice.ENGLISH);
        runNewestFirst();

        assertThat(settings.get(AppSetting.LANGUAGE).data()).contains("en");
    }

    @Test
    void restart_twoWritesPending_waitsForBothThenRestartsOnce() {
        final AtomicReference<String> seenAtRestart = new AtomicReference<>("");
        restart.onCall(() -> seenAtRestart.set(
                String.valueOf(settings.get(AppSetting.LANGUAGE).data())));
        final AppLanguage language = language(settings, held::add, Locale.GERMANY);
        language.choose(LanguageChoice.UKRAINIAN);
        language.choose(LanguageChoice.ENGLISH);

        language.restart(() -> {});
        runNewestFirst();

        assertThat(restart.calls()).isEqualTo(1);
        assertThat(seenAtRestart.get()).isEqualTo("Optional[en]");
    }

    @Test
    void restart_clickedTwice_restartCalledOnce() {
        final AppLanguage language = language();
        language.choose(LanguageChoice.UKRAINIAN);

        language.restart(() -> {});
        language.restart(() -> {});

        assertThat(restart.calls()).isEqualTo(1);
        assertThat(language.restartingProperty().get()).isTrue();
    }

    @Test
    void restart_notStarted_allowsAnotherTry() {
        final RecordingAppRestart cannot = new RecordingAppRestart(Result.ok(false));
        final AppLanguage language = new AppLanguage(
                new SettingWriter(settings, Runnable::run, Runnable::run),
                stored,
                () -> Locale.GERMANY,
                EnglishMessages.create(),
                cannot);

        language.restart(() -> {});
        language.restart(() -> {});

        assertThat(cannot.calls()).isEqualTo(2);
        assertThat(language.restartingProperty().get()).isFalse();
    }

    @Test
    void restart_appRestartThrows_runsOnFailureAndLaterClickTriesAgain() {
        final RecordingAppRestart throwing = new RecordingAppRestart().onCall(() -> {
            throw new IllegalStateException("boom");
        });
        final AppLanguage language = new AppLanguage(
                new SettingWriter(settings, Runnable::run, Runnable::run),
                stored,
                () -> Locale.GERMANY,
                EnglishMessages.create(),
                throwing);
        final AtomicBoolean failed = new AtomicBoolean();

        language.restart(() -> failed.set(true));
        language.restart(() -> {});

        assertThat(failed).isTrue();
        assertThat(throwing.calls()).isEqualTo(2);
    }
}
