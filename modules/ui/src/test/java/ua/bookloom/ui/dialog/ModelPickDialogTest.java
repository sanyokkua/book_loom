package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.ShellTestBase;

/** The card that asks which of several found models to use: its words, its radio list and its two answers. */
class ModelPickDialogTest extends ShellTestBase {

    private final List<String> answers = new ArrayList<>();

    private void ask() {
        onFx(() -> injector.getInstance(ModelPickDialog.class)
                .ask(
                        "ollama",
                        List.of("gemma4:e4b", "qwen3:8b"),
                        model -> answers.add("use " + model),
                        () -> answers.add("notNow")));
    }

    private List<RadioButton> options() {
        return required("model-pick-card").lookupAll(".radio-button").stream()
                .map(RadioButton.class::cast)
                .toList();
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#model-pick-card");
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    @Test
    void ask_shown_listsEveryModelWithNothingPickedAndUseDisabled() {
        ask();

        assertThat(textsUnder(required("model-pick-card")))
                .contains(
                        "Which model should BookLoom use?",
                        "Ollama has several models. Choose one for your translations.",
                        "Use this model",
                        "Not now");
        assertThat(options()).extracting(RadioButton::getText).containsExactly("gemma4:e4b", "qwen3:8b");
        assertThat(options()).noneMatch(RadioButton::isSelected);
        assertThat(button("model-pick-use").isDisabled()).isTrue();
        assertThat(button("model-pick-use").getStyleClass()).contains("btn-primary");
        assertThat(button("model-pick-not-now").getStyleClass()).contains("btn-secondary");
    }

    // IF the first model were preselected, THEN pressing Enter would choose a model the person never looked at.
    @Test
    void pick_oneModel_enablesUseAndUseAnswersWithThatModelOnly() {
        ask();

        onFx(() -> options().get(1).fire());
        assertThat(button("model-pick-use").isDisabled()).isFalse();
        onFx(() -> button("model-pick-use").fire());

        assertThat(answers).containsExactly("use qwen3:8b");
        assertThat(isShowing()).isFalse();
    }

    @Test
    void notNow_pressed_answersNotNowAndHidesTheCard() {
        ask();

        onFx(() -> button("model-pick-not-now").fire());

        assertThat(answers).containsExactly("notNow");
        assertThat(isShowing()).isFalse();
    }

    @Test
    void escape_pressed_actsAsNotNow() {
        ask();

        onFx(() -> javafx.event.Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(answers).containsExactly("notNow");
        assertThat(isShowing()).isFalse();
    }

    @Test
    void ask_underUkrainian_speaksUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));
        ask();

        assertThat(textsUnder(required("model-pick-card")))
                .contains("Яку модель використовувати?", "Використати цю модель", "Не зараз");
    }
}
