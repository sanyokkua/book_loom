package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.LanguageEvidence;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.dialog.OpenAnotherBookDialog;
import ua.bookloom.ui.state.RunState;

/**
 * Nothing the import page or its question says is cut: a wrapped line shows all its rows, a button shows its whole
 * label, and the question card stays inside the window. Ukrainian runs at both sizes because its words are the longest.
 */
class ImportLayoutFitTest extends ImportScreenTestBase {

    private static final double TOLERANCE = 0.5;

    @TempDir
    private Path dir;

    /** The labelled parts at or below {@code root} that are drawn smaller than their text needs. */
    private List<String> cutParts(final Node root) {
        return ThemeTestSupport.onFx(() -> {
            final List<String> cut = new ArrayList<>();
            collect(root, cut);
            return cut;
        });
    }

    private static void collect(final Node node, final List<String> cut) {
        if (!node.isVisible()) {
            return;
        }
        // A titled pane is a container here: its preferred width is its content's, which may stack instead.
        if (node instanceof Labeled labeled && !(node instanceof TitledPane)) {
            final double width = labeled.getLayoutBounds().getWidth();
            final boolean isShort = labeled.isWrapText()
                    ? labeled.getLayoutBounds().getHeight() + TOLERANCE < labeled.prefHeight(width)
                    : width + TOLERANCE < labeled.prefWidth(-1);
            if (isShort) {
                cut.add(labeled.getId() + " '" + labeled.getText() + "'");
            }
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, cut));
        }
    }

    private void open(final String fileName, final Result<ua.bookloom.api.pipeline.ImportedBook> answer)
            throws TimeoutException {
        final Path source = dir.resolve(fileName);
        projects.on(source, answer);
        openImport();
        openBook(source);
    }

    // IF the verdict or the fold's summary were cut, THEN the person could not read what the import found.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 960, 640", "uk, 960, 640", "en, 1440, 900", "uk, 1440, 900"})
    void verdictAndFold_twoLanguageMarks_showEveryWordWhole(
            final String language, final double width, final double height) throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        open(
                "book.epub",
                Result.ok(BookFixtures.inspected(
                        "p",
                        BookFormat.EPUB,
                        "3.0",
                        "Title",
                        "Author",
                        BookFixtures.evidence("en", "en", "uk", LanguageEvidence.Verdict.MISMATCH),
                        11,
                        78_214,
                        7,
                        0,
                        BookFixtures.pngCover())));

        showAtSceneSize(width, height);

        assertThat(cutParts(required("import-verdict"))).isEmpty();
        assertThat(cutParts(required("import-about"))).isEmpty();
    }

    // IF an open fold were squashed to its title bar on a short page, THEN its rows would be cut off and unreachable.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 960, 640", "uk, 960, 640", "en, 1440, 900"})
    void fold_openedOnAShortPage_keepsTheHeightItsRowsNeed(
            final String language, final double width, final double height) throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        open("book.epub", Result.ok(BookFixtures.frankensteinInspected()));
        showAtSceneSize(width, height);
        openFold();
        awaitLayout();

        final double drawn = boundsOf("import-about").getHeight();
        final double needed = ThemeTestSupport.onFx(() -> {
            final Node fold = required("import-about");
            return fold.prefHeight(fold.getLayoutBounds().getWidth());
        });

        assertThat(drawn).isGreaterThanOrEqualTo(needed - TOLERANCE);
        assertThat(cutParts(required("import-about"))).isEmpty();
    }

    // IF a refusal's buttons were cut, THEN the way out of a refused file would be unreadable.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 960, 640", "uk, 960, 640", "en, 1440, 900", "uk, 1440, 900"})
    void verdict_lockedBook_showsItsLineAndButtonWhole(final String language, final double width, final double height)
            throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        open("Purchased_Novel.epub", Result.ok(BookFixtures.drmProtected("EPUB", "Adobe ADEPT")));

        showAtSceneSize(width, height);

        assertThat(cutParts(required("import-verdict"))).isEmpty();
    }

    // IF the question's buttons were cut or the card ran past the window, THEN an answer could not be given.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 960, 640", "uk, 960, 640", "en, 1440, 900", "uk, 1440, 900"})
    void question_everySize_keepsItsButtonsWholeInsideTheWindow(
            final String language, final double width, final double height) {
        useLocale(Locale.forLanguageTag(language));
        openImport();
        showAtSceneSize(width, height);

        onFx(() -> injector.getInstance(OpenAnotherBookDialog.class)
                .ask("Earth Gravity · EN → UK · 26b Max", "velk.fb2", RunState.RUNNING, () -> {}, () -> {}));
        final Node card = required("open-another-card");
        final Bounds bounds = ThemeTestSupport.onFx(() -> card.localToScene(card.getLayoutBounds()));

        assertThat(cutParts(card)).isEmpty();
        assertThat(bounds.getMinX()).isGreaterThanOrEqualTo(0);
        assertThat(bounds.getMaxX()).isLessThanOrEqualTo(width);
        assertThat(bounds.getMaxY()).isLessThanOrEqualTo(height);
    }
}
