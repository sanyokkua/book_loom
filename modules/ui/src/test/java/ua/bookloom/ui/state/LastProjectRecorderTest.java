package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.UiTestInjector;

/** Opening a project, from any screen, stores its id as the last project, through the real settings round trip. */
class LastProjectRecorderTest extends ShellTestBase {

    private final MemorySettings settings = new MemorySettings();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale).settings(settings).build();
    }

    // IF only the Projects screen wrote the id, THEN a book opened through Import would not come back at the next
    // launch; the recorder follows the open project itself.
    @Test
    void openProject_anyWay_isStoredAsTheLastProject() throws Exception {
        assertThat(lastProject()).isEmpty();

        onFx(() -> OpenBookForTest.open(injector.getInstance(CurrentProject.class), "en", "uk"));

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lastProject().isPresent());
        assertThat(lastProject()).hasValue("p1");
    }

    private Optional<String> lastProject() {
        return Objects.requireNonNull(settings.get(AppSetting.LAST_PROJECT).data());
    }
}
