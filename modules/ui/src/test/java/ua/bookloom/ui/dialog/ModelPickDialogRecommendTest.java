package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.FixedMemory;

/** The memory-based model line between the pick card's words and its filter. */
class ModelPickDialogRecommendTest extends ShellTestBase {

    private final FixedMemory memory = new FixedMemory(32);

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale).memory(memory).build();
    }

    private void ask() {
        onFx(() -> injector.getInstance(ModelPickDialog.class)
                .ask("ollama", List.of("gemma4:e4b", "qwen3:8b"), model -> {}, () -> {}));
    }

    @Test
    void ask_32gb_showsTheLineAboveTheFilter() {
        ask();

        assertThat(((Label) required("model-pick-recommend")).getText())
                .isEqualTo("This computer has 32 GB: gemma4:26b-a4b is recommended."
                        + " Pull it with `ollama pull gemma4:26b-a4b`.");
        final var children = ((VBox) required("model-pick-recommend").getParent()).getChildren();
        assertThat(children.indexOf(required("model-pick-recommend")))
                .isLessThan(children.indexOf(required("model-pick-filter").getParent()));
    }

    @Test
    void ask_inUkrainian_saysTheLineInUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));
        ask();

        assertThat(((Label) required("model-pick-recommend")).getText())
                .isEqualTo("На цьому комп’ютері 32 ГБ пам’яті: рекомендовано gemma4:26b-a4b."
                        + " Завантажте командою `ollama pull gemma4:26b-a4b`.");
    }
}
