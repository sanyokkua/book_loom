package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** Names & style explains itself: the steps under the title, and a hover explanation on every control it offers. */
class NamesStyleTipsTest extends TranslatingScreenTestBase {

    private void showWithOneTerm() throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        final GlossaryEntry entry =
                new GlossaryEntry("e1", "p1", "Frankenstein", null, TermType.CHARACTER, Gender.UNKNOWN, false);
        glossary.willAnswer(Result.ok(List.of(entry)));
        glossary.willAnswer(Result.ok(List.of(entry)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private String expected(final String constant) {
        return injector.getInstance(Messages.class).get(MessageKey.valueOf(constant));
    }

    // IF the lead were a single vague sentence, THEN a person would not know what to do with the table.
    @Test
    void subtitle_shown_leadsWithTheScanAndNumbersTheThreeThingsToDo() throws Exception {
        showWithOneTerm();

        assertThat(labelText("names-style-subtitle"))
                .startsWith("The scan lists the names and terms it found in the book.")
                .contains("1. Write the spelling you want in Target.")
                .contains("2. Set Type and Gender")
                .contains("3. Switch Locked on")
                .contains("Optional");
    }

    // IF a button carried another control's words, THEN hovering it would explain the wrong action.
    @ParameterizedTest
    @CsvSource({
        "names-style-add,NAMES_STYLE_ADD_TIP",
        "names-style-model-scan,NAMES_STYLE_MODEL_SCAN_TIP",
        "names-style-import,NAMES_STYLE_IMPORT_TIP",
        "names-style-export,NAMES_STYLE_EXPORT_TIP",
        "names-style-back,NAMES_STYLE_BACK_TIP",
        "names-style-start,NAMES_STYLE_START_TIP",
        "names-style-banner,NAMES_STYLE_SKIP_TIP"
    })
    void screen_shown_eachActionExplainsItself(final String id, final String tip) throws Exception {
        showWithOneTerm();

        assertThat(TooltipProbe.tipText(required(id))).isEqualTo(expected(tip));
    }

    // IF a column header had no explanation, THEN Type, Gender and Locked would stay jargon.
    @ParameterizedTest
    @CsvSource({
        "0,NAMES_STYLE_COLUMN_SOURCE_TIP",
        "1,NAMES_STYLE_COLUMN_TYPE_TIP",
        "2,NAMES_STYLE_COLUMN_TARGET_TIP",
        "3,NAMES_STYLE_COLUMN_GENDER_TIP",
        "4,NAMES_STYLE_COLUMN_LOCKED_TIP"
    })
    void table_columnHeader_explainsItsColumn(final int column, final String tip) throws Exception {
        showWithOneTerm();
        final TableView<?> table = (TableView<?>) required("names-style-table");

        assertThat(TooltipProbe.headerTipText(table.getColumns().get(column))).isEqualTo(expected(tip));
    }

    // IF the row's own controls were untipped, THEN the place where names are actually edited would stay silent.
    @Test
    void row_controls_eachExplainsItself() throws Exception {
        showWithOneTerm();
        final Node root = required("names-style-table");

        final List<Node> combos = List.copyOf(root.lookupAll(".combo-box"));
        assertThat(TooltipProbe.tipText(root.lookup(".glossary-remove"))).isEqualTo(expected("NAMES_STYLE_REMOVE_TIP"));
        assertThat(TooltipProbe.tipText(root.lookup(".text-field")))
                .isEqualTo(expected("NAMES_STYLE_COLUMN_TARGET_TIP"));
        assertThat(combos).hasSize(2).allMatch(node -> node instanceof ComboBox<?>);
        assertThat(TooltipProbe.tipText(combos.get(0))).isEqualTo(expected("NAMES_STYLE_COLUMN_TYPE_TIP"));
        assertThat(TooltipProbe.tipText(combos.get(1))).isEqualTo(expected("NAMES_STYLE_COLUMN_GENDER_TIP"));
        assertThat(root.lookup(".toggle-switch")).isInstanceOf(ToggleSwitch.class);
        assertThat(TooltipProbe.tipText(root.lookup(".toggle-switch")))
                .isEqualTo(expected("NAMES_STYLE_COLUMN_LOCKED_TIP"));
        assertThat(root.lookup(".text-field")).isInstanceOf(TextField.class);
    }
}
