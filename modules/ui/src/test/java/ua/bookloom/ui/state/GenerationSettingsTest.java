package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.api.settings.GenerationOverrides;
import ua.bookloom.api.settings.ReasoningSetting;
import ua.bookloom.api.settings.SamplingSetting;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedGenerationOverrides;

/** Thinking and sampling are read once when the tab opens, stored the moment they are chosen, and overridden by name. */
class GenerationSettingsTest {

    private final MemorySettings store = new MemorySettings();

    private GenerationSettings direct(final MemorySettings settings, final GenerationOverrides overrides) {
        return new GenerationSettings(settings, overrides, Runnable::run, Runnable::run);
    }

    @Test
    void load_lowStored_low() {
        store.put(AppSetting.REASONING, ReasoningSetting.LOW);
        store.put(AppSetting.SAMPLING, SamplingSetting.TUNED);
        final GenerationSettings generation = direct(store, ScriptedGenerationOverrides.none());

        generation.load();

        assertThat(generation.reasoningProperty().get()).isEqualTo(ReasoningSetting.LOW);
        assertThat(generation.samplingProperty().get()).isEqualTo(SamplingSetting.TUNED);
        assertThat(generation.loadedProperty().get()).isTrue();
    }

    @Test
    void load_nothingStored_offAndProgramDefaults() {
        final GenerationSettings generation = direct(store, ScriptedGenerationOverrides.none());

        generation.load();

        assertThat(generation.reasoningProperty().get()).isEqualTo(ReasoningSetting.OFF);
        assertThat(generation.samplingProperty().get()).isEqualTo(SamplingSetting.PROGRAM_DEFAULTS);
    }

    @Test
    void choose_low_storesLow() {
        final GenerationSettings generation = direct(store, ScriptedGenerationOverrides.none());

        generation.choose(ReasoningSetting.LOW);

        assertThat(store.get(AppSetting.REASONING).data()).contains(ReasoningSetting.LOW);
    }

    @Test
    void choose_tuned_storesTuned() {
        final GenerationSettings generation = direct(store, ScriptedGenerationOverrides.none());

        generation.choose(SamplingSetting.TUNED);

        assertThat(store.get(AppSetting.SAMPLING).data()).contains(SamplingSetting.TUNED);
    }

    // A choice made before the stored values arrive must not be overwritten by them.
    @Test
    void choose_beforeLoad_personWins() {
        store.put(AppSetting.REASONING, ReasoningSetting.ON);
        final List<Runnable> held = new ArrayList<>();
        final GenerationSettings generation =
                new GenerationSettings(store, ScriptedGenerationOverrides.none(), held::add, Runnable::run);
        generation.load();

        generation.choose(ReasoningSetting.LOW);
        held.forEach(Runnable::run);

        assertThat(generation.reasoningProperty().get()).isEqualTo(ReasoningSetting.LOW);
    }

    @Test
    void load_envOverride_namesSource() {
        final GenerationSettings generation =
                direct(store, ScriptedGenerationOverrides.reasoningFrom("BOOKLOOM_REASONING"));

        generation.load();

        assertThat(generation.reasoningOverrideProperty().get()).isEqualTo("BOOKLOOM_REASONING");
        assertThat(generation.samplingOverrideProperty().get()).isNull();
    }

    @Test
    void choose_storeFails_saveFailed() {
        final GenerationSettings generation =
                direct(new MemorySettings().failPuts(), ScriptedGenerationOverrides.none());

        generation.choose(SamplingSetting.TUNED);

        assertThat(generation.saveFailedProperty().get()).isTrue();
        assertThat(generation.samplingProperty().get()).isEqualTo(SamplingSetting.TUNED);
    }

    @Test
    void load_storeUnreadable_keepsDefaultsAndStillLoaded() {
        final GenerationSettings generation =
                direct(new MemorySettings().failGets(), ScriptedGenerationOverrides.none());

        generation.load();

        assertThat(generation.reasoningProperty().get()).isEqualTo(ReasoningSetting.OFF);
        assertThat(generation.loadedProperty().get()).isTrue();
    }

    // Writes may be picked up by different threads in any order; the last choice must still be the stored one.
    @Test
    void choose_twiceWritesRunNewestFirst_lastChoiceStays() {
        final List<Runnable> held = new ArrayList<>();
        final GenerationSettings generation =
                new GenerationSettings(store, ScriptedGenerationOverrides.none(), held::add, Runnable::run);

        generation.choose(ReasoningSetting.LOW);
        generation.choose(ReasoningSetting.ON);
        runNewestFirst(held);

        assertThat(store.get(AppSetting.REASONING).data()).contains(ReasoningSetting.ON);
    }

    private static void runNewestFirst(final List<Runnable> held) {
        while (!held.isEmpty()) {
            held.remove(held.size() - 1).run();
        }
    }
}
