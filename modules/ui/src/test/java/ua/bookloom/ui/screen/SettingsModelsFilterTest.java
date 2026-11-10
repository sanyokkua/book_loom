package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ModelEntry;

/**
 * The model-name filter on the pickers Settings holds: the Models chooser and the Providers model list narrow by the
 * typed name, and each is headed by a field that says so (design D23).
 */
class SettingsModelsFilterTest extends SettingsModelsScreenTestBase {

    private static final String E4B = "gemma4:e4b";
    private static final String BIG = "gemma4:26b";
    private static final String QWEN = "qwen3.5:9b";
    private static final String PLACEHOLDER = "Filter models by name";

    @BeforeEach
    void listThreeModels() {
        catalog.respondFor("ollama", E4B, BIG, QWEN);
        catalog.respondFor("lmstudio");
    }

    @Test
    void typeE4b_threeModels_onlyE4bListed() throws TimeoutException {
        openModels();

        onFx(() -> modelsCombo().getEditor().setText("e4b"));
        awaitFx(() -> modelsCombo().getItems().size() == 1);

        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getItems().stream()
                        .map(ModelEntry::modelId)
                        .toList()))
                .containsExactly(E4B);
    }

    @Test
    void modelsTab_threeModels_hasAFilterFieldWithThePlaceholder() throws TimeoutException {
        openModels();

        assertThat(filterFields())
                .as("a text field with the placeholder '%s' on the Models tab", PLACEHOLDER)
                .isNotEmpty();
    }

    @Test
    void providersList_threeModels_hasAFilterFieldWithThePlaceholder() throws TimeoutException {
        openSettingsSettled();
        openFold();

        assertThat(filterFields())
                .as("a text field with the placeholder '%s' on the Providers tab", PLACEHOLDER)
                .isNotEmpty();
    }

    @Test
    void providersList_filterTyped_onlyMatchingModelsRemain() throws TimeoutException {
        openSettingsSettled();
        openFold();
        awaitFx(() -> modelCombo().getItems().size() == 3);
        final Optional<TextField> filter = filterFields().stream()
                .filter(field -> "settings-model-filter".equals(field.getId()))
                .findFirst();
        assertThat(filter)
                .as("a text field with the placeholder '%s' on the Providers tab", PLACEHOLDER)
                .isPresent();

        onFx(() -> filter.orElseThrow().setText("e4b"));

        assertThat(ThemeTestSupport.onFx(() -> List.copyOf(modelCombo().getItems())))
                .containsExactly(E4B);
    }

    @Test
    void modelsFilter_escape_clearsTheTextAndRestoresTheList() throws TimeoutException {
        openModels();
        onFx(() -> modelsField("models-filter").setText("qwen"));
        awaitFx(() -> modelsCombo().getItems().size() == 1);

        press(modelsField("models-filter"), KeyCode.ESCAPE);

        awaitFx(() -> modelsCombo().getItems().size() == 3);
        assertThat(ThemeTestSupport.onFx(() -> modelsField("models-filter").getText()))
                .isEmpty();
    }

    @Test
    void modelsFilter_down_movesTheFocusIntoTheChooser() throws TimeoutException {
        openModels();
        onFx(() -> modelsField("models-filter").requestFocus());

        press(modelsField("models-filter"), KeyCode.DOWN);

        assertThat(ThemeTestSupport.onFx(() ->
                        modelsCombo().getEditor().isFocused() || modelsCombo().isShowing()))
                .isTrue();
    }

    @Test
    void modelsFilter_chosenModelHidden_choiceKept() throws TimeoutException {
        openModels();
        chooseModel(E4B);

        onFx(() -> modelsField("models-filter").setText("qwen"));
        awaitFx(() -> modelsCombo().getItems().size() == 1);

        assertThat(modelForm().selected().get().modelId()).isEqualTo(E4B);
        assertThat(ThemeTestSupport.onFx(() -> modelsCombo().getValue().modelId()))
                .isEqualTo(E4B);
    }

    @Test
    void modelsFilter_llama_showsTheNoneSentence() throws TimeoutException {
        openModels();

        onFx(() -> modelsField("models-filter").setText("llama"));

        assertThat(visible("models-filter-none")).isTrue();
        assertThat(shown("models-filter-none")).isEqualTo("No model name contains \"llama\".");
    }

    @Test
    void providersFilter_typed_leavesTheDraftModelAlone() throws TimeoutException {
        openSettingsSettled();
        openFold();
        awaitFx(() -> modelCombo().getItems().size() == 3);
        final String before =
                ThemeTestSupport.onFx(() -> viewModel().draftModel().get());

        onFx(() -> modelsField("settings-model-filter").setText("qwen"));

        assertThat(ThemeTestSupport.onFx(() -> viewModel().draftModel().get())).isEqualTo(before);
        assertThat(ThemeTestSupport.onFx(() -> List.copyOf(modelCombo().getItems())))
                .containsExactly(QWEN);
    }

    private static void press(final TextField target, final KeyCode code) {
        ThemeTestSupport.onFx(() -> {
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
            return null;
        });
    }

    private List<TextField> filterFields() {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> PLACEHOLDER.equals(field.getPromptText()))
                .toList());
    }
}
