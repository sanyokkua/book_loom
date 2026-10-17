package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;

/**
 * The model calls of the work the window waits for, in a window of their own: with the prompts open, the calls do not
 * fit on a card, so they get a resizable, non-modal window owned by the main window. It opens at {@value #SHARE_PERCENT}
 * percent of the owner's size, centred on it, and keeps following the owner's size until the person resizes it
 * themselves; after that the person's size stays. The calls scroll inside it. FX thread only.
 */
@Slf4j
public final class ModelCallsWindow {

    /** The id of the window's root node, by which the window is found. */
    public static final String ROOT_ID = "model-calls-window";

    /** How much of the owner's width and height the window opens with, in percent. */
    static final int SHARE_PERCENT = 80;

    private static final double SHARE = SHARE_PERCENT / 100.0;
    private static final double MIN_SIZE = 360;
    // A window manager may round a requested size; a change within this many pixels of the one asked for is not the
    // person's.
    private static final double ROUNDING = 2;

    private final Messages messages;
    private final Consumer<Scene> theming;
    private final LiveCallView current;
    private final LiveCallView previous;
    private @Nullable Stage stage;
    private @Nullable Window owner;
    private boolean sizedByPerson;
    private int ownerListeners;
    private double askedWidth;
    private double askedHeight;
    private final ChangeListener<Number> onOwnerSize = (observed, was, now) -> {
        if (isOpen()) {
            fit();
        }
    };
    private final ChangeListener<Number> onOwnWidth = (observed, was, now) -> noticeWidth(now.doubleValue());
    private final ChangeListener<Number> onOwnHeight = (observed, was, now) -> noticeHeight(now.doubleValue());

    /**
     * Builds the window's content; nothing is shown until {@link #open(Window)}.
     *
     * @param messages the catalogue the window is worded from
     * @param theming applies the theme to the window's scene, so it follows the light and dark block
     */
    public ModelCallsWindow(final Messages messages, final Consumer<Scene> theming) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.theming = Objects.requireNonNull(theming, "theming");
        current = CallsSection.callView("model-calls-current", MessageKey.LIVE_CALL_CURRENT, messages);
        previous = CallsSection.callView("model-calls-previous", MessageKey.LIVE_CALL_PREVIOUS, messages);
    }

    /**
     * Opens the window over {@code over}, or brings it forward when it is already open.
     *
     * @param over the main window that owns it; the window closes with it
     */
    public void open(final Window over) {
        Objects.requireNonNull(over, "over");
        final Stage open = stage;
        if (open != null && open.isShowing()) {
            log.debug("the model calls window is already open: brought forward");
            open.toFront();
            return;
        }
        log.debug("opening the model calls window over a {}x{} owner", over.getWidth(), over.getHeight());
        final Stage built = build(over);
        stage = built;
        owner = over;
        sizedByPerson = false;
        startFollowing(over);
        fit();
        built.show();
    }

    /** Closes the window when it is open; the next {@link #open(Window)} fits it to the owner again. */
    public void close() {
        final Stage open = stage;
        if (open != null && open.isShowing()) {
            log.debug("closing the model calls window");
            open.close();
        }
    }

    /**
     * Whether the window is on screen.
     *
     * @return {@code true} while the window is showing, {@code false} otherwise
     */
    public boolean isOpen() {
        final Stage open = stage;
        return open != null && open.isShowing();
    }

    /**
     * Shows the calls in the window.
     *
     * @param live the current and previous call
     */
    public void show(final LiveCalls live) {
        Objects.requireNonNull(live, "live");
        current.show(live.current(), live);
        previous.show(live.previous(), live);
    }

    /**
     * Redraws only the current call, for a waiting call's moving clock.
     *
     * @param now the call in flight
     * @param live the calls with the moment its clock reads
     */
    public void showCurrent(final CallSnapshot now, final LiveCalls live) {
        current.show(now, live);
    }

    private Stage build(final Window over) {
        final ScrollPane scroll = new ScrollPane(new VBox(current, previous));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        final VBox root = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setId(ROOT_ID);
        root.getStyleClass().add("model-calls-window");
        final Scene scene = new Scene(root);
        theming.accept(scene);
        final Stage built = new Stage();
        built.initOwner(over);
        built.initModality(Modality.NONE);
        built.setTitle(messages.get(MessageKey.BUSY_CALLS));
        built.setMinWidth(MIN_SIZE);
        built.setMinHeight(MIN_SIZE);
        built.setScene(scene);
        built.widthProperty().addListener(onOwnWidth);
        built.heightProperty().addListener(onOwnHeight);
        built.setOnHidden(event -> stopFollowing());
        return built;
    }

    // One pair of owner listeners per open window: added when it opens, removed when it hides.
    private void startFollowing(final Window over) {
        over.widthProperty().addListener(onOwnerSize);
        over.heightProperty().addListener(onOwnerSize);
        ownerListeners += 2;
    }

    private void stopFollowing() {
        final Window over = owner;
        if (over != null) {
            over.widthProperty().removeListener(onOwnerSize);
            over.heightProperty().removeListener(onOwnerSize);
            ownerListeners = 0;
            owner = null;
        }
    }

    /** The number of listeners the window holds on its owner's size; for the test that proves they do not pile up. */
    int ownerListenerCount() {
        return ownerListeners;
    }

    // Sized and centred on the owner, as long as the person has not set a size of their own.
    private void fit() {
        final Stage open = stage;
        final Window over = owner;
        if (open == null || over == null || sizedByPerson) {
            return;
        }
        askedWidth = Math.max(MIN_SIZE, over.getWidth() * SHARE);
        askedHeight = Math.max(MIN_SIZE, over.getHeight() * SHARE);
        open.setWidth(askedWidth);
        open.setHeight(askedHeight);
        open.setX(over.getX() + (over.getWidth() - askedWidth) / 2);
        open.setY(over.getY() + (over.getHeight() - askedHeight) / 2);
    }

    private void noticeWidth(final double now) {
        if (isOpen() && !sizedByPerson && Math.abs(now - askedWidth) > ROUNDING) {
            log.debug("the model calls window was resized by the person: it stops following its owner");
            sizedByPerson = true;
        }
    }

    private void noticeHeight(final double now) {
        if (isOpen() && !sizedByPerson && Math.abs(now - askedHeight) > ROUNDING) {
            log.debug("the model calls window was resized by the person: it stops following its owner");
            sizedByPerson = true;
        }
    }
}
