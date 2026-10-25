package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.ShellTestBase;

/** The two cards asked before a continued run: what each says, how its buttons answer, and in Ukrainian. */
class ContinueDialogTest extends ShellTestBase {

    private static final ModelSelection EARLIER = new ModelSelection("ollama", "gemma4:e4b");
    private static final ModelSelection NOW = new ModelSelection("ollama", "qwen3:8b");

    private final List<String> answers = new ArrayList<>();

    private ContinueDialog dialog() {
        return injector.getInstance(ContinueDialog.class);
    }

    private void askNotAnswering() {
        onFx(() -> dialog().askNotAnswering(
                        EARLIER,
                        () -> answers.add("tryAgain"),
                        () -> answers.add("useAnother"),
                        () -> answers.add("notNow")));
    }

    private void askDifferent() {
        onFx(() -> dialog().askDifferent(
                        EARLIER,
                        NOW,
                        () -> answers.add("earlier"),
                        () -> answers.add("new"),
                        () -> answers.add("cancelled")));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#continue-card");
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private void escape() {
        onFx(() -> javafx.event.Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    @Test
    void askNotAnswering_shown_namesTheModelAndOffersThreeButtonsWithTryAgainPrimary() {
        askNotAnswering();

        assertThat(textsUnder(required("continue-card")))
                .contains(
                        "AI model not answering",
                        "Ollama · gemma4:e4b is not answering. If it is a program on this computer, open it, then press Try again.",
                        "Try again",
                        "Use another AI model...",
                        "Not now");
        assertThat(button("continue-try-again").getStyleClass()).contains("btn-primary");
        assertThat(button("continue-use-another").getStyleClass()).contains("btn-secondary");
        assertThat(button("continue-not-now").getStyleClass()).contains("btn-secondary");
    }

    @Test
    void askDifferent_shown_namesBothModelsAndOffersTheEarlierOnePrimary() {
        askDifferent();

        assertThat(textsUnder(required("continue-card")))
                .contains(
                        "A different model is chosen",
                        "This translation was started with Ollama · gemma4:e4b. The chosen model is now Ollama · qwen3:8b. A different model can change how the rest of the book reads.",
                        "Use the earlier model",
                        "Continue with the new one");
        assertThat(button("continue-earlier").getStyleClass()).contains("btn-primary");
        assertThat(button("continue-new").getStyleClass()).contains("btn-secondary");
    }

    // IF a button ran more than its own action or left the card open, THEN the run would start twice or never.
    @ParameterizedTest
    @CsvSource({
        "continue-try-again, tryAgain",
        "continue-use-another, useAnother",
        "continue-not-now, notNow",
    })
    void notAnsweringButton_pressed_runsOnlyItsActionAndHidesTheCard(final String id, final String expected) {
        askNotAnswering();

        onFx(() -> button(id).fire());

        assertThat(answers).containsExactly(expected);
        assertThat(isShowing()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "continue-earlier, earlier",
        "continue-new, new",
    })
    void differentButton_pressed_runsOnlyItsActionAndHidesTheCard(final String id, final String expected) {
        askDifferent();

        onFx(() -> button(id).fire());

        assertThat(answers).containsExactly(expected);
        assertThat(isShowing()).isFalse();
    }

    // IF Escape on the different-model card ran nothing, THEN the Translating screen would stay busy for good.
    @Test
    void escape_onTheDifferentModelCard_runsTheCancelAction() {
        askDifferent();

        escape();

        assertThat(answers).containsExactly("cancelled");
        assertThat(isShowing()).isFalse();
    }

    @Test
    void escape_onTheNotAnsweringCard_actsAsNotNow() {
        askNotAnswering();

        escape();

        assertThat(answers).containsExactly("notNow");
        assertThat(isShowing()).isFalse();
    }

    @Test
    void cards_underUkrainianAtTheMinimumWidth_speakUkrainianAndClipNoButtonLabel() {
        useLocale(Locale.forLanguageTag("uk"));
        resizeScene(CONTENT_AT_MINIMUM_WIDTH, CONTENT_AT_MINIMUM_HEIGHT);
        askNotAnswering();

        assertThat(textsUnder(required("continue-card")))
                .contains("Модель ШІ не відповідає", "Спробувати ще раз", "Використати іншу модель ШІ...", "Не зараз");
        assertThat(button("continue-use-another").getText()).isEqualTo("Використати іншу модель ШІ...");

        onFx(() -> button("continue-not-now").fire());
        askDifferent();

        assertThat(textsUnder(required("continue-card")))
                .contains("Обрано іншу модель", "Використати попередню модель", "Продовжити з новою");
        assertThat(shownTexts("continue-earlier")).noneMatch(text -> text.endsWith("..."));
        assertThat(shownTexts("continue-new")).noneMatch(text -> text.endsWith("..."));
    }

    private List<String> shownTexts(final String buttonId) {
        return required(buttonId).lookupAll(".text").stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .toList();
    }
}
