package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.scene.control.Labeled;
import javafx.scene.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The toolbar of both term cards on Names & style shows every caption whole at the window minimum, at the default size
 * and at a large one, in English and Ukrainian: the toolbar folds Import and Export into its menu rather than cutting a
 * button's words to an ellipsis, also while a model action runs and its Stop button joins the row.
 */
class NamesStyleToolbarFitTest extends TranslatingScreenTestBase {

    private static final double SEARCH_FLOOR = 120;

    private static final List<String> CAPTIONS = List.of(
            "names-style-glossary-title",
            "names-style-add",
            "names-style-scan",
            "names-style-review",
            "names-style-translate",
            "names-style-import",
            "names-style-export",
            "names-style-more",
            "recurring-title",
            "recurring-scan",
            "recurring-review",
            "recurring-translate",
            "recurring-add",
            "recurring-import",
            "recurring-export",
            "recurring-more");

    @AfterEach
    void releaseTheModel() {
        glossary.releaseModelCalls();
    }

    private void showNamesStyle(final String tag, final double width, final double height) throws Exception {
        useLocale(java.util.Locale.forLanguageTag(tag));
        readyToStart();
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(0, 0, List.of())));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
        // The headless screen is 1000 pixels square; a window larger than that must start at its corner to be drawn.
        interact(() -> {
            scene.getWindow().setX(0);
            scene.getWindow().setY(0);
        });
        resizeScene(width, height);
    }

    /** What each caption draws, against what it says, for every shown caption whose drawn text differs. */
    private Map<String, String> cutCaptions(final List<String> ids) {
        return ThemeTestSupport.onFx(() -> ids.stream()
                .filter(this::isShown)
                .filter(id -> !drawn((Labeled) required(id)).equals(((Labeled) required(id)).getText()))
                .collect(Collectors.toMap(id -> id, id -> drawn((Labeled) required(id)))));
    }

    private static String drawn(final Labeled labeled) {
        return labeled.getChildrenUnmodifiable().stream()
                .filter(Text.class::isInstance)
                .map(node -> ((Text) node).getText())
                .collect(Collectors.joining());
    }

    // IF a caption were cut to "Model sc...", THEN the person would have to guess what the button does.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({
        "en, 960, 640",
        "en, 1024, 700",
        "en, 1300, 800",
        "en, 1440, 900",
        "uk, 960, 640",
        "uk, 1024, 700",
        "uk, 1300, 800",
        "uk, 1440, 900"
    })
    void toolbar_atEachWindowSize_drawsEveryCaptionWhole(final String tag, final double width, final double height)
            throws Exception {
        showNamesStyle(tag, width, height);

        assertThat(cutCaptions(CAPTIONS)).isEmpty();
    }

    // IF Stop were squeezed to "S..." while the model works, THEN the one way out of a slow request would be
    // unreadable.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"en, 960, 640", "en, 1024, 700", "uk, 960, 640", "uk, 1024, 700"})
    void toolbar_modelActionRunning_drawsStopAndEveryCaptionWhole(
            final String tag, final double width, final double height) throws Exception {
        showNamesStyle(tag, width, height);
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop"));

        final List<String> withStop = new java.util.ArrayList<>(CAPTIONS);
        withStop.add("names-style-stop");
        withStop.add("recurring-stop");
        assertThat(cutCaptions(withStop)).isEmpty();
    }

    // IF the search field could shrink to nothing, THEN the person could not read or type a query: Review and
    // Translate fold into the menu before the field goes below its floor.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"uk, 960, 640", "uk, 1024, 700", "uk, 1300, 800"})
    void toolbar_modelActionRunningInUkrainian_keepsEverySearchFieldAtItsFloor(
            final String tag, final double width, final double height) throws Exception {
        showNamesStyle(tag, width, height);
        glossary.holdModelCalls();
        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop"));

        final List<Double> widths = ThemeTestSupport.onFx(() -> List.of(
                required("names-style-search").getLayoutBounds().getWidth(),
                required("recurring-search").getLayoutBounds().getWidth()));

        assertThat(widths).allSatisfy(each -> assertThat(each).isGreaterThanOrEqualTo(SEARCH_FLOOR));
    }
}
