package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.FixedMemory;

/** The memory-based model line under the Model entry on Settings. */
class ModelFieldRecommendTest extends SettingsScreenTestBase {

    private final FixedMemory memory = new FixedMemory(36);

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.memory(memory);
    }

    @Test
    void modelField_36gb_namesTheTierAndTheOptIn() {
        openSettings();

        assertThat(((Label) required("settings-model-recommend")).getText())
                .isEqualTo("This computer has 36 GB: gemma4:26b-a4b is recommended."
                        + " Pull it with `ollama pull gemma4:26b-a4b`.");
        assertThat(((Label) required("settings-model-optin")).getText()).contains("gemma4:31b");
    }

    @Test
    void modelField_line_sitsAfterTheEntryHint() {
        openSettings();

        final var box = required("settings-model-recommend").getParent();
        final var children = box.getChildrenUnmodifiable();
        assertThat(children.indexOf(required("settings-model-recommend")))
                .isGreaterThan(children.indexOf(required("settings-model")));
    }
}
