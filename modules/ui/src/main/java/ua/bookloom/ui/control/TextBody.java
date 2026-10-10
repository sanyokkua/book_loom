package ua.bookloom.ui.control;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import org.jspecify.annotations.Nullable;

/**
 * The read-only body of a prompt or a reply: one text area as tall as its wrapped text at the width it is given, up to
 * {@value CopyablePane#BODY_MAX} pixels, and scrolling inside beyond that. One text node holds the whole text, where a
 * label per part made every update build and measure a column of nodes.
 *
 * <p>A text area asks for a fixed number of rows whatever it holds, so this one reports a horizontal content bias and
 * measures its text wrapped at the width it is laid out at, inside its frame, its scroll pane and its content padding.
 * The measure is kept until the text, the width, the font or the frame changes. It takes no focus from Tab, as the labels it
 * replaces did not; a click still selects text in it.
 */
final class TextBody extends TextArea {

    private final Text probe = new Text();
    private @Nullable String measuredText;
    private double measuredWidth = Double.NaN;
    private @Nullable Font measuredFont;
    private @Nullable Insets measuredChrome;
    private double measuredHeight;

    TextBody() {
        // The bounds the area's own text node uses; a plain text node measures each line a little shorter.
        probe.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
        setEditable(false);
        setWrapText(true);
        setFocusTraversable(false);
        setMinHeight(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefHeight(final double width) {
        final String text = getText();
        final Font font = getFont();
        final Insets chrome = chrome();
        if (!text.equals(measuredText)
                || width != measuredWidth
                || !font.equals(measuredFont)
                || !chrome.equals(measuredChrome)) {
            measuredText = text;
            measuredWidth = width;
            measuredFont = font;
            measuredChrome = chrome;
            measuredHeight = measure(text, font, width, chrome);
        }
        return measuredHeight;
    }

    private double measure(final String text, final Font font, final double width, final Insets chrome) {
        probe.setFont(font);
        probe.setText(text);
        probe.setWrappingWidth(width < 0 ? 0 : Math.max(1, width - chrome.getLeft() - chrome.getRight()));
        final double height = probe.getLayoutBounds().getHeight() + chrome.getTop() + chrome.getBottom();
        return Math.min(Math.ceil(height), CopyablePane.BODY_MAX);
    }

    // The area's own insets, its scroll pane's and its content's: what lies between the area's edge and its text.
    private Insets chrome() {
        double top = snappedTopInset();
        double right = snappedRightInset();
        double bottom = snappedBottomInset();
        double left = snappedLeftInset();
        for (final String part : new String[] {".scroll-pane", ".content"}) {
            final Node node = lookup(part);
            if (node instanceof Region region && !region.equals(this)) {
                final Insets insets = region.getInsets();
                top += insets.getTop();
                right += insets.getRight();
                bottom += insets.getBottom();
                left += insets.getLeft();
            }
        }
        return new Insets(top, right, bottom, left);
    }
}
