package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.ViewNames;

/** The Providers and Models forms as the leave guard sees them: one source on the Settings screen. */
@SuppressWarnings("NullAway.Init")
class SettingsDirtySourceTest extends ModelFormTestBase {

    private ProviderForm providers;

    private SettingsDirtySource source() throws Exception {
        buildForm();
        providers = form(viewModel);
        select(GEMMA);
        return onFx(() -> new SettingsDirtySource(providers, form));
    }

    private void dirtyProvider() {
        onFx(() -> {
            providers.edit(draft -> draft.withName("Mine"));
            return null;
        });
    }

    private boolean save(final SettingsDirtySource source) throws Exception {
        final List<String> ran = new ArrayList<>();
        onFx(() -> {
            source.save(() -> ran.add("then"));
            return null;
        });
        await(() -> !providers.saving().get() && !form.saving().get());
        return !ran.isEmpty();
    }

    // IF the guard did not see the forms, THEN leaving Settings would lose their typing unasked.
    @Test
    void isDirty_untouchedForms_isFalseOnTheSettingsScreen() throws Exception {
        final SettingsDirtySource source = source();

        assertThat(source.home()).isEqualTo(ViewNames.SETTINGS);
        assertThat(onFx(source::isDirty)).isFalse();
    }

    // IF the question named a provider that has no name yet, THEN it would read "Save the changes to ?".
    @Test
    void subject_providerWithAName_isThatNameAndBlankIsNull() throws Exception {
        final SettingsDirtySource source = source();

        assertThat(onFx(source::subject)).isEqualTo(UnsavedSubject.provider("Ollama"));
        onFx(() -> {
            providers.edit(draft -> draft.withName(" "));
            return null;
        });
        assertThat(onFx(source::subject)).isNull();
        assertThat(onFx(source::isDirty)).isTrue();
    }

    // IF a changed model setting were not seen, THEN leaving Settings would drop it without a word.
    @Test
    void isDirty_modelOnly_true() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));

        assertThat(onFx(source::isDirty)).isTrue();
    }

    @Test
    void subject_modelDirty_modelKeys() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));

        assertThat(onFx(source::subject)).isEqualTo(UnsavedSubject.model("gemma4:e4b · Ollama"));
    }

    @Test
    void subject_providerDirty_providerKeys() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        dirtyProvider();

        assertThat(onFx(source::subject)).isEqualTo(UnsavedSubject.provider("Mine"));
    }

    // IF Save kept only one form's typing, THEN the other would be lost although the person said Save.
    @Test
    void save_bothDirty_savesBothThenRuns() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        dirtyProvider();

        final boolean ran = save(source);

        assertThat(ran).isTrue();
        assertThat(configs.find("ollama").orElseThrow().displayName()).isEqualTo("Mine");
        assertThat(stored(GEMMA).temperatureChoice()).isEqualTo(TemperatureChoice.OMIT);
        assertThat(onFx(source::isDirty)).isFalse();
    }

    // IF a failed model save still let the person leave, THEN the typing would be lost after they asked to keep it.
    @Test
    void save_modelFails_thenNotRun() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        modelSettings.failUpdate();

        assertThat(save(source)).isFalse();
        assertThat(onFx(source::isDirty)).isTrue();
    }

    @Test
    void save_providerFails_modelNotWrittenAndThenNotRun() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        dirtyProvider();
        configs.failSaves();

        assertThat(save(source)).isFalse();
        assertThat(modelSettings.updateCount()).isZero();
    }

    @Test
    void discard_bothDirty_dropsBoth() throws Exception {
        final SettingsDirtySource source = source();
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        dirtyProvider();

        onFx(() -> {
            source.discard();
            return null;
        });

        assertThat(onFx(source::isDirty)).isFalse();
    }
}
