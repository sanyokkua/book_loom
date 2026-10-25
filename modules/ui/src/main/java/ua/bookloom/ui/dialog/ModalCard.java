package ua.bookloom.ui.dialog;

import java.util.Objects;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.notify.FooterFeedback;
import ua.bookloom.ui.notify.Severity;

/**
 * A {@link DialogPane} whose footer reads the same on every platform: the platform button order scatters tertiary
 * actions with gaps (macOS puts "other" buttons mid-bar), while the design wants one right-aligned group, cancel then
 * tertiary then the confirming button.
 *
 * <p>The card has one of three {@link Size}s, a fixed header and footer, and a body that scrolls on its own, so the
 * footer's buttons are where the hand expects them whatever the body holds. The host calls {@link #fit} with the
 * window's size when the card is shown and whenever the window changes.
 */
@Slf4j
public class ModalCard extends DialogPane implements FooterFeedback {

    /** The three widths a dialog may have. */
    public enum Size {
        /** A confirmation or short question. */
        DIALOG(480),
        /** A form or a result. */
        WIDE(720),
        /** A large dialog: wide as the window allows, a fixed tall. */
        XL(1180);

        private final double width;

        Size(final double width) {
            this.width = width;
        }

        /**
         * The width in pixels in a window large enough.
         *
         * @return the nominal width
         */
        public double width() {
            return width;
        }
    }

    /** The tallest an XL card is, in pixels. */
    static final double XL_HEIGHT = 820;

    private static final String FOOTER_ORDER = "L+HECUAO";
    private static final double WINDOW_WIDTH_SHARE = 0.92;
    private static final double WINDOW_HEIGHT_SHARE = 0.9;
    private static final double FEEDBACK_INSET = 18;
    private static final String FEEDBACK_ID = "modal-feedback";
    private static final String FEEDBACK_CLASS = "dialog-feedback";

    private final Size size;
    private final Label feedback = new Label();
    private @Nullable Runnable onEscape;
    private boolean enterNeedsShortcut;
    private boolean wrapping;

