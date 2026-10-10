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
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * The glossary card's toolbar on Names & style shows every caption whole at the window minimum, at the default size and
 * at a large one: the toolbar wraps rather than cutting a button's words to an ellipsis, also while a model action runs
 * and its Stop button joins the row.
 */
class NamesStyleToolbarFitTest extends TranslatingScreenTestBase {

    private static final List<String> CAPTIONS = List.of(
            "names-style-glossary-title",
            "names-style-add",
            "names-style-scan",
            "names-style-review",
            "names-style-translate",
            "names-style-import",
            "names-style-export",
            "recurring-title",
            "recurring-scan",
            "recurring-review",
            "recurring-translate",
            "recurring-add");

    @AfterEach
    void releaseTheModel() {
        glossary.releaseModelCalls();
    }

    private void showNamesStyle(final double width, final double height) throws Exception {
        readyToStart();
        glossary.willAnswer(Result.ok(List.of()));
        glossary.willAnswer(Result.ok(List.of()));
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

    // IF a caption were cut to "Model sc…", THEN the person would have to guess what the button does.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1024, 700", "1400, 900"})
    void toolbar_atEachWindowSize_drawsEveryCaptionWhole(final double width, final double height) throws Exception {
        showNamesStyle(width, height);

        assertThat(cutCaptions(CAPTIONS)).isEmpty();
    }

    // IF Stop were squeezed to "S…" while the model works, THEN the one way out of a slow request would be unreadable.
    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"960, 640", "1024, 700"})
    void toolbar_modelActionRunning_drawsStopAndEveryCaptionWhole(final double width, final double height)
            throws Exception {
        showNamesStyle(width, height);
        glossary.holdModelCalls();

        onFx(() -> button("names-style-review").fire());
        awaitFx(() -> isShown("names-style-stop"));

        final List<String> withStop = new java.util.ArrayList<>(CAPTIONS);
        withStop.add("names-style-stop");
        withStop.add("recurring-stop");
        assertThat(cutCaptions(withStop)).isEmpty();
    }
}
