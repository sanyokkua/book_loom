package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;

/** A save the desk refuses: its reason fits the save, stays only while the refused text does, and moves nothing. */
class ReviewDeskRefusedSaveTest extends ReviewDeskTestBase {

    private static final String PROBLEM = "review-problem";
    private static final String TYPED = "Він пішов ⟦0⟧геть⟦1⟧.";
    private static final String REASON = "This text would change the book's Markdown formatting — it adds a heading,"
            + " list, emphasis or other structure the original did not have, or loses one it had.";

    private void refuseTheNextSave() {
        desk.willAnswer(Result.err(AppError.of(
                ErrorCode.validation,
                "This translation could not be kept",
                REASON,
                SafeDetails.empty().withReason(FailureReason.MARKDOWN_STRUCTURE).render(),
                null)));
    }

    private void saveRefused() throws Exception {
        openDesk();
        selectRow(2, "ch5 · p12");
        type(TYPED);
        refuseTheNextSave();
        onFx(() -> main().fire());
        awaitFx(() -> isShown(PROBLEM));
    }

    // IF the reason stayed after the person corrected the text, THEN it would describe a text no longer there until
    // the next Save; it goes as soon as the editor holds anything else, and stays while it holds the refused text.
    @Test
    void refusedSave_textTypedOver_reasonClearsAtOnce() throws Exception {
        saveRefused();
        assertThat(labelText(PROBLEM)).isEqualTo(REASON);

        type(TYPED + " ");

        assertThat(isShown(PROBLEM)).isFalse();
    }

    // IF a Ukrainian window showed the generic "something is not valid" for a refused save, THEN the person would not
    // know the formatting is what to fix; the reason has its own words in both languages.
    @Test
    void refusedSave_ukrainianWindow_namesTheMarkdownFormatting() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));

        saveRefused();

        assertThat(labelText(PROBLEM)).contains("Markdown").doesNotContain("Restoring");
    }

    // IF the reason's unwrapped width widened the detail, THEN the list was squeezed narrower after every refused save
    // and the first line ran into the scroll bar; the split keeps its widths and the reason ends before the bar.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void refusedSave_smallestWindow_listKeepsItsWidthAndTheReasonEndsBeforeTheBar(final String language)
            throws Exception {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(960, 640);
        openDesk();
        final double listBefore = fx(() -> rowList().getWidth());

        selectRow(2, "ch5 · p12");
        type(TYPED);
        refuseTheNextSave();
        onFx(() -> main().fire());
        awaitFx(() -> isShown(PROBLEM));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fx(() -> rowList().getWidth())).isEqualTo(listBefore);
        final double[] edges = fx(() -> {
            final Node problem = required(PROBLEM);
            return new double[] {
                problem.localToScene(problem.getBoundsInLocal()).getMaxX(), rightEdge()
            };
        });
        assertThat(edges[0]).isLessThan(edges[1]);
    }

    // The left edge of the detail's own vertical bar (not a text area's inside it) when it shows, else the right edge
    // of the detail's viewport.
    private double rightEdge() {
        final ScrollPane scroll = (ScrollPane) scene.getRoot().lookup(".review-detail-scroll");
        final Bounds viewport = scroll.localToScene(scroll.getBoundsInLocal());
        return scroll.getChildrenUnmodifiable().stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible())
                .mapToDouble(bar -> bar.localToScene(bar.getBoundsInLocal()).getMinX())
                .findFirst()
                .orElse(viewport.getMaxX());
    }
}