    /**
     * Creates a card of one size and fits it to a window large enough for it.
     *
     * @param size the width class of the card
     */
    public ModalCard(final Size size) {
        this.size = Objects.requireNonNull(size, "size");
        feedback.setId(FEEDBACK_ID);
        feedback.getStyleClass().add(FEEDBACK_CLASS);
        feedback.setManaged(false);
        // The line is drawn over the footer's free middle; it must never take a press meant for a button there.
        feedback.setMouseTransparent(true);
        getChildren().add(feedback);
        contentProperty().addListener((observable, old, now) -> wrapBody(now));
        fit(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /**
     * The card's width class.
     *
     * @return the size given at construction
     */
    public Size size() {
        return size;
    }

    /**
     * Sets what Escape does on this card, for a card whose Escape is not a plain close (a progress dialog hides its
     * work).
     *
     * @param action what Escape runs; {@code null} leaves the host's default, which closes a card that may be closed
     */
    public void setOnEscape(final @Nullable Runnable action) {
        this.onEscape = action;
    }

    /**
     * What Escape does on this card.
     *
     * @return the action set by {@link #setOnEscape}, or {@code null} when the host's default applies
     */
    public @Nullable Runnable onEscape() {
        return onEscape;
    }

    /**
     * Makes Enter run the default button only with Ctrl or Command, wherever the focus is, for a card whose main button
     * decides something a stray Enter on a focused row must not (the review desk).
     *
     * @param value {@code true} to need Ctrl or Command with Enter; plain Enter then does nothing on the card
     */
    public void setEnterNeedsShortcut(final boolean value) {
        this.enterNeedsShortcut = value;
    }

    /**
     * Whether Enter needs Ctrl or Command to run the default button on this card.
     *
     * @return {@code true} if it does, {@code false} for the usual Enter
     */
    public boolean isEnterNeedsShortcut() {
        return enterNeedsShortcut;
    }

    /**
     * Sizes the card for a window: the width class or 92 % of the window if that is smaller, and a height that never
     * exceeds 90 % of the window. An XL card has a fixed height, so the footer never moves.
     *
     * @param windowWidth the window's content width in pixels
     * @param windowHeight the window's content height in pixels
     */
    public final void fit(final double windowWidth, final double windowHeight) {
        final double width = Math.round(Math.min(size.width(), windowWidth * WINDOW_WIDTH_SHARE));
        final double ceiling = Math.round(windowHeight * WINDOW_HEIGHT_SHARE);
        setMinWidth(width);
        setPrefWidth(width);
        setMaxWidth(width);
        if (size == Size.XL) {
            final double height = Math.min(XL_HEIGHT, ceiling);
            setMinHeight(height);
            setPrefHeight(height);
            setMaxHeight(height);
        } else {
            setMinHeight(Region.USE_COMPUTED_SIZE);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            setMaxHeight(ceiling);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The line sits in the footer's free left side and is cut with an ellipsis rather than wrapped.
     */
    @Override
    public void feedback(final String text, final Severity severity) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(severity, "severity");
        log.debug("footer feedback of card {}: severity {}", getId(), severity);
        feedback.getStyleClass().removeIf(name -> name.startsWith(FEEDBACK_CLASS + "-"));
        feedback.getStyleClass().add(FEEDBACK_CLASS + "-" + severity.name().toLowerCase(java.util.Locale.ROOT));
        feedback.setText(text);
    }

    /** Empties the footer's feedback line. */
    public void clearFeedback() {
        feedback.setText("");
        feedback.getStyleClass().removeIf(name -> name.startsWith(FEEDBACK_CLASS + "-"));
    }

    // A card has the one width fit() gave it, but its host asks for its height with no width, and text that wraps is
    // then measured on one line: the card came out too short and its body scrolled with room left in the window
    // (About's buttons half under the scroll edge). The height is measured at the width the content really gets.
    @Override
    protected double computePrefHeight(final double width) {
        final double outer = width > 0 ? width : getPrefWidth();
        return super.computePrefHeight(outer > 0 ? outer - snappedLeftInset() - snappedRightInset() : width);
    }

    // The body is the one part that scrolls; DialogPane would otherwise squeeze a content taller than the card.
    private void wrapBody(final @Nullable Node now) {
        if (wrapping || now == null || now instanceof Body) {
            return;
        }
        wrapping = true;
        try {
            setContent(new Body(now));
        } finally {
            wrapping = false;
        }
    }

    // The feedback line fills the free gap between the footer's left-hand group (Undo all, Continue in background
    // and the note beside it) and its right-hand buttons, so it never lies over a button.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!(lookup(".button-bar") instanceof ButtonBar bar)) {
            return;
        }
        final double used = bar.prefWidth(-1);
        final double room = Math.max(0, bar.getLayoutBounds().getWidth() - used);
        final double inset = bar.getLayoutBounds().getWidth() > 0 ? FEEDBACK_INSET : 0;
        final double width = Math.max(0, room - 2 * inset);
        feedback.resizeRelocate(
                leftGroupEnd(bar) + inset,
                bar.getLayoutY(),
                width,
                bar.getLayoutBounds().getHeight());
    }

    private double leftGroupEnd(final ButtonBar bar) {
        double end = bar.getLayoutX();
        for (final Node node : bar.getButtons()) {
            if (ButtonBar.getButtonData(node) == ButtonBar.ButtonData.LEFT && node.isVisible() && node.isManaged()) {
                end = Math.max(
                        end,
                        sceneToLocal(node.localToScene(node.getLayoutBounds())).getMaxX());
            }
        }
        return end;
    }

    // ButtonBar sizes every button to the widest by default, and a card of fixed width then squeezes them all below
    // their words ("Not stated — s…"); each button keeps its own width instead.
    @Override
    protected Node createButton(final ButtonType buttonType) {
        final Node button = super.createButton(buttonType);
        ButtonBar.setButtonUniformSize(button, false);
        return button;
    }

    @Override
    protected Node createButtonBar() {
        final ButtonBar bar = (ButtonBar) super.createButtonBar();
        bar.setButtonOrder(FOOTER_ORDER);
        // The bar's skin overwrites every button's minimum width with this value when it places the buttons, so a
        // minimum set on the button itself is lost; USE_PREF_SIZE here makes each one never narrower than its words,
        // whatever the font (a wider fallback font made a four-button footer outgrow the card and cut the longest
        // label).
        bar.setButtonMinWidth(Region.USE_PREF_SIZE);
        return bar;
    }

    private static final class Body extends ScrollPane {
        Body(final Node content) {
            super(content);
            getStyleClass().add("dialog-body");
            setFitToWidth(true);
            setHbarPolicy(ScrollBarPolicy.NEVER);
        }

        // The skin measures the content at the pane's own width; the content is narrower by the pane's padding, so
        // its wrapped text would need more lines than measured.
        @Override
        protected double computePrefHeight(final double width) {
            final Node content = getContent();
            if (width <= 0 || content == null) {
                return super.computePrefHeight(width);
            }
            final Insets insets = getInsets();
            final double inner = width - insets.getLeft() - insets.getRight();
            return snapSizeY(content.prefHeight(inner) + insets.getTop() + insets.getBottom());
        }
    }
}
