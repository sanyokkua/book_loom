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
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** A gender that only the first-name list suggested is marked in the table, and a confirmed one is not. */
class GlossaryGenderSuggestedTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry SUGGESTED = new GlossaryEntry(
                    "e1", "p1", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    private static final GlossaryEntry CONFIRMED =
            new GlossaryEntry("e2", "p1", "Hermione", null, TermType.CHARACTER, Gender.FEMALE, false);

    private List<String> genderLabels() throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(SUGGESTED, CONFIRMED)));
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

    // IF choosing the shown value did nothing, THEN "choose a value to confirm it" would be false for the value the
    // person agrees with; the suggestion is stored as the person's.
    @Test
    void suggestedGender_theSameValueChosenAgain_isStoredAsConfirmed() throws Exception {
        genderLabels();
        final Label shown =
                ThemeTestSupport.onFx(() -> required("names-style-table").lookupAll(".glossary-choice").stream()
                        .map(label -> (Label) label)
                        .filter(label -> label.getText().equals("male (suggested)"))
                        .findFirst()
                        .orElseThrow());
        final TableCell<?, ?> row = (TableCell<?, ?>) shown.getParent();
        onFx(() -> Event.fireEvent(shown, click()));
        final ComboBox<?> combo = (ComboBox<?>) ThemeTestSupport.onFx(row::getGraphic);

        onFx(() -> {
            final ListCell<?> cell = popupCells(combo).stream()
                    .filter(candidate -> "male".equals(candidate.getText()))
                    .findFirst()
                    .orElseThrow();
            Event.fireEvent(cell, click());
        });
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated())
                .singleElement()
                .extracting(GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(Gender.MALE, false);
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
