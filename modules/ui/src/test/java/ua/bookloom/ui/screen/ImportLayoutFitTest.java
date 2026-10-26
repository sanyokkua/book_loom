package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.FitProbe;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The import page's "About this book" fold, opened on a short page, keeps the height its rows need. The closed fold,
 * the verdicts and the open-another question are checked at every size by {@code ScreenFitTest}'s catalogue cases.
 */
class ImportLayoutFitTest extends ImportScreenTestBase {

    private static final double TOLERANCE = 0.5;

    @TempDir
    private Path dir;

    // IF an open fold were squashed to its title bar on a short page, THEN its rows would be cut off and unreachable.
    @ParameterizedTest(name = "{0} at {1} x {2}")
    @CsvSource({"en, 960, 640", "uk, 960, 640", "en, 1440, 900"})
    void fold_openedOnAShortPage_keepsTheHeightItsRowsNeed(
            final String language, final double width, final double height) throws TimeoutException {
        useLocale(Locale.forLanguageTag(language));
        final Path source = dir.resolve("book.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinInspected()));
        openImport();
        openBook(source);
        showAtSceneSize(width, height);
        openFold();
        awaitLayout();

        final Node fold = required("import-about");
        final double drawn = boundsOf("import-about").getHeight();
        final double needed = ThemeTestSupport.onFx(
                () -> fold.prefHeight(fold.getLayoutBounds().getWidth()));

        assertThat(drawn).isGreaterThanOrEqualTo(needed - TOLERANCE);
        assertThat(ThemeTestSupport.onFx(() -> FitProbe.cutParts(fold))).isEmpty();
    }
}
