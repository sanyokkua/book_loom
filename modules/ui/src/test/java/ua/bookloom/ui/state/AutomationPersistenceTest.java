package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.UiTestInjector;

/** A choice made in one window is what the next window, over the same store, shows. */
class AutomationPersistenceTest extends FxTestBase {

    private static final long WAIT_SECONDS = 10;

    private final MemorySettings store = new MemorySettings();

    @Override
    public void start(final Stage stage) {
        // The toolkit is all this needs.
    }

    private Injector window() {
        return UiTestInjector.builder(Locale.ENGLISH).settings(store).build();
    }

    @Test
    void newInjector_reopenOffStored_switchShowsOff() throws Exception {
        final AutomationSettings first = window().getInstance(AutomationSettings.class);
        onFx(() -> {
            first.chooseReopenLastProject(false);
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> store.stored(AppSetting.REOPEN_LAST_PROJECT).isPresent());

        final AutomationSettings second = window().getInstance(AutomationSettings.class);
        second.load();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> second.loadedProperty().get()));

        assertThat(onFx(() -> second.reopenLastProjectProperty().get())).isFalse();
    }
}
