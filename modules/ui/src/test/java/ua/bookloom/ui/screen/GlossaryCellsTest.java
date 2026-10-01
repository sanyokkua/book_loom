package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The glossary's rows stay cheap to scroll: type and gender are text until used, a row's tooltips wait for the pointer,
 * and a row's switch draws no shadow until it has focus.
 */
class GlossaryCellsTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry VICTOR =
            new GlossaryEntry("e1", "p1", "Victor", null, TermType.CHARACTER, Gender.UNKNOWN, false);

    private void showGlossary() throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        glossary.willAnswer(Result.ok(List.of(VICTOR)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private Label typeLabel() {
        return (Label) required("names-style-table").lookup(".glossary-choice");
    }

    private static TableCell<?, ?> cellOf(final Label shown) {
        return (TableCell<?, ?>) shown.getParent();
    }

    private static MouseEvent mouse(final javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(
                type,
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

    // IF every row held two combo boxes, THEN each scrolled frame would repaint two heavy controls per row; a row only
    // read shows its type and gender as text.
    @Test
    void row_notUsed_showsTypeAsTextWithNoComboBox() throws Exception {
        showGlossary();

        assertThat(ThemeTestSupport.onFx(() -> typeLabel().getText())).isEqualTo("character");
        assertThat(required("names-style-table").lookupAll(".combo-box")).isEmpty();
    }

    // IF a click on the text did nothing, THEN a type could no longer be changed; the combo box takes its place and the
    // choice is stored at once.
    @Test
    void typeText_clickedAndAChoiceMade_storesTheNewType() throws Exception {
        showGlossary();
        final Label shown = typeLabel();
        final TableCell<?, ?> cell = cellOf(shown);
        glossary.willAnswer(
                Result.ok(new GlossaryEntry("e1", "p1", "Victor", null, TermType.PLACE, Gender.UNKNOWN, false)));

        onFx(() -> Event.fireEvent(shown, mouse(MouseEvent.MOUSE_CLICKED)));
        @SuppressWarnings("unchecked")
        final ComboBox<TermType> combo = (ComboBox<TermType>) ThemeTestSupport.onFx(cell::getGraphic);
        onFx(() -> {
            combo.hide();
            combo.getSelectionModel().select(TermType.PLACE);
        });
        awaitFx(() -> !glossary.updated().isEmpty());

        assertThat(glossary.updated()).extracting(GlossaryEntry::type).containsExactly(TermType.PLACE);
    }

    // IF Tab skipped the text, THEN a keyboard user could not change a type; reaching it by focus opens the combo box
    // focused, without dropping its list.
    @Test
    void typeText_focused_isReplacedByAFocusedClosedComboBox() throws Exception {
        showGlossary();
        final Label shown = typeLabel();
        final TableCell<?, ?> cell = cellOf(shown);

        onFx(shown::requestFocus);
        final Node graphic = ThemeTestSupport.onFx(cell::getGraphic);

        assertThat(shown.isFocusTraversable()).isTrue();
        assertThat(graphic).isInstanceOf(ComboBox.class);
        assertThat(ThemeTestSupport.onFx(graphic::isFocused)).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> ((ComboBox<?>) graphic).isShowing()))
                .isFalse();
    }

    // IF each row built its tooltips up front, THEN every row the table makes would pay for popups never shown; the
    // tooltip arrives with the pointer.
    @Test
    void lockSwitch_firstHovered_getsItsTooltipThen() throws Exception {
        showGlossary();
        final ToggleSwitch lock = (ToggleSwitch) required("names-style-table").lookup(".toggle-switch");
        assertThat(lock.getTooltip()).isNull();

        onFx(() -> Event.fireEvent(lock, mouse(MouseEvent.MOUSE_ENTERED)));

        assertThat(lock.getTooltip()).isNotNull();
        assertThat(lock.getTooltip().getText()).isEqualTo(lock.getAccessibleHelp());
    }

    // IF each row's switch carried a shadow, THEN every scrolled frame would recompute an effect per row.
    @Test
    void lockSwitch_notFocused_drawsNoThumbShadowUntilFocused() throws Exception {
        showGlossary();
        final ToggleSwitch lock = (ToggleSwitch) required("names-style-table").lookup(".toggle-switch");
        final Node thumb = lock.lookup(".thumb");

        assertThat(ThemeTestSupport.onFx(thumb::getEffect)).isNull();

        onFx(lock::requestFocus);

        assertThat(ThemeTestSupport.onFx(thumb::getEffect)).isNotNull();
    }
}
