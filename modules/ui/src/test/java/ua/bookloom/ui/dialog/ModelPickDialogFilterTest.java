package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** The model-name filter above the radio list of the card that asks which found model to use (design D23). */
class ModelPickDialogFilterTest extends ShellTestBase {

    private static final String PLACEHOLDER = "Filter models by name";

    private void ask() {
        onFx(() -> injector.getInstance(ModelPickDialog.class)
                .ask("ollama", List.of("mistral-small3.2:24b", "gemma4:e4b", "qwen3:8b"), model -> {}, () -> {}));
    }

    private Optional<TextField> filterField() {
        return ThemeTestSupport.onFx(() -> required("model-pick-card").lookupAll(".text-field").stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> PLACEHOLDER.equals(field.getPromptText()))
                .findFirst());
    }

    private List<String> shownOptions() {
        return ThemeTestSupport.onFx(() -> required("model-pick-card").lookupAll(".radio-button").stream()
                .filter(node -> node.isVisible() && node.isManaged())
                .map(RadioButton.class::cast)
                .map(RadioButton::getText)
                .toList());
    }

    @Test
    void ask_shown_hasAFilterFieldWithThePlaceholder() {
        ask();

        assertThat(filterField())
                .as("a text field with the placeholder '%s' on the model-pick card", PLACEHOLDER)
                .isPresent();
    }

    @Test
    void filterTyped_mistral_onlyTheMistralModelRemains() {
        ask();
        assertThat(filterField())
                .as("a text field with the placeholder '%s' on the model-pick card", PLACEHOLDER)
                .isPresent();

        onFx(() -> filterField().orElseThrow().setText("mistral"));

        assertThat(shownOptions()).containsExactly("mistral-small3.2:24b");
    }
}
