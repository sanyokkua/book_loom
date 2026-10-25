package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ThemeTestSupport;

/** The columns and cells of the two term tables (15i.U5): what each shows, where the remove cross sits, what is gone. */
class TermTableCardColumnsTest extends TermTableCardTestBase {

    // ---- columns ---------------------------------------------------------------------------------------------

    // IF the glossary kept its Check column or hid the remove cross inside the Term cell, THEN the table would not
    // match the Recurring terms table beside it.
    @ParameterizedTest
    @CsvSource({"en, Term, Kind, Translation, Gender, Locked", "uk, Термін, Вид, Переклад, Рід, Закріплено"})
    void glossaryColumns_shown_areTermKindTranslationGenderLockedThenAnUnnamedRemoveColumn(
            final String tag,
            final String term,
            final String type,
            final String target,
            final String gender,
            final String locked)
            throws Exception {
        showScreen(tag, 1024, 700, ROSALIND);

        assertThat(headers("names-style")).containsExactly(term, type, target, gender, locked, "");
    }

    // IF a column were squeezed below its header, THEN "Закріплено" would read "Закріпл..." at the smallest window.
    @ParameterizedTest
    @CsvSource({"en, 960", "uk, 960", "uk, 1040"})
    void glossaryHeaders_smallWindow_areWhole(final String tag, final double width) throws Exception {
        showScreen(tag, width, 640, ROSALIND);

        assertThat(ThemeTestSupport.onFx(() -> table("names-style").getColumns().stream()
                        .map(column -> (Label) column.getGraphic())
                        .filter(header -> header.getWidth() + 0.5 < header.prefWidth(-1))
                        .map(Label::getText)
                        .toList()))
                .as("headers drawn narrower than their words")
                .isEmpty();
    }

    // IF the Used column were missing, THEN the person could not tell how often the book writes a repeated word.
    @ParameterizedTest
    @CsvSource({"en, Term, Translation, Used", "uk, Термін, Переклад, Використано"})
    void recurringColumns_shown_areTermTranslationUsedThenTwoUnnamedActionColumns(
            final String tag, final String term, final String target, final String used) throws Exception {
        lexicon.holds(MASTER);
        showScreen(tag, 1024, 700, ROSALIND);

        assertThat(headers("recurring")).containsExactly(term, target, used, "", "");
    }

    // IF a row's remove cross sat anywhere but the last cell, THEN the two tables would not end the same way.
    @ParameterizedTest
    @CsvSource({"names-style", "recurring"})
    void removeCross_eachRow_sitsInTheLastColumnOnly(final String card) throws Exception {
        lexicon.holds(MASTER);
        showScreen("en", 1024, 700, ROSALIND);
        awaitFx(() -> !firstRowCells(card).isEmpty());

        final List<TableCell<?, ?>> cells = firstRowCells(card);

        assertThat(ThemeTestSupport.onFx(() -> cells.stream()
                        .map(cell -> cell.lookup(".glossary-remove") != null)
                        .toList()))
                .last()
                .isEqualTo(true);
        assertThat(ThemeTestSupport.onFx(() -> cells.subList(0, cells.size() - 1).stream()
                        .filter(cell -> cell.lookup(".glossary-remove") != null)
                        .count()))
                .isZero();
    }

    // IF the promote action kept its old words, THEN the Recurring terms table would not read "→ Glossary".
    @ParameterizedTest
    @CsvSource({"en, → Glossary", "uk, → "})
    void promoteButton_eachRow_readsArrowThenGlossary(final String tag, final String start) throws Exception {
        lexicon.holds(MASTER);
        showScreen(tag, 1024, 700, ROSALIND);
        awaitFx(() -> !firstRowCells("recurring").isEmpty());

        final TableCell<?, ?> promote = firstRowCells("recurring").get(3);

        assertThat(ThemeTestSupport.onFx(() -> ((Button) promote.lookup(".button")).getText()))
                .startsWith(start);
    }

    // IF the Check chips stayed, THEN a junk-looking or targetless row would be flagged twice: by chip and by
    // placeholder. (The "No target" placeholder itself is the prompt text of the empty Target field, which is a text
    // node of its own, so it is not looked for here; a chip is what is absent.)
    @Test
    void table_junkLookingAndTargetlessRows_showNoCheckChipsAndNoNotUsedYetText() throws Exception {
        lexicon.holds(IMP);
        showScreen("en", 1024, 700, SHOUTED, HALE);
        awaitFx(() -> !firstRowCells("recurring").isEmpty());

        assertThat(ThemeTestSupport.onFx(
                        () -> table("names-style").lookupAll(".glossary-flag").size()))
                .isZero();
        assertThat(textsUnder(required("names-style-table"))).doesNotContain("Likely junk", "Check");
        assertThat(textsUnder(required("recurring-table"))).doesNotContain("not used yet");
    }

    // ---- the cells ---------------------------------------------------------------------------------------------

    // IF a row without a target showed an empty box, THEN it would look like a field that failed to load.
    @ParameterizedTest
    @CsvSource({"en, No target", "uk, Без перекладу"})
    void targetCell_rowWithoutTarget_askForTargetWithThePlaceholder(final String tag, final String placeholder)
            throws Exception {
        showScreen(tag, 1024, 700, HALE, ROSALIND);

        final TextField empty =
                (TextField) ThemeTestSupport.onFx(() -> glossaryCell("Hale", 2).lookup(".text-field"));
        final TextField filled = (TextField)
                ThemeTestSupport.onFx(() -> glossaryCell("Rosalind", 2).lookup(".text-field"));

        assertThat(ThemeTestSupport.<String>onFx(() -> empty.getPromptText())).isEqualTo(placeholder);
        assertThat(ThemeTestSupport.<String>onFx(() -> filled.getText())).isEqualTo("Розалінда");
    }

    // IF a place or a term showed "unknown" as its gender, THEN the person would try to settle something that has none.
    @Test
    void genderCell_nonCharacters_showADashAndCharactersTheirGender() throws Exception {
        showScreen("en", 1024, 700, ROSALIND, HALE, BROOK, WAND);

        assertThat(shownText(glossaryCell("Brook", 3))).isEqualTo("—");
        assertThat(shownText(glossaryCell("Wand", 3))).isEqualTo("—");
        assertThat(shownText(glossaryCell("Rosalind", 3))).isEqualTo("female");
        assertThat(shownText(glossaryCell("Hale", 3))).isEqualTo("unknown");
    }

    // IF a guess were drawn like a decision, THEN the person would not know what still wants a look; only the
    // SUGGESTED origin carries the mark.
    @Test
    void genderCell_suggestedCharacter_isMarkedAndAConfirmedOneIsNot() throws Exception {
        showScreen("en", 1024, 700, JOHN, ROSALIND);

        assertThat(shownText(glossaryCell("John", 3))).isEqualTo("male (suggested)");
        assertThat(shownText(glossaryCell("Rosalind", 3))).isEqualTo("female");
    }
}
