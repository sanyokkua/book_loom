package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/** What the tests of the two equal term cards on Names &amp; style share: the screen, the rows and the lookups. */
abstract class TermTableCardTestBase extends TranslatingScreenTestBase {

    protected static final GlossaryEntry ROSALIND =
            new GlossaryEntry("e1", "p1", "Rosalind", "Розалінда", TermType.CHARACTER, Gender.FEMALE, false);
    protected static final GlossaryEntry HALE =
            new GlossaryEntry("e2", "p1", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false);
    protected static final GlossaryEntry JOHN = new GlossaryEntry(
                    "e3", "p1", "John", "Джон", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    protected static final GlossaryEntry BROOK =
            new GlossaryEntry("e4", "p1", "Brook", null, TermType.PLACE, Gender.UNKNOWN, false);
    protected static final GlossaryEntry WAND =
            new GlossaryEntry("e5", "p1", "Wand", "Паличка", TermType.TERM, Gender.UNKNOWN, false);
    protected static final GlossaryEntry SHOUTED =
            new GlossaryEntry("e6", "p1", "BRANWICK", null, TermType.OTHER, Gender.UNKNOWN, false);

    protected static final LexiconEntry MASTER =
            LexiconEntry.of("p1", "master").seen("господар").seen("господар");
    protected static final LexiconEntry IMP = LexiconEntry.of("p1", "imp");

    protected static final List<String> TOOLBAR_PARTS =
            List.of("search", "add", "scan", "review", "translate", "stop", "import", "export", "more");
    protected static final List<String> UNFOLDED =
            List.of("search", "add", "scan", "review", "translate", "import", "export");
    protected static final List<String> FOLDED = List.of("search", "add", "scan", "review", "translate", "more");
    protected static final Path CHOSEN = Path.of("in.csv");

    @AfterEach
    void releaseTheModel() {
        glossary.releaseModelCalls();
    }

    /** Shows Names &amp; style in this language at this scene size, the glossary holding {@code entries}. */
    protected void showScreen(final String tag, final double width, final double height, final GlossaryEntry... entries)
            throws Exception {
        useLocale(Locale.forLanguageTag(tag));
        readyToStart();
        glossary.willAnswer(Result.ok(List.of(entries)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        showAtSceneSize(width, height);
        awaitFx(() -> optional("recurring-card") != null);
    }

    protected static List<Node> descendants(final Node root) {
        final List<Node> all = new ArrayList<>();
        all.add(root);
        if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> all.addAll(descendants(child)));
        }
        return all;
    }

    /** The toolbar parts of a card that a person can see, in the order they sit, named by their id's last part. */
    protected List<String> shownParts(final String card) {
        return ThemeTestSupport.onFx(() -> descendants(required(card + "-toolbar")).stream()
                .filter(node -> node.getId() != null && node.getId().startsWith(card + "-"))
                .filter(node -> node.isVisible() && node.isManaged())
                .map(node -> node.getId().substring(card.length() + 1))
                .filter(TOOLBAR_PARTS::contains)
                .toList());
    }

    protected TableView<?> table(final String card) {
        return (TableView<?>) required(card + "-table");
    }

    protected List<String> headers(final String card) {
        return ThemeTestSupport.onFx(() -> table(card).getColumns().stream()
                .map(column -> ((Label) column.getGraphic()).getText())
                .toList());
    }

    /** The cells of the first filled row of a card's table, left to right. */
    protected List<TableCell<?, ?>> firstRowCells(final String card) {
        return ThemeTestSupport.onFx(() -> {
            final TableRow<?> row = table(card).lookupAll(".table-row-cell").stream()
                    .map(node -> (TableRow<?>) node)
                    .filter(candidate -> candidate.getItem() != null)
                    .findFirst()
                    .orElseThrow();
            return row.getChildrenUnmodifiable().stream()
                    .<TableCell<?, ?>>map(cell -> (TableCell<?, ?>) cell)
                    .toList();
        });
    }

    /** The cell of the glossary row holding {@code term}, in the column at {@code column}. */
    protected TableCell<?, ?> glossaryCell(final String term, final int column) {
        return ThemeTestSupport.onFx(() -> table("names-style").lookupAll(".table-row-cell").stream()
                .map(node -> (TableRow<?>) node)
                .filter(row -> row.getItem() instanceof GlossaryEntry entry
                        && entry.term().equals(term))
                .findFirst()
                .map(row -> (TableCell<?, ?>) row.getChildrenUnmodifiable().get(column))
                .orElseThrow());
    }

    protected String shownText(final TableCell<?, ?> cell) {
        return ThemeTestSupport.onFx(() -> ((Label) cell.getGraphic()).getText());
    }

    protected double minY(final String id) {
        return ThemeTestSupport.onFx(
                () -> required(id).localToScene(required(id).getBoundsInLocal()).getMinY());
    }
}
