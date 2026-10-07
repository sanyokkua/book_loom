package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** The glossary table's rows: every cell's content sits on the row's centre line, and an empty table says why. */
class GlossaryTableLayoutTest extends TranslatingScreenTestBase {

    private static final double ROW_CENTRE = 23;
    private static final double ONE_PIXEL = 1;
    private static final GlossaryEntry VICTOR =
            new GlossaryEntry("e1", "p1", "Victor", "Віктор", TermType.CHARACTER, Gender.MALE, true);

    private void showGlossary(final List<GlossaryEntry> entries) throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.frankensteinImport()));
        openImport();
        openBook(BOOK);
        chooseTarget();
        glossary.willAnswer(Result.ok(entries));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private TableView<?> table() {
        return (TableView<?>) required("names-style-table");
    }

    private List<Double> contentCentreOffsets() {
        return ThemeTestSupport.onFx(() -> {
            final TableRow<?> row = table().lookupAll(".table-row-cell").stream()
                    .map(node -> (TableRow<?>) node)
                    .filter(candidate -> candidate.getItem() != null)
                    .findFirst()
                    .orElseThrow();
            final Bounds rowBounds = row.localToScene(row.getBoundsInLocal());
            return row.getChildrenUnmodifiable().stream()
                    .map(child -> child instanceof TableCell<?, ?> cell ? cell.getGraphic() : null)
                    .filter(java.util.Objects::nonNull)
                    .map(graphic -> centreY(graphic) - rowBounds.getMinY())
                    .toList();
        });
    }

    private static double centreY(final Node graphic) {
        final Bounds bounds = graphic.localToScene(graphic.getBoundsInLocal());
        return (bounds.getMinY() + bounds.getMaxY()) / 2;
    }

    // IF a cell's padding or border pushed its content off the row's middle, THEN the type, target and lock would sit
    // at
    // different heights; every cell's content centre is at the middle of the 46 px row, within a pixel.
    @Test
    void row_rendered_hasEveryCellContentCentredOnTheRow() throws Exception {
        showGlossary(List.of(VICTOR));
        awaitFx(() -> !contentCentreOffsets().isEmpty());

        assertThat(contentCentreOffsets())
                .hasSizeGreaterThanOrEqualTo(5)
                .allSatisfy(offset -> assertThat(offset).isCloseTo(ROW_CENTRE, within(ONE_PIXEL)));
    }

    // IF an empty table drew nothing, THEN a person would not know whether the scan is pending or found nothing.
    @Test
    void table_withNoNames_saysSoAndHowToAddSome() throws Exception {
        showGlossary(List.of());

        awaitFx(() -> table().lookupAll(".glossary-empty").stream().anyMatch(Node::isVisible));

        assertThat(ThemeTestSupport.onFx(() -> table().lookupAll(".glossary-empty").stream()
                        .map(node -> ((Label) node).getText())
                        .findFirst()))
                .hasValue("No names yet — run Model scan or add a term.");
    }

    // IF a search that matches nothing showed "no names yet", THEN the person would think the book has none.
    @Test
    void table_searchMatchesNothing_saysTheSearchMatchedNone() throws Exception {
        showGlossary(List.of(VICTOR));
        awaitFx(() -> !contentCentreOffsets().isEmpty());

        onFx(() -> ((TextField) required("names-style-search")).setText("zzz"));

        awaitFx(() -> table().lookupAll(".glossary-empty").stream()
                .anyMatch(node -> node.isVisible() && "No name matches the search.".equals(((Label) node).getText())));
    }
}
