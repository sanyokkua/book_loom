package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicLong;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** The anchor puts the view back after the content grows, except while the person is scrolling it. */
@SuppressWarnings("NullAway.Init")
class ScrollAnchorTest extends FxTestBase {

    private static final double VIEW_HEIGHT = 200;
    private static final double CONTENT_HEIGHT = 1000;
    private static final double GROWN_HEIGHT = 2000;
    private static final double KEPT_OFFSET_VALUE = 0.25;
    private static final double MOVED_VALUE = 0.3;
    private static final double EXACT = 1e-6;
    private static final long SECOND = 1_000_000_000L;

    private ScrollPane pane;
    private Region content;

    @Override
    public void start(final Stage stage) {
        content = new Region();
        content.setMinHeight(CONTENT_HEIGHT);
        pane = new ScrollPane(content);
        pane.setPadding(Insets.EMPTY);
        pane.setFitToWidth(true);
        pane.setPrefSize(150, VIEW_HEIGHT);
        stage.setScene(new Scene(pane, 150, VIEW_HEIGHT));
        stage.show();
    }

    private static ScrollEvent scrollEvent() {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                10,
                10,
                10,
                10,
                false,
                false,
                false,
                false,
                false,
                false,
                0,
                -3,
                0,
                -3,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                0,
                null);
    }

    // The person's own move lands in the same layout pass as the growth, before the anchor has heard of the new
    // height; the listener below plays it. Returns the pane's value after the growth, with the last scroll event
    // `sinceScrollNanos` before it.
    private double growAfterScrollEvent(final long sinceScrollNanos) {
        return ThemeTestSupport.onFx(() -> {
            content.layoutBoundsProperty().addListener((observed, was, now) -> pane.setVvalue(MOVED_VALUE));
            final AtomicLong clock = new AtomicLong(SECOND);
            ScrollAnchor.install(pane, clock::get);
            pane.setVvalue(KEPT_OFFSET_VALUE);
            Event.fireEvent(content, scrollEvent());
            clock.addAndGet(sinceScrollNanos);
            content.setMinHeight(GROWN_HEIGHT);
            pane.applyCss();
            pane.layout();
            return pane.getVvalue();
        });
    }

    // IF the anchor restored while the person is scrolling, THEN the position they had a layout pass ago would
    // overwrite where they are now and the view would snap back during a run.
    @Test
    void contentGrows_duringAScrollGesture_keepsWhereThePersonIs() {
        final double value = growAfterScrollEvent(ScrollAnchor.GESTURE_QUIET_NANOS / 5);

        assertThat(value).isCloseTo(MOVED_VALUE, within(EXACT));
    }

    // IF the anchor never restored, THEN a screen that briefly shrinks and grows would leave the view somewhere else;
    // with no scrolling for a second the position the anchor kept is put back over whatever the layout pass left.
    @Test
    void contentGrows_whenNoScrollingForASecond_putsTheKeptPositionBack() {
        final double after = growAfterScrollEvent(SECOND);

        assertThat(after).isLessThan(MOVED_VALUE - EXACT);
    }

    // IF a focus handed back to a replaced control scrolled the pane to it, THEN a retry would throw the person to
    // another part of the page; inside holdWhile the view returns to where it was.
    @Test
    void holdWhile_changeMovesTheView_putsTheViewBack() {
        final double after = ThemeTestSupport.onFx(() -> {
            ScrollAnchor.install(pane);
            pane.setVvalue(KEPT_OFFSET_VALUE);
            ScrollAnchor.holdWhile(content, () -> pane.setVvalue(MOVED_VALUE));
            return pane.getVvalue();
        });

        assertThat(after).isCloseTo(KEPT_OFFSET_VALUE, within(EXACT));
    }
}
