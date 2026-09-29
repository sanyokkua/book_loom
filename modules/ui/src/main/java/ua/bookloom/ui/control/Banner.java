package ua.bookloom.ui.control;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A message strip: a glyph, a title and a text, in one of four roles, with room for extra detail and one optional
 * action.
 *
 * <p>The role is never the only carrier of meaning, because every banner also has its glyph and its words. The
 * setters replace the words and the role in place, so a live banner changes without moving anything on screen. The
 * title and text nodes are addressable as {@code <id>-title} and {@code <id>-text}.
 */
public final class Banner extends HBox {

    private static final double TEXT_SPACING = 3;

    /** The look of a banner: the style class that paints it. */
    public enum Role {
        /** Neutral information. */
        INFO("banner-info"),
        /** Success. */
        OK("banner-ok"),
        /** Something needs attention. */
        WARN("banner-warn"),
        /** A failure. */
        ERR("banner-err");

        private final String styleClass;

        Role(final String styleClass) {
            this.styleClass = styleClass;
        }

        String styleClass() {
            return styleClass;
        }
    }

    private final Label icon = new Label();
    private final Label title = wrapped("banner-title");
    private final Label text = wrapped("banner-text");
    private final VBox column = new VBox(TEXT_SPACING, title, text);
    private Role role;

    /**
     * Builds the banner.
     *
     * @param id the node id
     * @param role the look
     * @param glyph the mark drawn before the words
     * @param title the bold first line
     * @param text the second line
     */
    public Banner(final String id, final Role role, final String glyph, final String title, final String text) {
        Objects.requireNonNull(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        icon.getStyleClass().add("banner-icon");
        setId(id);
        this.title.setId(id + "-title");
        this.text.setId(id + "-text");
        setAlignment(Pos.TOP_LEFT);
        getStyleClass().addAll("banner", role.styleClass());
        getChildren().addAll(icon, column);
        setGlyph(glyph);
        setTitle(title);
        setText(text);
    }

    /**
     * Replaces the look.
     *
     * @param newRole the role to paint
     */
    public void setRole(final Role newRole) {
        Objects.requireNonNull(newRole, "newRole");
        getStyleClass().remove(role.styleClass());
        getStyleClass().add(newRole.styleClass());
        role = newRole;
    }

    /**
     * Replaces the mark.
     *
     * @param glyph the new mark
     */
    public void setGlyph(final String glyph) {
        icon.setText(Objects.requireNonNull(glyph, "glyph"));
    }

    /**
     * Replaces the first line.
     *
     * @param value the new title
     */
    public void setTitle(final String value) {
        title.setText(Objects.requireNonNull(value, "value"));
        title.setVisible(!value.isBlank());
        title.setManaged(!value.isBlank());
    }

    /**
     * Replaces the second line.
     *
     * @param value the new text
     */
    public void setText(final String value) {
        text.setText(Objects.requireNonNull(value, "value"));
    }

    /**
     * Adds a further node under the text, such as a code chip.
     *
     * @param detail the node to add
     */
    public void addDetail(final Node detail) {
        column.getChildren().add(Objects.requireNonNull(detail, "detail"));
    }

    /**
     * Adds an action button under the text.
     *
     * @param id the button's node id
     * @param label the visible, already translated text
     * @param action what pressing it does
     * @return the button, shown; the caller may hide it with {@link #setActionShown(Button, boolean)}
     */
    public Button addAction(final String id, final String label, final Runnable action) {
        Objects.requireNonNull(action, "action");
        final Button button = new Button(Objects.requireNonNull(label, "label"));
        button.setId(Objects.requireNonNull(id, "id"));
        button.getStyleClass().add("btn-secondary");
        button.setOnAction(event -> action.run());
        column.getChildren().add(button);
        return button;
    }

    /**
     * Shows or hides an action button so that a hidden one takes no room.
     *
     * @param button a button returned by {@link #addAction}
     * @param shown whether it is visible and takes space
     */
    public static void setActionShown(final Button button, final boolean shown) {
        button.setVisible(shown);
        button.setManaged(shown);
    }

    private static Label wrapped(final String styleClass) {
        final Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }
}
