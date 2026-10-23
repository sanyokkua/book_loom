package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;

/** The model note under the Providers tab's model control, read from the real scene. */
class SettingsModelNoteScreenTest extends SettingsScreenTestBase {

    private Label noteLabel() {
        return (Label) required("settings-model-note");
    }

    // IF the note took room with no model chosen, THEN an empty line would sit under the control.
    @Test
    void noteLabel_ollamaWithNoModel_isHiddenAndNotManaged() throws TimeoutException {
        openSettingsSettled();

        assertThat(noteLabel().isVisible()).isFalse();
        assertThat(noteLabel().isManaged()).isFalse();
    }

    // IF the note did not follow what is typed, THEN the person would choose without the advice the owner asked for.
    @Test
    void noteLabel_recommendedModelTyped_showsTheRecommendation() throws TimeoutException {
        openSettingsSettled();

        typeIntoModelEntry("gemma4:26b-mlx");

        assertThat(noteLabel().isVisible()).isTrue();
        assertThat(noteLabel().isManaged()).isTrue();
        assertThat(noteLabel().getText()).contains("Recommended").contains("26B");
    }

    @Test
    void noteLabel_minimumModelPicked_showsTheMinimumNote() throws TimeoutException {
        catalog.respondWith("gemma4:e4b-mlx", "gemma3:12b");
        openSettings();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        ThemeTestSupport.onFx(() -> {
            modelCombo().getSelectionModel().select("gemma4:e4b-mlx");
            return null;
        });

        assertThat(noteLabel().getText()).contains("E4B").contains("minimum");
    }

    @Test
    void noteLabel_tooSmallModelTyped_warns() throws TimeoutException {
        openSettingsSettled();

        typeIntoModelEntry("gemma4:e2b-mlx");

        assertThat(noteLabel().getText()).contains("Not recommended").contains("E2B");
    }

    @Test
    void noteLabel_unknownModelTyped_staysHidden() throws TimeoutException {
        openSettingsSettled();

        typeIntoModelEntry("gemma3:12b");

        assertThat(noteLabel().isVisible()).isFalse();
        assertThat(noteLabel().isManaged()).isFalse();
    }

    // IF the note stayed after the entry was cleared by a provider change, THEN it would advise a model no longer
    // chosen.
    @Test
    void noteLabel_providerChangedToLmStudio_showsTheServerNoteAndDropsTheModelNote() throws TimeoutException {
        openSettingsSettled();
        typeIntoModelEntry("gemma4:26b-mlx");

        onFx(() -> viewModel().editProvider("lmstudio"));

        assertThat(noteLabel().isVisible()).isTrue();
        assertThat(noteLabel().getText())
                .contains("LM Studio")
                .contains("does not stream")
                .doesNotContain("Recommended");
    }

    @Test
    void noteLabel_ukrainian_isWrittenInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openSettingsSettled();

        typeIntoModelEntry("gemma4:26b-mlx");

        assertThat(noteLabel().getText()).contains("Рекомендовано").contains("26B");
    }
}
