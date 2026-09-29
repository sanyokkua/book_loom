package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testfx.framework.junit5.ApplicationTest;

/** The searchable choice box narrows by name ignoring case and accents and settles typed text per its mode. */
@SuppressWarnings("NullAway.Init")
class SearchableComboTest extends ApplicationTest {

    private static final List<String> LANGUAGES = List.of(
            "Belarusian",
            "Bulgarian",
            "Catalan",
            "Chinese (Simplified)",
            "Chinese (Traditional)",
            "Croatian",
            "Czech",
            "Danish",
            "Dutch",
            "English",
            "Estonian",
            "Finnish",
            "French",
            "German",
            "Greek",
            "Hungarian",
            "Irish",
            "Italian",
            "Japanese",
            "Korean",
            "Latvian",
            "Lithuanian",
            "Norwegian Bokmål",
            "Polish",
            "Portuguese",
            "Romanian",
            "Serbian",
            "Slovak",
            "Slovenian",
            "Spanish",
            "Swedish",
            "Turkish",
            "Ukrainian",
            "Vietnamese");

    private VBox root;
    private Button elsewhere;

    @Override
    public void start(final Stage stage) {
        elsewhere = new Button("elsewhere");
        elsewhere.setId("elsewhere");
        root = new VBox(elsewhere);
        stage.setScene(new Scene(root, 500, 400));
        stage.show();
    }

    private <T> SearchableCombo<T> show(final SearchableCombo<T> combo) {
        combo.setId("combo");
        interact(() -> root.getChildren().add(0, combo));
        return combo;
    }

    private void typeInto(final SearchableCombo<?> combo, final String text) {
        clickOn(combo.getEditor());
        interact(() -> combo.getEditor().setText(text));
    }

    private void moveFocusAway() {
        clickOn(elsewhere);
    }

    private static String names(final SearchableCombo<String> combo) {
        return combo.getItems().stream().collect(Collectors.joining("|"));
    }

    @ParameterizedTest
    @CsvSource({
        "ukr,Ukrainian",
        "fren,French",
        "BOKMAL,Norwegian Bokmål",
        "chin,Chinese (Simplified)|Chinese (Traditional)"
    })
    void typing_text_narrowsToEntriesContainingItIgnoringCaseAndAccents(final String typed, final String expected) {
        final SearchableCombo<String> combo = show(SearchableCombo.strict(LANGUAGES, s -> s));

        typeInto(combo, typed);

        assertThat(names(combo)).contains(expected.split("\\|"));
        assertThat(combo.getItems()).hasSizeLessThan(LANGUAGES.size());
    }

    @Test
    void typing_ukr_leavesExactlyUkrainian() {
        final SearchableCombo<String> combo = show(SearchableCombo.strict(LANGUAGES, s -> s));

        typeInto(combo, "ukr");

        assertThat(combo.getItems()).containsExactly("Ukrainian");
    }

    @Test
    void strict_unknownText_keepsPreviousValueAndReportsInvalidText() {
        final SearchableCombo<String> combo = show(SearchableCombo.strict(LANGUAGES, s -> s));
        interact(() -> combo.select("Ukrainian"));

        typeInto(combo, "xyz");
        moveFocusAway();

        assertThat(combo.getCommitted()).isEqualTo("Ukrainian");
        assertThat(combo.getInvalidText()).isEqualTo("xyz");
        assertThat(combo.getEditor().getText()).isEqualTo("Ukrainian");
    }

    @Test
    void freeText_typedText_isCommittedAsTyped() {
        final SearchableCombo<String> combo = show(SearchableCombo.freeText(LANGUAGES, s -> s, s -> s));

        typeInto(combo, "solarpunk novella");
        moveFocusAway();

        assertThat(combo.getCommitted()).isEqualTo("solarpunk novella");
    }

    private SearchableCombo<String> parsedCombo() {
        return show(SearchableCombo.parsed(
                List.of("English", "Polish", "Ukrainian"),
                s -> "la".equals(s) ? "Latin" : s,
                text -> "Latin".equals(text) || "la".equals(text) ? Optional.of("la") : Optional.empty()));
    }

    @Test
    void parsed_valueTheListDoesNotHold_isCommitted() {
        final SearchableCombo<String> combo = parsedCombo();

        typeInto(combo, "Latin");
        moveFocusAway();

        assertThat(combo.getCommitted()).isEqualTo("la");
    }

    @Test
    void parsed_unrecognisedText_keepsPreviousAndLaterChoiceClearsInvalidText() {
        final SearchableCombo<String> combo = parsedCombo();
        interact(() -> combo.select("Polish"));

        typeInto(combo, "Elvish");
        moveFocusAway();
        assertThat(combo.getCommitted()).isEqualTo("Polish");
        assertThat(combo.getInvalidText()).isEqualTo("Elvish");

        interact(() -> combo.getSelectionModel().select("Ukrainian"));

        assertThat(combo.getCommitted()).isEqualTo("Ukrainian");
        assertThat(combo.getInvalidText()).isNull();
    }

    @Test
    void choosingFromNarrowedList_commitsItAndEmptiesFilter() {
        final SearchableCombo<String> combo = show(SearchableCombo.strict(LANGUAGES, s -> s));
        typeInto(combo, "ukr");

        interact(() -> combo.getSelectionModel().select("Ukrainian"));

        assertThat(combo.getCommitted()).isEqualTo("Ukrainian");
        assertThat(combo.getItems()).hasSize(LANGUAGES.size());
    }
}
