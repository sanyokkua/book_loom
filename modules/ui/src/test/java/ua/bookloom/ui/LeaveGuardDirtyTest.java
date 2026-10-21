package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.dialog.LeaveDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.DirtySource;

/**
 * Leaving a screen whose form holds unsaved changes asks first (task 5.8, "LeaveGuard gains a dirty-form check"): a
 * clean form lets the person go at once; a dirty one asks Keep editing, Discard or Save, and only Discard and Save
 * leave, Save after the changes are saved.
 */
class LeaveGuardDirtyTest extends ShellTestBase {

    /** A form on Settings that records what the guard asked of it. */
    private static final class RecordingForm implements DirtySource {

        private final List<String> calls = new ArrayList<>();
        private boolean dirty;

        @Override
        public ViewNames home() {
            return ViewNames.SETTINGS;
        }

        @Override
        public boolean isDirty() {
            return dirty;
        }

        @Override
        public void discard() {
            calls.add("discard");
        }

        @Override
        public void save(final Runnable then) {
            calls.add("save");
            then.run();
        }
    }

    private final RecordingForm form = new RecordingForm();
    private final List<String> proceeded = new ArrayList<>();

    private boolean leaveSettings() {
        final LeaveGuard guard = new LeaveGuard(
                injector.getInstance(ActivityTracker.class),
                injector.getInstance(LeaveDialog.class),
                injector.getInstance(Messages.class),
                Set.of(form));
        final boolean admitted =
                ThemeTestSupport.onFx(() -> guard.admits(ViewNames.SETTINGS, () -> proceeded.add("left")));
        WaitForAsyncUtils.waitForFxEvents();
        return admitted;
    }

    // IF a clean form asked, THEN every move away from Settings would cost a click for nothing.
    @Test
    void admits_formClean_letsThePersonGoAtOnce() {
        assertThat(leaveSettings()).isTrue();
        assertThat(scene.getRoot().lookup("#unsaved-card")).isNull();
    }

    // IF a dirty form let the person go, THEN the changes would be lost without a word.
    @Test
    void admits_formDirty_asksAndDoesNotLeaveYet() {
        form.dirty = true;

        assertThat(leaveSettings()).isFalse();

        assertThat(scene.getRoot().lookup("#unsaved-card")).isNotNull();
        assertThat(((Button) required("unsaved-keep")).isDefaultButton()).isTrue();
        assertThat(proceeded).isEmpty();
    }

    // IF an answer did something else than it says, THEN a person could lose the changes they meant to keep.
    @ParameterizedTest
    @CsvSource({"unsaved-keep, -, -", "unsaved-discard, discard, left", "unsaved-save, save, left"})
    void answer_eachChoice_doesWhatItSays(final String button, final String formCall, final String left) {
        form.dirty = true;
        leaveSettings();

        onFx(() -> ((Button) required(button)).fire());

        assertThat(String.join(" ", form.calls)).isEqualTo("-".equals(formCall) ? "" : formCall);
        assertThat(String.join(" ", proceeded)).isEqualTo("-".equals(left) ? "" : left);
        assertThat(scene.getRoot().lookup("#unsaved-card")).isNull();
    }
}
