package ua.bookloom.ui;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * The application window's root node: deliberately empty.
 *
 * <p>There is no navigation, no {@code ViewNames} enum and no FXML here, and their absence is scoped rather than
 * accidental — the component library and the screens are later stages. What this node exists to prove is that the
 * token stylesheet cascades from {@code .root} to a real styled child, which is the mechanism every later screen
 * depends on.
 *
 * <p>Styling is by CSS class only. No {@code setStyle(...)} call appears anywhere in this module, and none may: an
 * inline style is invisible to the theme swap and cannot be overridden by a token.
 */
public final class AppShellView {

    /** Matches the {@code .app-shell} selector in {@code theme.css}. */
    public static final String SHELL_STYLE_CLASS = "app-shell";

    /** Matches the {@code .placeholder-label} selector, and gives the widget test a node to resolve a token on. */
    public static final String PLACEHOLDER_STYLE_CLASS = "placeholder-label";

    /** Stable id so a test can find the label without depending on layout structure. */
    public static final String PLACEHOLDER_ID = "app-shell-placeholder";

    private AppShellView() {
        // Static factory only.
    }

    /**
     * Builds the empty themed root.
     *
     * @return the root node, ready to be placed in a {@code Scene}
     */
    public static Parent create() {
        final Label placeholder = new Label("BookLoom");
        placeholder.setId(PLACEHOLDER_ID);
        placeholder.getStyleClass().add(PLACEHOLDER_STYLE_CLASS);

        final StackPane shell = new StackPane(placeholder);
        shell.getStyleClass().add(SHELL_STYLE_CLASS);
        return shell;
    }
}
