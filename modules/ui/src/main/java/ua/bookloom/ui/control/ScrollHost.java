package ua.bookloom.ui.control;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/**
 * The one scroll pane of the interface: every screen, Settings tab, dialog body and detail pane that can hold more
 * than its viewport scrolls through it, in code ({@code new ScrollHost(content)}) and in FXML ({@code <ScrollHost>}).
 *
 * <p>It scrolls down, never across: its content is fitted to its width, so wrapped text wraps instead of running out
 * of sight, and the vertical bar shows whenever the content is taller than the viewport. A place that must fill the
 * height it is given (a form with a pinned footer, a split) also fits to the height; it still scrolls as soon as its
 * content's minimum is taller. The wheel and the trackpad move it through {@link SmoothScroll}, installed once on the
 * shell.
 *
 * <p>A pane that is not one of these — a tab's content laid out straight into the tab — is clipped by the tab and
 * cannot be scrolled at all, which is why a Settings tab holds its content in one, and the source guard checks that no
 * other class builds a scroll pane of its own.
 */
public class ScrollHost extends ScrollPane {

    /** An empty host, for FXML, which sets the content afterwards. */
    public ScrollHost() {
        configure();
    }

    /**
     * A host for {@code content}.
     *
     * @param content what scrolls; its width follows the host's
     */
    public ScrollHost(final Node content) {
        super(content);
        configure();
    }

    private void configure() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
    }
}
