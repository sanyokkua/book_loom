package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** The model-name filter above the radio list of the card that asks which found model to use (design D23). */
class ModelPickDialogFilterTest extends ShellTestBase {

    private static final String PLACEHOLDER = "Filter models by name";

    private void ask() {
        onFx(() -> injector.getInstance(ModelPickDialog.class)
                .ask(
                        ModelPickProviders.ollama(),
                        List.of("mistral-small3.2:24b", "gemma4:e4b", "qwen3:8b"),
                        model -> {},
                        () -> {}));
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

    private void press(final javafx.scene.Node target, final KeyCode code) {
        onFx(() ->
                Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false)));
    }

    private boolean useDisabled() {
        return ThemeTestSupport.onFx(() -> required("model-pick-use").isDisable());
    }

    @Test
    void escape_withFilterText_clearsItKeepsTheCardOpenAndRestoresTheList() {
        ask();
        onFx(() -> filterField().orElseThrow().setText("qwen"));

        press(filterField().orElseThrow(), KeyCode.ESCAPE);

        assertThat(ThemeTestSupport.onFx(() -> filterField().orElseThrow().getText()))
                .isEmpty();
        assertThat(shownOptions()).hasSize(3);
        assertThat(ThemeTestSupport.onFx(() -> required("model-pick-card").getScene()))
                .isNotNull();
    }

    @Test
    void down_inTheFilter_focusesTheFirstVisibleRadio() {
        ask();
        onFx(() -> filterField().orElseThrow().setText("qwen"));

        press(filterField().orElseThrow(), KeyCode.DOWN);

        assertThat(ThemeTestSupport.onFx(() -> {
                    final Scene scene = required("model-pick-card").getScene();
                    return ((RadioButton) scene.getFocusOwner()).getText();
                }))
                .isEqualTo("qwen3:8b");
    }

    @Test
    void filterTyped_pickHidden_useStaysEnabled() {
        ask();
        onFx(() -> ((RadioButton) required("model-pick-card").lookupAll(".radio-button").stream()
                        .filter(node -> "gemma4:e4b".equals(((RadioButton) node).getText()))
                        .findFirst()
                        .orElseThrow())
                .setSelected(true));
        assertThat(useDisabled()).isFalse();

        onFx(() -> filterField().orElseThrow().setText("qwen"));

        assertThat(shownOptions()).containsExactly("qwen3:8b");
        assertThat(useDisabled()).isFalse();
    }

    @Test
    void filterTyped_llama_showsTheNoneSentence() {
        ask();

        onFx(() -> filterField().orElseThrow().setText("llama"));

        final javafx.scene.control.Label none = (javafx.scene.control.Label) required("model-pick-filter-none");

        assertThat(ThemeTestSupport.onFx(none::getText)).isEqualTo("No model name contains \"llama\".");
    }
}
