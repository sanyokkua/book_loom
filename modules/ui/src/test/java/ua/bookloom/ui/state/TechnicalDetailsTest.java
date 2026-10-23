package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SettingsRepository;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.MemorySettings;

/**
 * The one "Show technical details" switch: remembered through the settings store, followed by the progress dialog's
 * opening view, and readable by any fold. The Settings Providers "Connection details" fold is not built yet, so a
 * recording fake stands for a registered reader here; the real fold test lands in 7.1a.
 */
class TechnicalDetailsTest {

    private static TechnicalDetails details(final SettingsRepository store) {
        return new TechnicalDetails(store, Runnable::run, Runnable::run);
    }

    @Test
    void newInstance_nothingStored_isOffAndProgressOpensSimple() {
        final TechnicalDetails details = details(new MemorySettings());
        final ProgressDialogDefaults defaults = new ProgressDialogDefaults(details);

        assertThat(details.property().get()).isFalse();
        assertThat(defaults.initialMode().get()).isEqualTo(ProgressDialogModel.Mode.SIMPLE);
    }

    @Test
    void switchedOn_progressDefaultIsDetailedAndRegisteredFoldOpens() {
        final TechnicalDetails details = details(new MemorySettings());
        final ProgressDialogDefaults defaults = new ProgressDialogDefaults(details);
        final SimpleBooleanProperty foldOpen = new SimpleBooleanProperty(false);
        foldOpen.bind(details.property());

        details.property().set(true);

        assertThat(defaults.initialMode().get()).isEqualTo(ProgressDialogModel.Mode.DETAILED);
        assertThat(foldOpen.get()).isTrue();
    }

    @Test
    void switchedOff_afterOn_progressDefaultIsSimpleAndFoldCloses() {
        final TechnicalDetails details = details(new MemorySettings());
        final ProgressDialogDefaults defaults = new ProgressDialogDefaults(details);
        final SimpleBooleanProperty foldOpen = new SimpleBooleanProperty(false);
        foldOpen.bind(details.property());
        details.property().set(true);

        details.property().set(false);

        assertThat(defaults.initialMode().get()).isEqualTo(ProgressDialogModel.Mode.SIMPLE);
        assertThat(foldOpen.get()).isFalse();
    }

    @Test
    void toggle_persists_aNewInstanceReadsItBack() {
        final SettingsRepository store = new MemorySettings();
        details(store).property().set(true);

        assertThat(details(store).property().get()).isTrue();
        assertThat(store.all().data()).containsEntry("ui.technicalDetails", "true");
    }

    @Test
    void newInstance_storeFails_isOffAndStaysUsable() {
        final TechnicalDetails details = details(new FailingSettings());

        assertThat(details.property().get()).isFalse();
        details.property().set(true);
        assertThat(details.property().get()).isTrue();
    }

    @Test
    void choiceBeforeStoredValueArrives_wins() {
        final SettingsRepository store = new MemorySettings();
        final java.util.List<Runnable> held = new java.util.ArrayList<>();
        final TechnicalDetails details = new TechnicalDetails(store, held::add, Runnable::run);
        details.property().set(true);

        held.forEach(Runnable::run);

        assertThat(details.property().get()).isTrue();
    }

    private static final class FailingSettings implements SettingsRepository {

        private static final AppError ERROR = AppError.of(ErrorCode.internal, "t", "m", null, null);

        @Override
        public <T> Result<Optional<T>> get(final AppSetting<T> setting) {
            return Result.err(ERROR);
        }

        @Override
        public <T> Result<T> put(final AppSetting<T> setting, final T value) {
            return Result.err(ERROR);
        }

        @Override
        public Result<Boolean> remove(final AppSetting<?> setting) {
            return Result.err(ERROR);
        }

        @Override
        public Result<Map<String, String>> all() {
            return Result.err(ERROR);
        }
    }
}
