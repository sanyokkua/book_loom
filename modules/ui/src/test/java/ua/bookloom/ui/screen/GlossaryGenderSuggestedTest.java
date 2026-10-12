package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.event.Event;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * A gender whose origin is SUGGESTED (a first-name or unverified guess) is marked in the table, and a person's or a
 * verified one is not (15i.N0); a gender the person picks is stored as theirs.
 */
class GlossaryGenderSuggestedTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry SUGGESTED = new GlossaryEntry(
                    "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    private static final GlossaryEntry CONFIRMED =
            new GlossaryEntry("e2", "p1", "Rosalind", null, TermType.CHARACTER, Gender.FEMALE, false);
    private static final GlossaryEntry VERIFIED = new GlossaryEntry(
                    "e3", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withInferredGender(Gender.MALE);

    private List<String> genderLabels() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(SUGGESTED, CONFIRMED, VERIFIED)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        return ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                .map(label -> ((Label) label).getText())
                .toList());
    }

    @Test
    void table_suggestedAndConfirmedGenders_markOnlyTheSuggestedOne() throws Exception {
        assertThat(genderLabels()).contains("male (suggested)", "female").doesNotContain("female (suggested)");
    }

    // IF a verified gender were drawn like a guess, THEN the table would ask the person to confirm what the book
    // proved;
    // only the SUGGESTED origin carries the mark.
    @Test
    void table_personsVerifiedAndSuggestedGenders_markExactlyTheSuggestedOne() throws Exception {
        final List<String> labels = genderLabels();

        assertThat(labels).filteredOn(label -> label.contains("(suggested)")).containsExactly("male (suggested)");
        assertThat(labels).contains("male", "female");
    }

    // IF choosing the shown value did nothing, THEN "choose a value to confirm it" would be false for the value the
    // person agrees with; the suggestion is stored as the person's.
    @Test
    void suggestedGender_theSameValueChosenAgain_isStoredAsConfirmed() throws Exception {
        choose("male (suggested)", "male");

        assertThat(glossary.updated())
                .singleElement()
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(Gender.MALE, GenderOrigin.PERSON);
    }

    // IF a person's correction of a verified gender stayed "verified", THEN the next review could overwrite it.
    @Test
    void verifiedGender_anotherValueChosen_isStoredAsThePersons() throws Exception {
        final ComboBox<Gender> combo = openEditor("male");

        // A popup click only reports the value already held; a different value arrives as the combo's own selection.
        onFx(() -> combo.setValue(Gender.FEMALE));
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated())
                .singleElement()
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly("Hale", Gender.FEMALE, GenderOrigin.PERSON);
    }

    private void choose(final String shownText, final String optionText) throws Exception {
        final ComboBox<?> combo = openEditor(shownText);

        onFx(() -> {
            final ListCell<?> cell = popupCells(combo).stream()
                    .filter(candidate -> optionText.equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow();
            Event.fireEvent(cell, click());
        });
        awaitFx(() -> !glossary.updated().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Gender> openEditor(final String shownText) throws Exception {
        genderLabels();
        final Label shown =
                ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                        .map(label -> (Label) label)
                        .filter(label -> label.getText().equals(shownText))
                        .findFirst()
                        .orElseThrow());
        final TableCell<?, ?> row = (TableCell<?, ?>) shown.getParent();
        onFx(() -> Event.fireEvent(shown, click()));
        return (ComboBox<Gender>) ThemeTestSupport.onFx(row::getGraphic);
    }

    private static java.util.List<ListCell<?>> popupCells(final ComboBox<?> combo) {
        final javafx.scene.Node content = ((ComboBoxListViewSkin<?>) combo.getSkin()).getPopupContent();
        return content.lookupAll(".list-cell").stream()
                .<ListCell<?>>map(node -> (ListCell<?>) node)
                .toList();
    }

    private static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }
}
