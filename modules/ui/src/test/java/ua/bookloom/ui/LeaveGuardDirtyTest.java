package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.dialog.LeaveDialog;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.DirtySource;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.ProviderForm;
import ua.bookloom.ui.state.SettingsDirtySource;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.UnsavedSubject;

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
        private boolean saveSucceeds = true;
        private @Nullable UnsavedSubject subject;

        @Override
        public ViewNames home() {
            return ViewNames.SETTINGS;
        }

        @Override
        public boolean isDirty() {
            return dirty;
        }

        @Override
        public @Nullable UnsavedSubject subject() {
            return subject;
        }

        @Override
        public void discard() {
            calls.add("discard");
            dirty = false;
        }

        @Override
        public void save(final Runnable then) {
            calls.add("save");
            if (saveSucceeds) {
                dirty = false;
                then.run();
            }
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

    // IF the question did not name the provider, THEN a person could not tell which form the changes belong to.
    @Test
    void admits_formWithSubject_namesTheProviderInTheQuestion() {
        form.dirty = true;
        form.subject = UnsavedSubject.provider("Office LM");

        leaveSettings();

        assertThat(((Label) scene.getRoot().lookup("#unsaved-card .dialog-title")).getText())
                .isEqualTo("Save the changes to Office LM?");
    }

    // IF Discard skipped the running-work question, THEN leaving Settings would leave an inference test behind unasked.
    @Test
    void answer_discardWhileInferenceTestRuns_stillAsksTheWorkQuestion() {
        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.PROVIDER_INFERENCE_TEST, () -> {}));
        form.dirty = true;
        leaveSettings();

        onFx(() -> ((Button) required("unsaved-discard")).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(form.calls).containsExactly("discard");
        assertThat(proceeded).isEmpty();
        assertThat(scene.getRoot().lookup("#leave-card")).isNotNull();
    }

    // IF a failed save still left, THEN the changes would be lost after the person asked to keep them.
    @Test
    void answer_saveFails_doesNotLeaveAndTheFormStaysDirty() {
        form.dirty = true;
        form.saveSucceeds = false;
        leaveSettings();

        onFx(() -> ((Button) required("unsaved-save")).fire());

        assertThat(form.calls).containsExactly("save");
        assertThat(proceeded).isEmpty();
        assertThat(form.dirty).isTrue();
    }

    // IF the question did not name the model and its provider, THEN a person could not tell which settings they
    // changed.
    @Test
    void admits_modelDirty_asksNamingModel() {
        form.dirty = true;
        form.subject = UnsavedSubject.model("gemma4:e4b \u00b7 Ollama");

        leaveSettings();

        assertThat(((Label) scene.getRoot().lookup("#unsaved-card .dialog-title")).getText())
                .isEqualTo("Save the settings of gemma4:e4b \u00b7 Ollama?");
    }

    // IF Discard left the real Providers form dirty with no provider to return to, THEN the guard would ask forever.
    @Test
    void answer_discardOnTheRealFormWithNoProviders_leavesAndAsksOnlyOnce() {
        final ProviderForm real = injector.getInstance(ProviderForm.class);
        final SettingsViewModel viewModel = injector.getInstance(SettingsViewModel.class);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {
            viewModel.startAdding();
            real.edit(draft -> draft.withName("Office LM"));
        });
        final LeaveGuard guard = new LeaveGuard(
                injector.getInstance(ActivityTracker.class),
                injector.getInstance(LeaveDialog.class),
                injector.getInstance(Messages.class),
                Set.of(new SettingsDirtySource(real, injector.getInstance(ModelForm.class))));
        onFx(() -> {
            guard.admits(ViewNames.SETTINGS, () -> proceeded.add("left"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        onFx(() -> ((Button) required("unsaved-discard")).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(proceeded).containsExactly("left");
        assertThat(scene.getRoot().lookup("#unsaved-card")).isNull();
    }

    @Override
    protected Injector createInjector(final Locale locale) {
        final FakeProviderConfigs none = new FakeProviderConfigs();
        none.delete("ollama");
        none.delete("lmstudio");
        return UiTestInjector.builder(locale).configs(none).build();
    }
}
