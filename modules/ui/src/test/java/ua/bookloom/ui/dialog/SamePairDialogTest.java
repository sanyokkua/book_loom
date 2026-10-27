package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** The Same language pair card: the existing project shown, the two choices, and when the name button is ready. */
class SamePairDialogTest extends ShellTestBase {

    private static final String AUTOMATIC = "Earth Gravity · EN → UK";
    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");
    private static final ProjectNameRules RULES =
            new ProjectNameRules(Set.of(Project.keyOf(AUTOMATIC), Project.keyOf("Земне тяжіння")), AUTOMATIC, false);

    private final List<String> answers = new ArrayList<>();

    private static ProjectRow existing(final int percent, final boolean started) {
        final Project project = ProjectFixtures.project(
                "p-uk",
                ProjectFixtures.BOOK_FILE,
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.AUTO,
                AUTOMATIC,
                OPENED);
        return ProjectFixtures.row(project, percent, started ? ProjectFixtures.run("p-uk", JobState.COMPLETED) : null);
    }

    private void ask(final ProjectRow row, final String keepName) {
        onFx(() -> injector.getInstance(SamePairDialog.class)
                .ask(
                        row,
                        "Ukrainian",
                        keepName,
                        RULES,
                        () -> answers.add("open"),
                        name -> answers.add("second:" + name),
                        () -> answers.add("cancelled")));
    }

    private void ask() {
        ask(existing(42, true), "Cancel");
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    private TextField name() {
        return (TextField) required("samepair-name");
    }

    private void type(final String text) {
        onFx(() -> name().setText(text));
    }

    @Test
    void ask_shown_namesTheLanguageAndShowsTheExistingProjectWithModelQualityAndProgress() {
        ask();

        assertThat(textsUnder(required("samepair-card")))
                .contains(
                        "This book already has a Ukrainian translation",
                        "Two projects of one book can share languages only if the new one has its own name.",
                        AUTOMATIC,
                        "Ollama · gemma4:e4b · Balanced",
                        "42 %",
                        "Open that project",
                        "Make a second Ukrainian translation");
    }

    @Test
    void ask_existingProjectNeverRun_showsNotStartedInsteadOfAPercent() {
        ask(existing(0, false), "Cancel");

        assertThat(textsUnder(required("samepair-card"))).contains("not started");
    }

    // IF Open were the default, THEN Enter on a card the person opened to make a copy would reopen the old one.
    @Test
    void ask_shown_defaultsToTheSecondTranslationWithAnEmptyFocusedNameAndADisabledButton() {
        ask();

        assertThat(((RadioButton) required("samepair-second")).isSelected()).isTrue();
        assertThat(((RadioButton) required("samepair-open")).isSelected()).isFalse();
        assertThat(name().getText()).isEmpty();
        assertThat(name().getPromptText()).isEqualTo("e.g. " + AUTOMATIC + " · second try");
        assertThat(name().isFocused()).isTrue();
        assertThat(button("samepair-use").getText()).isEqualTo("Use Ukrainian with this name");
        assertThat(button("samepair-use").isDisable()).isTrue();
        assertThat(button("samepair-use").getStyleClass()).contains("btn-primary");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Земне тяжіння", "ЗЕМНЕ ТЯЖІННЯ", "earth gravity · en → uk"})
    void name_usedByAnotherProjectOfTheBook_showsTheErrorAndKeepsTheButtonDisabled(final String typed) {
        ask();

        type(typed);

        assertThat(isVisibleInScene("samepair-error")).isTrue();
        assertThat(((Label) required("samepair-error")).getText()).isEqualTo("That name is already used");
        assertThat(button("samepair-use").isDisable()).isTrue();
    }

    @Test
    void name_freeName_enablesTheButtonAndMakeSecondGetsTheText() {
        ask();

        type("Earth Gravity · 26b");
        assertThat(isVisibleInScene("samepair-error")).isFalse();
        onFx(() -> button("samepair-use").fire());

        assertThat(answers).containsExactly("second:Earth Gravity · 26b");
    }

    @Test
    void chip_pressed_startsFromTheAutomaticNameOfAnEmptyField() {
        ask();

        onFx(() -> button("samepair-chip-26b").fire());

        assertThat(name().getText()).isEqualTo(AUTOMATIC + " · 26b");
        assertThat(button("samepair-use").isDisable()).isFalse();
    }

    // IF Open still demanded a name, THEN a person who only wants the old project would be stopped by a field.
    @Test
    void openSelected_buttonReadsOpenThatProjectIsEnabledAndOpensIt() {
        ask();

        onFx(() -> ((RadioButton) required("samepair-open")).setSelected(true));

        assertThat(button("samepair-use").getText()).isEqualTo("Open that project");
        assertThat(button("samepair-use").isDisable()).isFalse();
        onFx(() -> button("samepair-use").fire());
        assertThat(answers).containsExactly("open");
    }

    @Test
    void keepButton_pressed_runsCancelledAndWearsTheCallersName() {
        ask(existing(42, true), "Keep English");
        assertThat(button("samepair-cancel").getText()).isEqualTo("Keep English");

        onFx(() -> button("samepair-cancel").fire());

        assertThat(answers).containsExactly("cancelled");
        assertThat(isVisibleInScene("samepair-card")).isFalse();
    }

    @Test
    void escape_onTheCard_runsCancelled() {
        ask();

        onFx(() -> javafx.event.Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(answers).containsExactly("cancelled");
    }

    @Test
    void ask_underUkrainian_speaksUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));

        ask();

        assertThat(textsUnder(required("samepair-card")))
                .contains("Ця книга вже має переклад: Ukrainian", "Відкрити той проєкт", "Прогрес");
    }
}
