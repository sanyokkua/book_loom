package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** A prompt's or reply's body is as tall as its wrapped text up to the cap, so nothing is clipped below it. */
@SuppressWarnings("NullAway.Init")
class TextBodyTest extends FxTestBase {

    private static final String SENTENCE =
            "The boats waited by the shore while the river ran slowly past the old mill. ";
    private static final double ROOM_HEIGHT = 900;

    private Stage stage;
    private TextBody body;

    @Override
    public void start(final Stage primary) {
        stage = primary;
        body = new TextBody();
        final Scene scene = ThemeTestSupport.themedScene();
        scene.setRoot(new VBox(body));
        primary.setScene(scene);
        primary.setWidth(480);
        primary.setHeight(ROOM_HEIGHT);
        primary.show();
    }

    private boolean isScrolling() {
        return ThemeTestSupport.onFx(() -> {
            body.applyCss();
            body.layout();
            return body.lookupAll(".scroll-bar").stream()
                    .filter(ScrollBar.class::isInstance)
                    .map(ScrollBar.class::cast)
                    .anyMatch(bar -> bar.getOrientation() == Orientation.VERTICAL && isShown(bar));
        });
    }

    private static boolean isShown(final Node bar) {
        return bar.isVisible()
                && bar.getLayoutBounds().getWidth() > 0
                && bar.getLayoutBounds().getHeight() > 0;
    }

    // IF the measure were taken before the skin and the stylesheet gave the area its insets, THEN the last line would
    // be clipped and a scroll bar would appear for a text that fits under the cap.
    @ParameterizedTest
    @ValueSource(ints = {2, 6, 12})
    void prefHeight_textUnderTheCap_showsEveryLineWithoutScrolling(final int sentences) {
        ThemeTestSupport.onFx(() -> {
            body.setText(SENTENCE.repeat(sentences));
            return null;
        });
        settle();

        assertThat(isScrolling()).isFalse();
    }

    // IF the area kept the height it measured at its old width, THEN a narrower window would wrap the text onto more
    // lines than the area holds.
    @Test
    void prefHeight_windowNarrowed_remeasuresAtTheNewWidth() {
        ThemeTestSupport.onFx(() -> {
            body.setText(SENTENCE.repeat(6));
            return null;
        });
        settle();

        ThemeTestSupport.onFx(() -> {
            stage.setWidth(260);
            return null;
        });
        settle();

        assertThat(isScrolling()).isFalse();
    }

    // IF a longer text only grew the area up to its first measure, THEN the new lines would be clipped.
    @Test
    void prefHeight_textReplacedByALongerOne_growsTheArea() {
        ThemeTestSupport.onFx(() -> {
            body.setText(SENTENCE);
            return null;
        });
        settle();

        ThemeTestSupport.onFx(() -> {
            body.setText(SENTENCE.repeat(10));
            return null;
        });
        settle();

        assertThat(isScrolling()).isFalse();
    }

    private void settle() {
        interact(() -> {});
    }
}
