package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.TextField;
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
        final Optional<TextField> filter = filterFields().stream().findFirst();
        assertThat(filter)
                .as("a text field with the placeholder '%s' on the Providers tab", PLACEHOLDER)
                .isPresent();

        onFx(() -> filter.orElseThrow().setText("e4b"));

        assertThat(ThemeTestSupport.onFx(() -> List.copyOf(modelCombo().getItems())))
                .containsExactly(E4B);
    }

    private List<TextField> filterFields() {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> PLACEHOLDER.equals(field.getPromptText()))
                .toList());
    }
}
